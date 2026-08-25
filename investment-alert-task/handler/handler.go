package handler

import (
	"crypto/rand"
	"database/sql"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"

	"investment-alert-task/internal/config"
	"investment-alert-task/internal/core"
	"investment-alert-task/store"
)

type Handler struct {
	cfg *config.Config
	st  *store.Store
}

func New(cfg *config.Config, st *store.Store) *Handler {
	return &Handler{cfg: cfg, st: st}
}

// ── Auth ──────────────────────────────────────────────────────────────────────
// All routes here are internal, called only by agent-system-rest (Java) on
// behalf of an already-authenticated user — no JWT validation happens in this
// service, only a shared service key.

func (h *Handler) withServiceAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-Alert-Key") != h.cfg.ServiceKey {
			writeError(w, http.StatusUnauthorized, "invalid service key")
			return
		}
		next(w, r)
	}
}

// ── Health ────────────────────────────────────────────────────────────────────

func (h *Handler) Health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

// ── Create ────────────────────────────────────────────────────────────────────

// POST /internal/alerts/price
func (h *Handler) CreatePrice(w http.ResponseWriter, r *http.Request) {
	h.withServiceAuth(func(w http.ResponseWriter, r *http.Request) {
		var rc config.AlertRuleConfig
		if !decodeBody(w, r, &rc) {
			return
		}
		rc.ID = newID()
		rc.Enabled = true
		rule, err := config.ParsePriceRule(rc)
		if err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		if err := h.st.CreatePriceRule(rule); err != nil {
			log.Printf("[handler] create price rule error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to create alert rule")
			return
		}
		writeJSON(w, http.StatusCreated, rule)
	})(w, r)
}

// POST /internal/alerts/defi
func (h *Handler) CreateDeFi(w http.ResponseWriter, r *http.Request) {
	h.withServiceAuth(func(w http.ResponseWriter, r *http.Request) {
		var rc config.DeFiAlertRuleConfig
		if !decodeBody(w, r, &rc) {
			return
		}
		rc.ID = newID()
		rc.Enabled = true
		rule, err := config.ParseDeFiRule(rc)
		if err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		if err := h.st.CreateDeFiRule(rule); err != nil {
			log.Printf("[handler] create defi rule error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to create alert rule")
			return
		}
		writeJSON(w, http.StatusCreated, rule)
	})(w, r)
}

// POST /internal/alerts/predict-market
func (h *Handler) CreatePredictMarket(w http.ResponseWriter, r *http.Request) {
	h.withServiceAuth(func(w http.ResponseWriter, r *http.Request) {
		var rc config.PredictMarketAlertRuleConfig
		if !decodeBody(w, r, &rc) {
			return
		}
		rc.ID = newID()
		rc.Enabled = true
		rule, err := config.ParsePredictMarketRule(rc)
		if err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		if err := h.st.CreatePredictMarketRule(rule); err != nil {
			log.Printf("[handler] create predict-market rule error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to create alert rule")
			return
		}
		writeJSON(w, http.StatusCreated, rule)
	})(w, r)
}

// ── List ──────────────────────────────────────────────────────────────────────

// alertsResponse is the combined shape returned by GET /internal/alerts.
type alertsResponse struct {
	Price         []*core.AlertRule              `json:"price"`
	DeFi          []*core.DeFiAlertRule          `json:"defi"`
	PredictMarket []*core.PredictMarketAlertRule `json:"predictMarket"`
}

// GET /internal/alerts?ownerUuid=
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	h.withServiceAuth(func(w http.ResponseWriter, r *http.Request) {
		ownerUuid := r.URL.Query().Get("ownerUuid")
		if ownerUuid == "" {
			writeError(w, http.StatusBadRequest, "ownerUuid is required")
			return
		}

		price, err := h.st.ListPriceRulesByOwner(ownerUuid)
		if err != nil {
			log.Printf("[handler] list price rules error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to list alert rules")
			return
		}
		defi, err := h.st.ListDeFiRulesByOwner(ownerUuid)
		if err != nil {
			log.Printf("[handler] list defi rules error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to list alert rules")
			return
		}
		predict, err := h.st.ListPredictMarketRulesByOwner(ownerUuid)
		if err != nil {
			log.Printf("[handler] list predict-market rules error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to list alert rules")
			return
		}

		writeJSON(w, http.StatusOK, alertsResponse{Price: price, DeFi: defi, PredictMarket: predict})
	})(w, r)
}

// ── Update ────────────────────────────────────────────────────────────────────

type updateRequest struct {
	Threshold *float64                `json:"threshold"`
	Direction *string                 `json:"direction"`
	Enabled   *bool                   `json:"enabled"`
	Frequency *config.FrequencyConfig `json:"frequency"`
	OwnerUuid string                  `json:"ownerUuid"`
}

// PATCH /internal/alerts/{type}/{id}
func (h *Handler) Update(w http.ResponseWriter, r *http.Request) {
	h.withServiceAuth(func(w http.ResponseWriter, r *http.Request) {
		ruleType := r.PathValue("type")
		id := r.PathValue("id")

		var req updateRequest
		if !decodeBody(w, r, &req) {
			return
		}

		switch ruleType {
		case "price":
			rule, err := h.st.GetPriceRuleByID(id)
			if !checkOwned(w, rule, err, req.OwnerUuid, func() string { return rule.OwnerUuid }) {
				return
			}
			applyCommonUpdates(&rule.Threshold, &rule.Direction, &rule.Enabled, &rule.Frequency, req)
			if err := h.st.UpdatePriceRule(rule); err != nil {
				log.Printf("[handler] update price rule error id=%s: %v", id, err)
				writeError(w, http.StatusInternalServerError, "failed to update alert rule")
				return
			}
			writeJSON(w, http.StatusOK, rule)

		case "defi":
			rule, err := h.st.GetDeFiRuleByID(id)
			if !checkOwned(w, rule, err, req.OwnerUuid, func() string { return rule.OwnerUuid }) {
				return
			}
			applyCommonUpdates(&rule.Threshold, &rule.Direction, &rule.Enabled, &rule.Frequency, req)
			if err := h.st.UpdateDeFiRule(rule); err != nil {
				log.Printf("[handler] update defi rule error id=%s: %v", id, err)
				writeError(w, http.StatusInternalServerError, "failed to update alert rule")
				return
			}
			writeJSON(w, http.StatusOK, rule)

		case "predict-market":
			rule, err := h.st.GetPredictMarketRuleByID(id)
			if !checkOwned(w, rule, err, req.OwnerUuid, func() string { return rule.OwnerUuid }) {
				return
			}
			applyCommonUpdates(&rule.Threshold, &rule.Direction, &rule.Enabled, &rule.Frequency, req)
			if err := h.st.UpdatePredictMarketRule(rule); err != nil {
				log.Printf("[handler] update predict-market rule error id=%s: %v", id, err)
				writeError(w, http.StatusInternalServerError, "failed to update alert rule")
				return
			}
			writeJSON(w, http.StatusOK, rule)

		default:
			writeError(w, http.StatusBadRequest, "type must be one of: price, defi, predict-market")
		}
	})(w, r)
}

func applyCommonUpdates(threshold *float64, direction *core.Direction, enabled *bool, frequency **core.Frequency, req updateRequest) {
	if req.Threshold != nil {
		*threshold = *req.Threshold
	}
	if req.Direction != nil {
		*direction = core.Direction(*req.Direction)
	}
	if req.Enabled != nil {
		*enabled = *req.Enabled
	}
	if req.Frequency != nil {
		if req.Frequency.Number != nil || req.Frequency.Unit != "" {
			num := 0
			if req.Frequency.Number != nil {
				num = *req.Frequency.Number
			}
			*frequency = &core.Frequency{Number: num, Unit: req.Frequency.Unit}
		}
	}
}

// checkOwned handles the "not found" / "db error" / "not the owner" branches shared by all
// three Update/Delete paths, writing the appropriate HTTP response and returning false if the
// caller should stop processing.
func checkOwned[T any](w http.ResponseWriter, rule *T, err error, ownerUuid string, getOwner func() string) bool {
	if err == sql.ErrNoRows {
		writeError(w, http.StatusNotFound, "alert rule not found")
		return false
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to fetch alert rule")
		return false
	}
	if ownerUuid != "" && getOwner() != ownerUuid {
		writeError(w, http.StatusForbidden, "not the owner")
		return false
	}
	return true
}

// ── Delete ────────────────────────────────────────────────────────────────────

// DELETE /internal/alerts/{type}/{id}?ownerUuid=
func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	h.withServiceAuth(func(w http.ResponseWriter, r *http.Request) {
		ruleType := r.PathValue("type")
		id := r.PathValue("id")
		ownerUuid := r.URL.Query().Get("ownerUuid")
		if ownerUuid == "" {
			writeError(w, http.StatusBadRequest, "ownerUuid is required")
			return
		}

		var deleteErr error
		switch ruleType {
		case "price":
			rule, err := h.st.GetPriceRuleByID(id)
			if !checkOwned(w, rule, err, ownerUuid, func() string { return rule.OwnerUuid }) {
				return
			}
			deleteErr = h.st.DeletePriceRule(id, ownerUuid)
		case "defi":
			rule, err := h.st.GetDeFiRuleByID(id)
			if !checkOwned(w, rule, err, ownerUuid, func() string { return rule.OwnerUuid }) {
				return
			}
			deleteErr = h.st.DeleteDeFiRule(id, ownerUuid)
		case "predict-market":
			rule, err := h.st.GetPredictMarketRuleByID(id)
			if !checkOwned(w, rule, err, ownerUuid, func() string { return rule.OwnerUuid }) {
				return
			}
			deleteErr = h.st.DeletePredictMarketRule(id, ownerUuid)
		default:
			writeError(w, http.StatusBadRequest, "type must be one of: price, defi, predict-market")
			return
		}

		if deleteErr != nil {
			log.Printf("[handler] delete rule error type=%s id=%s: %v", ruleType, id, deleteErr)
			writeError(w, http.StatusInternalServerError, "failed to delete alert rule")
			return
		}
		w.WriteHeader(http.StatusNoContent)
	})(w, r)
}

// ── Helpers ───────────────────────────────────────────────────────────────────

func decodeBody(w http.ResponseWriter, r *http.Request, v any) bool {
	body, err := io.ReadAll(io.LimitReader(r.Body, 32*1024))
	if err != nil {
		writeError(w, http.StatusBadRequest, "cannot read body")
		return false
	}
	if err := json.Unmarshal(body, v); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON")
		return false
	}
	return true
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

// newID generates a random UUID v4 string.
func newID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}
