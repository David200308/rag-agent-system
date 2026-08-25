package cmd

import (
	"encoding/json"
	"fmt"
	"os"
	"text/tabwriter"

	"github.com/spf13/cobra"

	"agent-cli/internal/style"
)

var financialCmd = &cobra.Command{
	Use:     "financial",
	Short:   "Manage your financial portfolio",
	Aliases: []string{"fin"},
}

// ── Deposits ──────────────────────────────────────────────────────────────────

var depositsCmd = &cobra.Command{Use: "deposits", Short: "Manage cash deposits"}

var depositsListCmd = &cobra.Command{
	Use:   "list",
	Short: "List cash deposits",
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var items []map[string]any
		if err := c.JSON("GET", "/api/v1/financial/deposits", nil, &items); err != nil {
			return err
		}
		if len(items) == 0 {
			fmt.Println("No deposits found.")
			return nil
		}
		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(w, style.Header("ID\tPLATFORM\tTYPE\tCURRENCY\tAMOUNT\tCONVERTED"))
		for _, d := range items {
			fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%v\t%v %s\n",
				str(d, "id"), str(d, "platform"), str(d, "depositType"),
				str(d, "currency"), d["amount"],
				d["convertedAmount"], str(d, "convertedCurrency"))
		}
		w.Flush()
		return nil
	},
}

var depositsAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Create a cash deposit",
	Long:  "Create a cash deposit.\n\nExample:\n  agent-cli financial deposits add --data '{\"platform\":\"Chase\",\"currency\":\"USD\",\"amount\":5000}'",
	RunE:  financialCreate("POST", "/api/v1/financial/deposits"),
}

var depositsUpdateCmd = &cobra.Command{
	Use:   "update <id>",
	Short: "Update a cash deposit",
	Args:  cobra.ExactArgs(1),
	RunE:  financialUpdate("PUT", "/api/v1/financial/deposits"),
}

var depositsDeleteCmd = &cobra.Command{
	Use:   "delete <id>",
	Short: "Delete a cash deposit",
	Args:  cobra.ExactArgs(1),
	RunE:  financialDelete("/api/v1/financial/deposits"),
}

// ── Stocks ────────────────────────────────────────────────────────────────────

var stocksCmd = &cobra.Command{Use: "stocks", Short: "Manage stock investments"}

var stocksListCmd = &cobra.Command{
	Use:   "list",
	Short: "List stocks with live prices",
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var items []map[string]any
		if err := c.JSON("GET", "/api/v1/financial/stocks", nil, &items); err != nil {
			return err
		}
		if len(items) == 0 {
			fmt.Println("No stocks found.")
			return nil
		}
		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(w, style.Header("ID\tSYMBOL\tNAME\tSHARES\tCURR PRICE\tPNL%"))
		for _, s := range items {
			pnl := "N/A"
			if v, ok := s["pnlPercent"].(float64); ok {
				pnl = style.PnL(v, fmt.Sprintf("%+.2f%%", v))
			}
			price := "N/A"
			if v, ok := s["currentPrice"].(float64); ok {
				price = fmt.Sprintf("%.4f %s", v, str(s, "priceCurrency"))
			}
			fmt.Fprintf(w, "%s\t%s\t%s\t%v\t%s\t%s\n",
				str(s, "id"), style.Bold(str(s, "symbol")), str(s, "name"),
				s["stockAmount"], price, pnl)
		}
		w.Flush()
		return nil
	},
}

var stocksAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Add a stock investment",
	Long:  "Add a stock investment.\n\nExample:\n  agent-cli financial stocks add --data '{\"symbol\":\"AAPL\",\"stockAmount\":10,\"investAmount\":1500,\"currency\":\"USD\"}'",
	RunE:  financialCreate("POST", "/api/v1/financial/stocks"),
}

var stocksUpdateCmd = &cobra.Command{
	Use:   "update <id>",
	Short: "Update a stock investment",
	Args:  cobra.ExactArgs(1),
	RunE:  financialUpdate("PUT", "/api/v1/financial/stocks"),
}

var stocksDeleteCmd = &cobra.Command{
	Use:   "delete <id>",
	Short: "Delete a stock investment",
	Args:  cobra.ExactArgs(1),
	RunE:  financialDelete("/api/v1/financial/stocks"),
}

// ── Crypto ────────────────────────────────────────────────────────────────────

var cryptoCmd = &cobra.Command{Use: "crypto", Short: "Manage crypto investments"}

var cryptoListCmd = &cobra.Command{
	Use:   "list",
	Short: "List crypto investments with live prices",
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var items []map[string]any
		if err := c.JSON("GET", "/api/v1/financial/crypto", nil, &items); err != nil {
			return err
		}
		if len(items) == 0 {
			fmt.Println("No crypto investments found.")
			return nil
		}
		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(w, style.Header("ID\tSYMBOL\tNAME\tAMOUNT\tCURR PRICE (USDT)\tPNL%"))
		for _, s := range items {
			pnl := "N/A"
			if v, ok := s["pnlPercent"].(float64); ok {
				pnl = style.PnL(v, fmt.Sprintf("%+.2f%%", v))
			}
			price := "N/A"
			if v, ok := s["currentPrice"].(float64); ok {
				price = fmt.Sprintf("%.4f", v)
			}
			fmt.Fprintf(w, "%s\t%s\t%s\t%v\t%s\t%s\n",
				str(s, "id"), style.Bold(str(s, "symbol")), str(s, "name"),
				s["amount"], price, pnl)
		}
		w.Flush()
		return nil
	},
}

var cryptoAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Add a crypto investment",
	Long:  "Add a crypto investment.\n\nExample:\n  agent-cli financial crypto add --data '{\"symbol\":\"BTC\",\"name\":\"Bitcoin\",\"amount\":0.5,\"investAmount\":25000,\"currency\":\"USD\"}'",
	RunE:  financialCreate("POST", "/api/v1/financial/crypto"),
}

var cryptoUpdateCmd = &cobra.Command{
	Use:   "update <id>",
	Short: "Update a crypto investment",
	Args:  cobra.ExactArgs(1),
	RunE:  financialUpdate("PUT", "/api/v1/financial/crypto"),
}

var cryptoDeleteCmd = &cobra.Command{
	Use:   "delete <id>",
	Short: "Delete a crypto investment",
	Args:  cobra.ExactArgs(1),
	RunE:  financialDelete("/api/v1/financial/crypto"),
}

// ── Cards ─────────────────────────────────────────────────────────────────────

var cardsCmd = &cobra.Command{Use: "cards", Short: "Manage payment cards"}

var cardsListCmd = &cobra.Command{
	Use:   "list",
	Short: "List payment cards",
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var items []map[string]any
		if err := c.JSON("GET", "/api/v1/financial/cards", nil, &items); err != nil {
			return err
		}
		if len(items) == 0 {
			fmt.Println("No cards found.")
			return nil
		}
		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(w, style.Header("ID\tBANK\tCARD NAME\tNETWORK\tEXPIRY\tCREDIT LIMIT"))
		for _, card := range items {
			limit := "N/A"
			if v := card["creditLimit"]; v != nil {
				limit = fmt.Sprintf("%v %s", v, str(card, "creditLimitCurrency"))
			}
			fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\n",
				str(card, "id"), str(card, "bank"), str(card, "cardName"),
				str(card, "network"), str(card, "expireDate"), limit)
		}
		w.Flush()
		return nil
	},
}

var cardsAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Add a payment card",
	Long:  "Add a payment card.\n\nExample:\n  agent-cli financial cards add --data '{\"bank\":\"Chase\",\"cardName\":\"Sapphire Reserve\",\"network\":\"Visa\"}'",
	RunE:  financialCreate("POST", "/api/v1/financial/cards"),
}

var cardsUpdateCmd = &cobra.Command{
	Use:   "update <id>",
	Short: "Update a payment card",
	Args:  cobra.ExactArgs(1),
	RunE:  financialUpdate("PUT", "/api/v1/financial/cards"),
}

var cardsDeleteCmd = &cobra.Command{
	Use:   "delete <id>",
	Short: "Delete a payment card",
	Args:  cobra.ExactArgs(1),
	RunE:  financialDelete("/api/v1/financial/cards"),
}

// ── Salary ────────────────────────────────────────────────────────────────────

var salaryCmd = &cobra.Command{Use: "salary", Short: "Manage salary usage records"}

var salaryListCmd = &cobra.Command{
	Use:   "list",
	Short: "List salary usage records",
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var items []map[string]any
		if err := c.JSON("GET", "/api/v1/financial/salary", nil, &items); err != nil {
			return err
		}
		if len(items) == 0 {
			fmt.Println("No salary records found.")
			return nil
		}
		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(w, style.Header("ID\tPERIOD\tREGION\tCURRENCY\tSALARY\tBONUS\tTAX\tEXPENSES"))
		for _, s := range items {
			year, _ := s["year"].(float64)
			month, _ := s["month"].(float64)
			period := fmt.Sprintf("%04d-%02d", int(year), int(month))
			fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%v\t%v\t%v\t%v\n",
				str(s, "id"), period, str(s, "region"), str(s, "currency"),
				s["salary"], s["bonus"], s["tax"], s["totalExpense"])
		}
		w.Flush()
		return nil
	},
}

var salaryAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Create a salary usage record",
	Long:  "Create a salary usage record.\n\nExample:\n  agent-cli financial salary add --data '{\"year\":2026,\"month\":7,\"region\":\"US\",\"currency\":\"USD\",\"salary\":9000,\"bonus\":0,\"tax\":1800}'",
	RunE:  financialCreate("POST", "/api/v1/financial/salary"),
}

var salaryUpdateCmd = &cobra.Command{
	Use:   "update <id>",
	Short: "Update a salary usage record",
	Args:  cobra.ExactArgs(1),
	RunE:  financialUpdate("PUT", "/api/v1/financial/salary"),
}

var salaryDeleteCmd = &cobra.Command{
	Use:   "delete <id>",
	Short: "Delete a salary usage record",
	Args:  cobra.ExactArgs(1),
	RunE:  financialDelete("/api/v1/financial/salary"),
}

// ── Prices ────────────────────────────────────────────────────────────────────

var pricesCmd = &cobra.Command{Use: "prices", Short: "Manage market prices"}

var pricesRefreshCmd = &cobra.Command{
	Use:   "refresh",
	Short: "Force-refresh live market prices",
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		if err := c.JSON("POST", "/api/v1/financial/prices/refresh", nil, nil); err != nil {
			return err
		}
		fmt.Println(style.OK("Prices refreshed."))
		return nil
	},
}

// ── Helpers ───────────────────────────────────────────────────────────────────

func financialCreate(method, path string) func(*cobra.Command, []string) error {
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

func financialUpdate(method, basePath string) func(*cobra.Command, []string) error {
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

func financialDelete(basePath string) func(*cobra.Command, []string) error {
	return func(cmd *cobra.Command, args []string) error {
		c := newClient()
		if err := c.JSON("DELETE", basePath+"/"+args[0], nil, nil); err != nil {
			return err
		}
		fmt.Println(style.OK(fmt.Sprintf("Deleted %s.", args[0])))
		return nil
	}
}

func str(m map[string]any, key string) string {
	v, _ := m[key].(string)
	return v
}

func init() {
	// data flag on add/update commands
	for _, cmd := range []*cobra.Command{
		depositsAddCmd, depositsUpdateCmd,
		stocksAddCmd, stocksUpdateCmd,
		cryptoAddCmd, cryptoUpdateCmd,
		cardsAddCmd, cardsUpdateCmd,
		salaryAddCmd, salaryUpdateCmd,
	} {
		cmd.Flags().String("data", "", "JSON body (required)")
	}

	depositsCmd.AddCommand(depositsListCmd, depositsAddCmd, depositsUpdateCmd, depositsDeleteCmd)
	stocksCmd.AddCommand(stocksListCmd, stocksAddCmd, stocksUpdateCmd, stocksDeleteCmd)
	cryptoCmd.AddCommand(cryptoListCmd, cryptoAddCmd, cryptoUpdateCmd, cryptoDeleteCmd)
	cardsCmd.AddCommand(cardsListCmd, cardsAddCmd, cardsUpdateCmd, cardsDeleteCmd)
	salaryCmd.AddCommand(salaryListCmd, salaryAddCmd, salaryUpdateCmd, salaryDeleteCmd)
	pricesCmd.AddCommand(pricesRefreshCmd)

	financialCmd.AddCommand(depositsCmd, stocksCmd, cryptoCmd, cardsCmd, salaryCmd, pricesCmd)
	rootCmd.AddCommand(financialCmd)
}
