package cmd

import (
	"encoding/json"
	"fmt"
	"os"
	"sort"
	"strings"
	"text/tabwriter"

	"github.com/spf13/cobra"

	"agent-cli/internal/style"
)

var travelCmd = &cobra.Command{
	Use:     "travel",
	Short:   "Manage your travel records",
	Aliases: []string{"tr"},
}

var travelListCmd = &cobra.Command{
	Use:   "list",
	Short: "List travel records",
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var items []map[string]any
		if err := c.JSON("GET", "/api/v1/travel", nil, &items); err != nil {
			return err
		}
		if len(items) == 0 {
			fmt.Println("No travel records found.")
			return nil
		}
		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(w, style.Header("ID\tTITLE\tSTART\tEND\tSTOPS\tEXPENSES"))
		for _, r := range items {
			stops, _ := r["stops"].([]any)
			expenses, _ := r["expenses"].([]any)
			fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%d\t%s\n",
				str(r, "id"), style.Bold(str(r, "title")), str(r, "startDate"), str(r, "endDate"),
				len(stops), expenseTotals(expenses))
		}
		w.Flush()
		return nil
	},
}

var travelGetCmd = &cobra.Command{
	Use:   "get <id>",
	Short: "Get a travel record's full detail, including stops and expenses",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var items []map[string]any
		if err := c.JSON("GET", "/api/v1/travel", nil, &items); err != nil {
			return err
		}
		for _, r := range items {
			if str(r, "id") == args[0] {
				printJSON(r)
				return nil
			}
		}
		return fmt.Errorf("travel record %s not found", args[0])
	},
}

var travelAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Create a travel record",
	Long: "Create a travel record.\n\nExample:\n  agent-cli travel add --data '{\"title\":\"Japan Trip\",\"startDate\":\"2026-04-01\",\"endDate\":\"2026-04-10\"," +
		"\"stops\":[{\"city\":\"Tokyo\",\"country\":\"Japan\",\"lat\":35.6762,\"lon\":139.6503,\"transport\":null}]," +
		"\"expenses\":[{\"category\":\"Flight\",\"amount\":800,\"currency\":\"USD\"}]}'",
	RunE: travelCreate("POST", "/api/v1/travel"),
}

var travelUpdateCmd = &cobra.Command{
	Use:   "update <id>",
	Short: "Update a travel record",
	Args:  cobra.ExactArgs(1),
	RunE:  travelUpdate("PUT", "/api/v1/travel"),
}

var travelDeleteCmd = &cobra.Command{
	Use:   "delete <id>",
	Short: "Delete a travel record",
	Args:  cobra.ExactArgs(1),
	RunE:  travelDelete("/api/v1/travel"),
}

// ── Helpers ───────────────────────────────────────────────────────────────────

func travelCreate(method, path string) func(*cobra.Command, []string) error {
	return func(cmd *cobra.Command, args []string) error {
		data, _ := cmd.Flags().GetString("data")
		if data == "" {
			return fmt.Errorf("--data is required (JSON body)")
		}
		var body map[string]any
		if err := json.Unmarshal([]byte(data), &body); err != nil {
			return fmt.Errorf("invalid JSON: %w", err)
		}
		c := newClient()
		var result map[string]any
		if err := c.JSON(method, path, body, &result); err != nil {
			return err
		}
		printJSON(result)
		return nil
	}
}

func travelUpdate(method, basePath string) func(*cobra.Command, []string) error {
	return func(cmd *cobra.Command, args []string) error {
		data, _ := cmd.Flags().GetString("data")
		if data == "" {
			return fmt.Errorf("--data is required (JSON body)")
		}
		var body map[string]any
		if err := json.Unmarshal([]byte(data), &body); err != nil {
			return fmt.Errorf("invalid JSON: %w", err)
		}
		c := newClient()
		var result map[string]any
		if err := c.JSON(method, basePath+"/"+args[0], body, &result); err != nil {
			return err
		}
		printJSON(result)
		return nil
	}
}

func travelDelete(basePath string) func(*cobra.Command, []string) error {
	return func(cmd *cobra.Command, args []string) error {
		c := newClient()
		if err := c.JSON("DELETE", basePath+"/"+args[0], nil, nil); err != nil {
			return err
		}
		fmt.Println(style.OK(fmt.Sprintf("Deleted %s.", args[0])))
		return nil
	}
}

func expenseTotals(expenses []any) string {
	totals := map[string]float64{}
	for _, e := range expenses {
		em, ok := e.(map[string]any)
		if !ok {
			continue
		}
		amount, _ := em["amount"].(float64)
		currency := str(em, "currency")
		if currency == "" {
			currency = "?"
		}
		totals[currency] += amount
	}
	if len(totals) == 0 {
		return "—"
	}
	parts := make([]string, 0, len(totals))
	for currency, amount := range totals {
		parts = append(parts, fmt.Sprintf("%.2f %s", amount, currency))
	}
	sort.Strings(parts)
	return strings.Join(parts, ", ")
}

func init() {
	for _, cmd := range []*cobra.Command{travelAddCmd, travelUpdateCmd} {
		cmd.Flags().String("data", "", "JSON body (required)")
	}

	travelCmd.AddCommand(travelListCmd, travelGetCmd, travelAddCmd, travelUpdateCmd, travelDeleteCmd)
	rootCmd.AddCommand(travelCmd)
}
