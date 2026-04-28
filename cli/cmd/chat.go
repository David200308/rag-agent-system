package cmd

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"text/tabwriter"
	"time"

	"cli/api"

	"github.com/spf13/cobra"
)

type conversation struct {
	ID        string `json:"id"`
	Title     string `json:"title"`
	CreatedAt any    `json:"createdAt"`
}

type chatMessage struct {
	Role      string `json:"role"`
	Content   string `json:"content"`
	CreatedAt any    `json:"createdAt"`
}

type queryResp struct {
	Answer  string `json:"answer"`
	Sources []struct {
		Source string `json:"source"`
	} `json:"sources"`
	Metadata struct {
		ConversationID string `json:"conversationId"`
	} `json:"metadata"`
	RouteDecision struct {
		Route string `json:"route"`
	} `json:"routeDecision"`
}

var chatCmd = &cobra.Command{
	Use:   "chat",
	Short: "Chat / conversation commands",
}

func init() {
	chatCmd.AddCommand(&cobra.Command{
		Use:   "list",
		Short: "List your conversations",
		RunE: func(_ *cobra.Command, _ []string) error {
			raw, err := api.Get("/api/v1/agent/conversations")
			if err != nil {
				return err
			}
			var convos []conversation
			if err := json.Unmarshal(raw, &convos); err != nil {
				return err
			}
			if len(convos) == 0 {
				fmt.Println(d("No conversations found."))
				return nil
			}
			tw := tabwriter.NewWriter(os.Stdout, 0, 0, 3, ' ', 0)
			fmt.Fprintln(tw, "ID\tTITLE\tCREATED")
			for _, cv := range convos {
				title := short(cv.Title, 55)
				if title == "" {
					title = cv.ID
				}
				fmt.Fprintf(tw, "%s\t%s\t%s\n", cv.ID, title, fmtTime(cv.CreatedAt))
			}
			tw.Flush()
			return nil
		},
	})

	chatCmd.AddCommand(&cobra.Command{
		Use:   "id <conversation-id>",
		Short: "Show message history for a conversation",
		Args:  cobra.ExactArgs(1),
		RunE: func(_ *cobra.Command, args []string) error {
			raw, err := api.Get("/api/v1/agent/conversations/" + args[0])
			if err != nil {
				return err
			}
			var msgs []chatMessage
			if err := json.Unmarshal(raw, &msgs); err != nil {
				return err
			}
			if len(msgs) == 0 {
				fmt.Println(d("No messages found."))
				return nil
			}
			sep := d(strings.Repeat("─", 60))
			for _, m := range msgs {
				fmt.Println(sep)
				if m.Role == "user" {
					fmt.Printf("%s  %s\n%s\n", b("You"), d(fmtTime(m.CreatedAt)), m.Content)
				} else {
					fmt.Printf("%s  %s\n%s\n", g("Assistant"), d(fmtTime(m.CreatedAt)), m.Content)
				}
			}
			fmt.Println(sep)
			return nil
		},
	})

	askCmd := &cobra.Command{
		Use:   "ask <message>",
		Short: "Send a message to the RAG agent",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			convID, _ := cmd.Flags().GetString("conversation")
			body := map[string]any{"query": args[0]}
			if convID != "" {
				body["conversationId"] = convID
			}
			fmt.Fprintln(os.Stderr, d("Querying agent…"))
			raw, err := api.Post("/api/v1/agent/query", body)
			if err != nil {
				return err
			}
			var res queryResp
			if err := json.Unmarshal(raw, &res); err != nil {
				return err
			}
			fmt.Printf("\n%s\n%s\n", g("Assistant"), res.Answer)
			if len(res.Sources) > 0 {
				fmt.Printf("\n%s\n", d("Sources:"))
				for i, s := range res.Sources {
					if i >= 5 {
						break
					}
					fmt.Printf("  %s %s\n", d("·"), s.Source)
				}
			}
			if res.Metadata.ConversationID != "" {
				fmt.Printf("\n%s  route: %s\n",
					d("conversation: "+res.Metadata.ConversationID),
					d(res.RouteDecision.Route))
			}
			return nil
		},
	}
	askCmd.Flags().StringP("conversation", "c", "", "Continue an existing conversation")
	chatCmd.AddCommand(askCmd)

	chatCmd.AddCommand(&cobra.Command{
		Use:   "delete <conversation-id>",
		Short: "Delete a conversation and all its messages",
		Args:  cobra.ExactArgs(1),
		RunE: func(_ *cobra.Command, args []string) error {
			fmt.Printf("Delete conversation %s? [y/N] ", args[0])
			sc := bufio.NewScanner(os.Stdin)
			sc.Scan()
			if strings.ToLower(strings.TrimSpace(sc.Text())) != "y" {
				fmt.Println("Aborted.")
				return nil
			}
			if _, err := api.Delete("/api/v1/agent/conversations/" + args[0]); err != nil {
				return err
			}
			fmt.Printf("%s Deleted conversation %s\n", g("✓"), b(args[0]))
			return nil
		},
	})

	rootCmd.AddCommand(chatCmd)
}

func short(s string, n int) string {
	s = strings.ReplaceAll(strings.TrimSpace(s), "\n", " ")
	runes := []rune(s)
	if len(runes) > n {
		return string(runes[:n]) + "…"
	}
	return s
}

func fmtTime(v any) string {
	switch val := v.(type) {
	case float64:
		return time.UnixMilli(int64(val)).UTC().Format("2006-01-02 15:04")
	case string:
		t, err := time.Parse(time.RFC3339, strings.Replace(val, "Z", "+00:00", 1))
		if err != nil {
			return val
		}
		return t.UTC().Format("2006-01-02 15:04")
	}
	return ""
}
