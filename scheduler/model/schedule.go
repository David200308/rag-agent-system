package model

import "time"

// Schedule mirrors the scheduled_messages table.
type Schedule struct {
	ID               int64     `json:"id"`
	ConversationID   string    `json:"conversationId"`
	OwnerEmail       string    `json:"ownerEmail"`
	Message          string    `json:"message"`
	CronExpr         string    `json:"cronExpr"`         // e.g. "0 8 * * 1"
	TopK             int       `json:"topK"`
	UseKnowledgeBase bool      `json:"useKnowledgeBase"`
	UseWebFetch      bool      `json:"useWebFetch"`
	Enabled          bool      `json:"enabled"`
	CreatedAt        time.Time `json:"createdAt"`
	UpdatedAt        time.Time `json:"updatedAt"`
}

// CreateRequest is the body for POST /schedules.
type CreateRequest struct {
	ConversationID   string `json:"conversationId"`
	Message          string `json:"message"`
	CronMinute       string `json:"cronMinute"`   // 0-59 or *
	CronHour         string `json:"cronHour"`     // 0-23 or *
	CronDay          string `json:"cronDay"`      // 1-31 or *
	CronMonth        string `json:"cronMonth"`    // 1-12 or *
	CronWeekday      string `json:"cronWeekday"`  // 0-6 or *
	TopK             int    `json:"topK"`
	UseKnowledgeBase bool   `json:"useKnowledgeBase"`
	UseWebFetch      bool   `json:"useWebFetch"`
}

// UpdateRequest is the body for PATCH /schedules/{id}.
type UpdateRequest struct {
	Message          *string `json:"message"`
	CronMinute       *string `json:"cronMinute"`
	CronHour         *string `json:"cronHour"`
	CronDay          *string `json:"cronDay"`
	CronMonth        *string `json:"cronMonth"`
	CronWeekday      *string `json:"cronWeekday"`
	TopK             *int    `json:"topK"`
	UseKnowledgeBase *bool   `json:"useKnowledgeBase"`
	UseWebFetch      *bool   `json:"useWebFetch"`
	Enabled          *bool   `json:"enabled"`
}

// BuildCronExpr assembles a 5-field cron expression from individual fields.
// Blank fields default to "*".
func BuildCronExpr(minute, hour, day, month, weekday string) string {
	if minute == "" {
		minute = "*"
	}
	if hour == "" {
		hour = "*"
	}
	if day == "" {
		day = "*"
	}
	if month == "" {
		month = "*"
	}
	if weekday == "" {
		weekday = "*"
	}
	return minute + " " + hour + " " + day + " " + month + " " + weekday
}
