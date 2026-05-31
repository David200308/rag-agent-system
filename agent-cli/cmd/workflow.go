package cmd

import (
	"fmt"
	"os"
	"text/tabwriter"

	"github.com/spf13/cobra"
)

var workflowCmd = &cobra.Command{
	Use:     "workflow",
	Short:   "View workflows and run history",
	Aliases: []string{"wf"},
}

var wfListCmd = &cobra.Command{
	Use:   "list",
	Short: "List your workflows",
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var workflows []map[string]any
		if err := c.JSON("GET", "/api/v1/workflow", nil, &workflows); err != nil {
			return err
		}
		if len(workflows) == 0 {
			fmt.Println("No workflows found.")
			return nil
		}
		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(w, "ID\tNAME\tPATTERN\tCREATED")
		for _, wf := range workflows {
			id, _ := wf["id"].(string)
			name, _ := wf["name"].(string)
			pattern, _ := wf["agentPattern"].(string)
			createdAt, _ := wf["createdAt"].(string)
			fmt.Fprintf(w, "%s\t%s\t%s\t%s\n", id, name, pattern, createdAt)
		}
		w.Flush()
		return nil
	},
}

var wfGetCmd = &cobra.Command{
	Use:   "get <id>",
	Short: "Get a workflow and its agents",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var wf map[string]any
		if err := c.JSON("GET", "/api/v1/workflow/"+args[0], nil, &wf); err != nil {
			return err
		}
		printJSON(wf)

		var agents []map[string]any
		if err := c.JSON("GET", "/api/v1/workflow/"+args[0]+"/agents", nil, &agents); err != nil {
			return err
		}
		if len(agents) == 0 {
			return nil
		}
		fmt.Println("\nAgents:")
		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(w, "  ID\tNAME\tROLE")
		for _, a := range agents {
			id := fmt.Sprintf("%v", a["id"])
			name, _ := a["name"].(string)
			role, _ := a["role"].(string)
			fmt.Fprintf(w, "  %s\t%s\t%s\n", id, name, role)
		}
		w.Flush()
		return nil
	},
}

var wfDeleteCmd = &cobra.Command{
	Use:   "delete <id>",
	Short: "Delete a workflow",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		if err := c.JSON("DELETE", "/api/v1/workflow/"+args[0], nil, nil); err != nil {
			return err
		}
		fmt.Printf("Workflow %s deleted.\n", args[0])
		return nil
	},
}

var wfRunsCmd = &cobra.Command{
	Use:   "runs <workflow-id>",
	Short: "List runs for a workflow",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var runs []map[string]any
		if err := c.JSON("GET", "/api/v1/workflow/"+args[0]+"/runs", nil, &runs); err != nil {
			return err
		}
		if len(runs) == 0 {
			fmt.Println("No runs found.")
			return nil
		}
		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(w, "RUN ID\tSTATUS\tSTARTED\tINPUT")
		for _, r := range runs {
			runID, _ := r["id"].(string)
			status, _ := r["status"].(string)
			startedAt, _ := r["startedAt"].(string)
			input, _ := r["userInput"].(string)
			if len(input) > 40 {
				input = input[:40] + "…"
			}
			fmt.Fprintf(w, "%s\t%s\t%s\t%s\n", runID, status, startedAt, input)
		}
		w.Flush()
		return nil
	},
}

var wfLogsCmd = &cobra.Command{
	Use:   "logs <run-id>",
	Short: "Get logs for a workflow run",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var logs []map[string]any
		if err := c.JSON("GET", "/api/v1/workflow/runs/"+args[0]+"/logs", nil, &logs); err != nil {
			return err
		}
		if len(logs) == 0 {
			fmt.Println("No logs found.")
			return nil
		}
		for _, l := range logs {
			ts, _ := l["createdAt"].(string)
			agent, _ := l["agentName"].(string)
			logType, _ := l["logType"].(string)
			content, _ := l["content"].(string)
			if agent != "" {
				fmt.Printf("[%s] [%s] [%s] %s\n", ts, agent, logType, content)
			} else {
				fmt.Printf("[%s] [%s] %s\n", ts, logType, content)
			}
		}
		return nil
	},
}

func init() {
	workflowCmd.AddCommand(wfListCmd)
	workflowCmd.AddCommand(wfGetCmd)
	workflowCmd.AddCommand(wfDeleteCmd)
	workflowCmd.AddCommand(wfRunsCmd)
	workflowCmd.AddCommand(wfLogsCmd)

	rootCmd.AddCommand(workflowCmd)
}
