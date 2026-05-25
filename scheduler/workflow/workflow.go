package workflow

import (
	"time"

	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"scheduler/activity"
	"scheduler/model"
)

const TaskQueue = "rag-scheduler"

// RagQueryWorkflow is executed by the Temporal worker on each cron tick.
// Temporal provides durable execution: if the worker crashes mid-run,
// Temporal replays the workflow on the next healthy worker.
func RagQueryWorkflow(ctx workflow.Context, payload model.TriggerPayload) error {
	ctx = workflow.WithActivityOptions(ctx, workflow.ActivityOptions{
		StartToCloseTimeout: 5 * time.Minute,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts:    3,
			InitialInterval:    2 * time.Second,
			BackoffCoefficient: 2.0,
			MaximumInterval:    30 * time.Second,
		},
	})
	return workflow.ExecuteActivity(ctx, activity.TriggerActivity, payload).Get(ctx, nil)
}
