package cmd

import (
	"fmt"
	"os"
	"text/tabwriter"

	"github.com/spf13/cobra"
)

var conversationCmd = &cobra.Command{
	Use:     "conversation",
	Short:   "Manage your conversations",
	Aliases: []string{"conv"},
}

var convListCmd = &cobra.Command{
	Use:   "list",
	Short: "List conversations",
	RunE: func(cmd *cobra.Command, args []string) error {
		archived, _ := cmd.Flags().GetBool("archived")
		path := "/api/v1/agent/conversations"
		if archived {
			path += "/archived"
		}
		c := newClient()
		var convs []map[string]any
		if err := c.JSON("GET", path, nil, &convs); err != nil {
			return err
		}
		if len(convs) == 0 {
			fmt.Println("No conversations found.")
			return nil
		}
		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(w, "ID\tTITLE\tMESSAGES\tCREATED")
		for _, conv := range convs {
			id, _ := conv["id"].(string)
			title, _ := conv["title"].(string)
			if title == "" {
				title = "(untitled)"
			}
			msgCount := ""
			if msgs, ok := conv["messageCount"].(float64); ok {
				msgCount = fmt.Sprintf("%d", int(msgs))
			}
			createdAt, _ := conv["createdAt"].(string)
			fmt.Fprintf(w, "%s\t%s\t%s\t%s\n", id, title, msgCount, createdAt)
		}
		w.Flush()
		return nil
	},
}

var convGetCmd = &cobra.Command{
	Use:   "get <id>",
	Short: "Get messages in a conversation",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var messages []map[string]any
		if err := c.JSON("GET", "/api/v1/agent/conversations/"+args[0], nil, &messages); err != nil {
			return err
		}
		if len(messages) == 0 {
			fmt.Println("No messages found.")
			return nil
		}
		for _, msg := range messages {
			role, _ := msg["role"].(string)
			content, _ := msg["content"].(string)
			ts, _ := msg["createdAt"].(string)
			fmt.Printf("[%s] %s: %s\n\n", ts, role, content)
		}
		return nil
	},
}

var convDeleteCmd = &cobra.Command{
	Use:   "delete <id>",
	Short: "Delete a conversation",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		if err := c.JSON("DELETE", "/api/v1/agent/conversations/"+args[0], nil, nil); err != nil {
			return err
		}
		fmt.Printf("Conversation %s deleted.\n", args[0])
		return nil
	},
}

var convArchiveCmd = &cobra.Command{
	Use:   "archive <id>",
	Short: "Archive a conversation",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		if err := c.JSON("PATCH", "/api/v1/agent/conversations/"+args[0]+"/archive", nil, nil); err != nil {
			return err
		}
		fmt.Printf("Conversation %s archived.\n", args[0])
		return nil
	},
}

var convUnarchiveCmd = &cobra.Command{
	Use:   "unarchive <id>",
	Short: "Unarchive a conversation",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		if err := c.JSON("PATCH", "/api/v1/agent/conversations/"+args[0]+"/unarchive", nil, nil); err != nil {
			return err
		}
		fmt.Printf("Conversation %s unarchived.\n", args[0])
		return nil
	},
}

func init() {
	convListCmd.Flags().Bool("archived", false, "List archived conversations")

	conversationCmd.AddCommand(convListCmd)
	conversationCmd.AddCommand(convGetCmd)
	conversationCmd.AddCommand(convDeleteCmd)
	conversationCmd.AddCommand(convArchiveCmd)
	conversationCmd.AddCommand(convUnarchiveCmd)

	rootCmd.AddCommand(conversationCmd)
}
