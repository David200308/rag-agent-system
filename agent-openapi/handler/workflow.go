package handler

import "net/http"

// WorkflowList proxies GET /v1/workflow → GET /api/v1/workflow
func (h *Handler) WorkflowList(w http.ResponseWriter, r *http.Request) {
	h.proxy(w, r, "/api/v1/workflow")
}

// WorkflowGet proxies GET /v1/workflow/{id} → /api/v1/workflow/{id}
func (h *Handler) WorkflowGet(w http.ResponseWriter, r *http.Request) {
	h.proxy(w, r, "/api/v1/workflow/"+r.PathValue("id"))
}

// WorkflowAgents proxies GET /v1/workflow/{id}/agents → /api/v1/workflow/{id}/agents
func (h *Handler) WorkflowAgents(w http.ResponseWriter, r *http.Request) {
	h.proxy(w, r, "/api/v1/workflow/"+r.PathValue("id")+"/agents")
}

// WorkflowRunTrigger proxies POST /v1/workflow/{id}/runs → /api/v1/workflow/{id}/runs
func (h *Handler) WorkflowRunTrigger(w http.ResponseWriter, r *http.Request) {
	h.proxy(w, r, "/api/v1/workflow/"+r.PathValue("id")+"/runs")
}

// WorkflowRuns proxies GET /v1/workflow/{id}/runs → /api/v1/workflow/{id}/runs
func (h *Handler) WorkflowRuns(w http.ResponseWriter, r *http.Request) {
	h.proxy(w, r, "/api/v1/workflow/"+r.PathValue("id")+"/runs")
}

// WorkflowRunLogs proxies GET /v1/workflow/runs/{id}/logs → /api/v1/workflow/runs/{id}/logs
func (h *Handler) WorkflowRunLogs(w http.ResponseWriter, r *http.Request) {
	h.proxy(w, r, "/api/v1/workflow/runs/"+r.PathValue("id")+"/logs")
}
