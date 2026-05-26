package model

import "time"

// Schedule is the REST representation returned to clients.
// ID is a UUID string.
type Schedule struct {
	ID               string     `json:"id"`
	ConversationID   string     `json:"conversationId"`
	OwnerEmail       string     `json:"ownerEmail"`
	Message          string     `json:"message"`
	CronExpr         string     `json:"cronExpr"`
	Timezone         string     `json:"timezone"`
	TopK             int        `json:"topK"`
	UseKnowledgeBase bool       `json:"useKnowledgeBase"`
	UseWebFetch      bool       `json:"useWebFetch"`
	Enabled          bool       `json:"enabled"`
	NextRunAt        *time.Time `json:"nextRunAt,omitempty"`
	LastRunAt        *time.Time `json:"lastRunAt,omitempty"`
	CreatedAt        time.Time  `json:"createdAt"`
}

// ScheduleRun is a single execution record stored in schedule_runs.
type ScheduleRun struct {
	WorkflowID string     `json:"workflowId"` // run UUID
	Status     string     `json:"status"`
	StartTime  *time.Time `json:"startTime,omitempty"`
	CloseTime  *time.Time `json:"closeTime,omitempty"`
}

// CreateRequest is the body for POST /schedules.
type CreateRequest struct {
	ConversationID   string `json:"conversationId"`
	Message          string `json:"message"`
	CronMinute       string `json:"cronMinute"`
	CronHour         string `json:"cronHour"`
	CronDay          string `json:"cronDay"`
	CronMonth        string `json:"cronMonth"`
	CronWeekday      string `json:"cronWeekday"`
	Timezone         string `json:"timezone"` // IANA name, e.g. "America/New_York". Defaults to "UTC".
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
	Timezone         *string `json:"timezone"`
	TopK             *int    `json:"topK"`
	UseKnowledgeBase *bool   `json:"useKnowledgeBase"`
	UseWebFetch      *bool   `json:"useWebFetch"`
	Enabled          *bool   `json:"enabled"`
}

// InternalCreateRequest is the body for POST /internal/schedules (service-key auth).
// Used by the Spring Boot workflow engine to create schedules on behalf of a user.
type InternalCreateRequest struct {
	OwnerEmail       string `json:"ownerEmail"`
	ConversationID   string `json:"conversationId"`
	Message          string `json:"message"`
	CronExpr         string `json:"cronExpr"` // full 5-field cron expression
	Timezone         string `json:"timezone"`
	TopK             int    `json:"topK"`
	UseKnowledgeBase bool   `json:"useKnowledgeBase"`
	UseWebFetch      bool   `json:"useWebFetch"`
}

// BuildCronExpr assembles a 5-field cron expression. Blank fields default to "*".
func BuildCronExpr(minute, hour, day, month, weekday string) string {
	f := func(s string) string {
		if s == "" {
			return "*"
		}
		return s
	}
	return f(minute) + " " + f(hour) + " " + f(day) + " " + f(month) + " " + f(weekday)
}
