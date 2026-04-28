package cmd

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"text/tabwriter"

	"cli/api"

	"github.com/spf13/cobra"
)

type workflow struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	AgentPattern string `json:"agentPattern"`
	TeamExecMode string `json:"teamExecMode"`
	Description  string `json:"description"`
	Agents       []any  `json:"agents"`
	CreatedAt    any    `json:"createdAt"`
}

type wfAgent struct {
	OrderIndex   int      `json:"orderIndex"`
	Name         string   `json:"name"`
	Role         string   `json:"role"`
	Tools        []string `json:"tools"`
	SystemPrompt string   `json:"systemPrompt"`
}

type wfRun struct {
	ID         string `json:"id"`
	Status     string `json:"status"`
	StartedAt  any    `json:"startedAt"`
	FinishedAt any    `json:"finishedAt"`
}

type wfLog struct {
	Level     string `json:"level"`
	AgentName string `json:"agentName"`
	Message   string `json:"message"`
	CreatedAt any    `json:"createdAt"`
}

var workflowCmd = &cobra.Command{
	Use:   "workflow",
	Short: "Workflow commands",
}

func init() {
	workflowCmd.AddCommand(&cobra.Command{
		Use:   "list",
		Short: "List your workflows",
		RunE: func(_ *cobra.Command, _ []string) error {
			raw, err := api.Get("/api/v1/workflow")
			if err != nil {
				return err
			}
			var wfs []workflow
			if err := json.Unmarshal(raw, &wfs); err != nil {
				return err
			}
			if len(wfs) == 0 {
				fmt.Println(d("No workflows found."))
				return nil
			}
			tw := tabwriter.NewWriter(os.Stdout, 0, 0, 3, ' ', 0)
			fmt.Fprintln(tw, "ID\tNAME\tPATTERN\tAGENTS\tCREATED")
			for _, wf := range wfs {
				fmt.Fprintf(tw, "%s\t%s\t%s\t%d\t%s\n",
					wf.ID, short(wf.Name, 30), wf.AgentPattern, len(wf.Agents), fmtTime(wf.CreatedAt))
			}
			tw.Flush()
			return nil
		},
	})

	workflowCmd.AddCommand(&cobra.Command{
		Use:   "id <workflow-id>",
		Short: "Show workflow details including agents",
		Args:  cobra.ExactArgs(1),
		RunE: func(_ *cobra.Command, args []string) error {
			raw, err := api.Get("/api/v1/workflow/" + args[0])
			if err != nil {
				return err
			}
			var wf workflow
			if err := json.Unmarshal(raw, &wf); err != nil {
				return err
			}
			fmt.Printf("\n%s\n%s\n", b(wf.Name), d(wf.Description))
			fmt.Printf("Pattern: %s   Mode: %s\n\n", y(wf.AgentPattern), c(wf.TeamExecMode))

			agRaw, err := api.Get("/api/v1/workflow/" + args[0] + "/agents")
			if err != nil {
				return err
			}
			var agents []wfAgent
			if err := json.Unmarshal(agRaw, &agents); err != nil {
				return err
			}
			if len(agents) == 0 {
				fmt.Println(d("No agents configured."))
				return nil
			}
			tw := tabwriter.NewWriter(os.Stdout, 0, 0, 3, ' ', 0)
			fmt.Fprintln(tw, "#\tNAME\tROLE\tTOOLS\tSYSTEM PROMPT")
			for _, a := range agents {
				tools := "—"
				if len(a.Tools) > 0 {
					tools = strings.Join(a.Tools, ",")
				}
				fmt.Fprintf(tw, "%d\t%s\t%s\t%s\t%s\n",
					a.OrderIndex, a.Name, a.Role, tools, short(a.SystemPrompt, 50))
			}
			tw.Flush()
			return nil
		},
	})

	runCmd := &cobra.Command{
		Use:   "run <workflow-id> <input>",
		Short: "Trigger a workflow run",
		Args:  cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			notify, _ := cmd.Flags().GetBool("notify")
			fmt.Fprintln(os.Stderr, d("Starting run…"))
			raw, err := api.Post("/api/v1/workflow/"+args[0]+"/runs", map[string]any{
				"userInput":   args[1],
				"emailNotify": notify,
			})
			if err != nil {
				return err
			}
			var res struct {
				RunID string `json:"runId"`
			}
			if err := json.Unmarshal(raw, &res); err != nil {
				return err
			}
			fmt.Printf("%s Run started: %s\n", g("✓"), b(res.RunID))
			fmt.Printf("Check logs: %s\n", c("agent-system workflow logs "+res.RunID))
			return nil
		},
	}
	runCmd.Flags().BoolP("notify", "n", false, "Email notification on completion")
	workflowCmd.AddCommand(runCmd)

	workflowCmd.AddCommand(&cobra.Command{
		Use:   "runs <workflow-id>",
		Short: "List recent runs for a workflow",
		Args:  cobra.ExactArgs(1),
		RunE: func(_ *cobra.Command, args []string) error {
			raw, err := api.Get("/api/v1/workflow/" + args[0] + "/runs")
			if err != nil {
				return err
			}
			var runs []wfRun
			if err := json.Unmarshal(raw, &runs); err != nil {
				return err
			}
			if len(runs) == 0 {
				fmt.Println(d("No runs found."))
				return nil
			}
			tw := tabwriter.NewWriter(os.Stdout, 0, 0, 3, ' ', 0)
			fmt.Fprintln(tw, "RUN ID\tSTATUS\tSTARTED\tFINISHED")
			for _, ru := range runs {
				fmt.Fprintf(tw, "%s\t%s\t%s\t%s\n",
					ru.ID, ru.Status, fmtTime(ru.StartedAt), fmtTime(ru.FinishedAt))
			}
			tw.Flush()
			return nil
		},
	})

	workflowCmd.AddCommand(&cobra.Command{
		Use:   "logs <run-id>",
		Short: "Show logs for a completed workflow run",
		Args:  cobra.ExactArgs(1),
		RunE: func(_ *cobra.Command, args []string) error {
			raw, err := api.Get("/api/v1/workflow/runs/" + args[0] + "/logs")
			if err != nil {
				return err
			}
			var logs []wfLog
			if err := json.Unmarshal(raw, &logs); err != nil {
				return err
			}
			if len(logs) == 0 {
				fmt.Println(d("No logs found."))
				return nil
			}
			for _, entry := range logs {
				lvl := fmt.Sprintf("%-5s", entry.Level)
				var lvlColor string
				switch entry.Level {
				case "ERROR":
					lvlColor = r(lvl)
				case "WARN":
					lvlColor = y(lvl)
				case "INFO":
					lvlColor = c(lvl)
				default:
					lvlColor = d(lvl)
				}
				fmt.Printf("%s  %s  %s  %s\n",
					d(fmtTime(entry.CreatedAt)), lvlColor, b(entry.AgentName), entry.Message)
			}
			return nil
		},
	})

	rootCmd.AddCommand(workflowCmd)
}
