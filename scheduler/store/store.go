package store

import (
	"database/sql"
	"fmt"
	"log"
	"scheduler/config"
	"scheduler/model"
	"time"

	_ "github.com/go-sql-driver/mysql"
)

type Store struct {
	db *sql.DB
}

func Connect(cfg *config.Config) *Store {
	var db *sql.DB
	var err error
	for i := 0; i < 10; i++ {
		db, err = sql.Open("mysql", cfg.DSNI)
		if err == nil {
			if pingErr := db.Ping(); pingErr == nil {
				break
			}
		}
		log.Printf("[store] waiting for MySQL... attempt %d/10", i+1)
		time.Sleep(3 * time.Second)
	}
	if err != nil {
		log.Fatalf("[store] cannot connect to MySQL: %v", err)
	}
	db.SetMaxOpenConns(10)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(5 * time.Minute)
	return &Store{db: db}
}

// ListByConversation returns all schedules for a conversation.
func (s *Store) ListByConversation(conversationID string) ([]model.Schedule, error) {
	rows, err := s.db.Query(`
		SELECT id, conversation_id, owner_email, message, cron_expr,
		       top_k, use_knowledge_base, use_web_fetch, enabled, created_at, updated_at
		FROM scheduled_messages
		WHERE conversation_id = ?
		ORDER BY created_at ASC
	`, conversationID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanSchedules(rows)
}

// ListEnabled returns all enabled schedules (used on startup).
func (s *Store) ListEnabled() ([]model.Schedule, error) {
	rows, err := s.db.Query(`
		SELECT id, conversation_id, owner_email, message, cron_expr,
		       top_k, use_knowledge_base, use_web_fetch, enabled, created_at, updated_at
		FROM scheduled_messages
		WHERE enabled = TRUE
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanSchedules(rows)
}

// GetByID fetches a single schedule.
func (s *Store) GetByID(id int64) (*model.Schedule, error) {
	row := s.db.QueryRow(`
		SELECT id, conversation_id, owner_email, message, cron_expr,
		       top_k, use_knowledge_base, use_web_fetch, enabled, created_at, updated_at
		FROM scheduled_messages WHERE id = ?
	`, id)
	sc, err := scanSchedule(row)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return sc, err
}

// Create inserts a new schedule and returns it with the new ID.
func (s *Store) Create(sc *model.Schedule) error {
	res, err := s.db.Exec(`
		INSERT INTO scheduled_messages
		  (conversation_id, owner_email, message, cron_expr, top_k, use_knowledge_base, use_web_fetch, enabled)
		VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
	`, sc.ConversationID, sc.OwnerEmail, sc.Message, sc.CronExpr, sc.TopK, sc.UseKnowledgeBase, sc.UseWebFetch)
	if err != nil {
		return fmt.Errorf("create schedule: %w", err)
	}
	id, _ := res.LastInsertId()
	sc.ID = id
	return nil
}

// Update modifies a schedule's mutable fields.
func (s *Store) Update(sc *model.Schedule) error {
	_, err := s.db.Exec(`
		UPDATE scheduled_messages
		SET message=?, cron_expr=?, top_k=?, use_knowledge_base=?, use_web_fetch=?, enabled=?
		WHERE id=? AND owner_email=?
	`, sc.Message, sc.CronExpr, sc.TopK, sc.UseKnowledgeBase, sc.UseWebFetch, sc.Enabled,
		sc.ID, sc.OwnerEmail)
	return err
}

// Delete removes a schedule.
func (s *Store) Delete(id int64, ownerEmail string) (bool, error) {
	res, err := s.db.Exec(`DELETE FROM scheduled_messages WHERE id=? AND owner_email=?`, id, ownerEmail)
	if err != nil {
		return false, err
	}
	n, _ := res.RowsAffected()
	return n > 0, nil
}

// ── helpers ──────────────────────────────────────────────────────────────────

func scanSchedules(rows *sql.Rows) ([]model.Schedule, error) {
	var result []model.Schedule
	for rows.Next() {
		sc := model.Schedule{}
		if err := rows.Scan(
			&sc.ID, &sc.ConversationID, &sc.OwnerEmail, &sc.Message, &sc.CronExpr,
			&sc.TopK, &sc.UseKnowledgeBase, &sc.UseWebFetch, &sc.Enabled, &sc.CreatedAt, &sc.UpdatedAt,
		); err != nil {
			return nil, err
		}
		result = append(result, sc)
	}
	return result, rows.Err()
}

type rowScanner interface {
	Scan(dest ...any) error
}

func scanSchedule(row rowScanner) (*model.Schedule, error) {
	sc := &model.Schedule{}
	err := row.Scan(
		&sc.ID, &sc.ConversationID, &sc.OwnerEmail, &sc.Message, &sc.CronExpr,
		&sc.TopK, &sc.UseKnowledgeBase, &sc.UseWebFetch, &sc.Enabled, &sc.CreatedAt, &sc.UpdatedAt,
	)
	return sc, err
}
