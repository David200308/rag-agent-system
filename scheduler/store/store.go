package store

import (
	"database/sql"
	"fmt"
	"time"

	_ "github.com/go-sql-driver/mysql"
	"scheduler/model"
)

type Store struct {
	db *sql.DB
}

func New(dsn string) (*Store, error) {
	db, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil, fmt.Errorf("open db: %w", err)
	}
	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("ping db: %w", err)
	}
	return &Store{db: db}, nil
}

func nullStr(s string) sql.NullString {
	if s == "" {
		return sql.NullString{}
	}
	return sql.NullString{String: s, Valid: true}
}

func (s *Store) Create(sc *model.Schedule) error {
	_, err := s.db.Exec(`
		INSERT INTO scheduled_messages
			(id, conversation_id, workflow_id, workflow_input, owner_email, message, cron_expr, timezone,
			 top_k, use_knowledge_base, use_web_fetch, enabled, created_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		sc.ID, nullStr(sc.ConversationID), nullStr(sc.WorkflowID), nullStr(sc.WorkflowInput),
		sc.OwnerEmail, sc.Message, sc.CronExpr, sc.Timezone,
		sc.TopK, sc.UseKnowledgeBase, sc.UseWebFetch, sc.Enabled, sc.CreatedAt)
	return err
}

func (s *Store) GetByID(id string) (*model.Schedule, error) {
	row := s.db.QueryRow(`
		SELECT id, conversation_id, workflow_id, workflow_input, owner_email, message, cron_expr, timezone,
		       top_k, use_knowledge_base, use_web_fetch, enabled, created_at
		FROM scheduled_messages WHERE id = ?`, id)
	return scanRow(row)
}

func (s *Store) ListByConversation(ownerEmail, convID string) ([]*model.Schedule, error) {
	rows, err := s.db.Query(`
		SELECT id, conversation_id, workflow_id, workflow_input, owner_email, message, cron_expr, timezone,
		       top_k, use_knowledge_base, use_web_fetch, enabled, created_at
		FROM scheduled_messages
		WHERE conversation_id = ? AND owner_email = ?
		ORDER BY created_at DESC`, convID, ownerEmail)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanRows(rows)
}

// ListByWorkflow returns schedules for a workflow, filtered by owner.
func (s *Store) ListByWorkflow(ownerEmail, workflowID string) ([]*model.Schedule, error) {
	rows, err := s.db.Query(`
		SELECT id, conversation_id, workflow_id, workflow_input, owner_email, message, cron_expr, timezone,
		       top_k, use_knowledge_base, use_web_fetch, enabled, created_at
		FROM scheduled_messages
		WHERE workflow_id = ? AND owner_email = ?
		ORDER BY created_at DESC`, workflowID, ownerEmail)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanRows(rows)
}

// ListByOwner returns schedules filtered by owner (and optionally conversation).
func (s *Store) ListByOwner(ownerEmail, convID string) ([]*model.Schedule, error) {
	query := `SELECT id, conversation_id, workflow_id, workflow_input, owner_email, message, cron_expr, timezone,
		       top_k, use_knowledge_base, use_web_fetch, enabled, created_at
		FROM scheduled_messages WHERE owner_email = ?`
	args := []any{ownerEmail}
	if convID != "" {
		query += " AND conversation_id = ?"
		args = append(args, convID)
	}
	rows, err := s.db.Query(query+" ORDER BY created_at DESC", args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanRows(rows)
}

func (s *Store) Update(sc *model.Schedule) error {
	_, err := s.db.Exec(`
		UPDATE scheduled_messages
		SET message=?, cron_expr=?, timezone=?, top_k=?, use_knowledge_base=?, use_web_fetch=?, enabled=?
		WHERE id=?`,
		sc.Message, sc.CronExpr, sc.Timezone, sc.TopK,
		sc.UseKnowledgeBase, sc.UseWebFetch, sc.Enabled, sc.ID)
	return err
}

func (s *Store) Delete(id string) error {
	_, err := s.db.Exec(`DELETE FROM scheduled_messages WHERE id = ?`, id)
	return err
}

// ListAll returns all schedules (used at startup to reload into Asynq scheduler).
func (s *Store) ListAll() ([]*model.Schedule, error) {
	rows, err := s.db.Query(`
		SELECT id, conversation_id, workflow_id, workflow_input, owner_email, message, cron_expr, timezone,
		       top_k, use_knowledge_base, use_web_fetch, enabled, created_at
		FROM scheduled_messages ORDER BY created_at`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanRows(rows)
}

// ── Runs ──────────────────────────────────────────────────────────────────────

func (s *Store) InsertRun(scheduleID, runID, status string, startTime time.Time) error {
	_, err := s.db.Exec(`
		INSERT INTO schedule_runs (id, schedule_id, status, start_time)
		VALUES (?, ?, ?, ?)`, runID, scheduleID, status, startTime)
	return err
}

func (s *Store) CompleteRun(runID, status string, closeTime time.Time) error {
	_, err := s.db.Exec(`
		UPDATE schedule_runs SET status=?, close_time=? WHERE id=?`,
		status, closeTime, runID)
	return err
}

// LastRunTime returns the close_time of the most recent completed run, or nil.
func (s *Store) LastRunTime(scheduleID string) *time.Time {
	var t time.Time
	err := s.db.QueryRow(`
		SELECT close_time FROM schedule_runs
		WHERE schedule_id = ? AND close_time IS NOT NULL
		ORDER BY close_time DESC LIMIT 1`, scheduleID).Scan(&t)
	if err != nil {
		return nil
	}
	return &t
}

func (s *Store) ListRuns(scheduleID string) ([]model.ScheduleRun, error) {
	rows, err := s.db.Query(`
		SELECT id, status, start_time, close_time FROM schedule_runs
		WHERE schedule_id = ? ORDER BY start_time DESC LIMIT 20`, scheduleID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var runs []model.ScheduleRun
	for rows.Next() {
		var r model.ScheduleRun
		var start sql.NullTime
		var close sql.NullTime
		if err := rows.Scan(&r.WorkflowID, &r.Status, &start, &close); err != nil {
			return nil, err
		}
		if start.Valid {
			r.StartTime = &start.Time
		}
		if close.Valid {
			r.CloseTime = &close.Time
		}
		runs = append(runs, r)
	}
	return runs, rows.Err()
}

// ── Helpers ───────────────────────────────────────────────────────────────────

func scanRow(row *sql.Row) (*model.Schedule, error) {
	var sc model.Schedule
	var convID, wfID, wfInput sql.NullString
	err := row.Scan(&sc.ID, &convID, &wfID, &wfInput, &sc.OwnerEmail, &sc.Message,
		&sc.CronExpr, &sc.Timezone, &sc.TopK, &sc.UseKnowledgeBase,
		&sc.UseWebFetch, &sc.Enabled, &sc.CreatedAt)
	if err != nil {
		return nil, err
	}
	sc.ConversationID = convID.String
	sc.WorkflowID = wfID.String
	sc.WorkflowInput = wfInput.String
	return &sc, nil
}

func scanRows(rows *sql.Rows) ([]*model.Schedule, error) {
	var out []*model.Schedule
	for rows.Next() {
		var sc model.Schedule
		var convID, wfID, wfInput sql.NullString
		err := rows.Scan(&sc.ID, &convID, &wfID, &wfInput, &sc.OwnerEmail, &sc.Message,
			&sc.CronExpr, &sc.Timezone, &sc.TopK, &sc.UseKnowledgeBase,
			&sc.UseWebFetch, &sc.Enabled, &sc.CreatedAt)
		if err != nil {
			return nil, err
		}
		sc.ConversationID = convID.String
		sc.WorkflowID = wfID.String
		sc.WorkflowInput = wfInput.String
		out = append(out, &sc)
	}
	return out, rows.Err()
}
