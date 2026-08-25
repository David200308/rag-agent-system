package cmd

import (
	"fmt"
	"os"
	"strings"
	"text/tabwriter"

	"github.com/spf13/cobra"

	"agent-cli/internal/style"
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
		fmt.Fprintln(w, style.Header("ID\tTITLE\tMESSAGES\tCREATED"))
		for _, conv := range convs {
			id, _ := conv["id"].(string)
			title, _ := conv["title"].(string)
			if title == "" {
				title = style.Dim("(untitled)")
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
			roleLabel := style.Bold(role)
			if role == "user" {
				roleLabel = style.Cyan(style.Bold(role))
			} else if role == "assistant" {
				roleLabel = style.Green(style.Bold(role))
			}
			fmt.Printf("%s %s: %s\n\n", style.Dim("["+ts+"]"), roleLabel, content)
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
		fmt.Println(style.OK(fmt.Sprintf("Conversation %s deleted.", args[0])))
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
		fmt.Println(style.OK(fmt.Sprintf("Conversation %s archived.", args[0])))
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
		fmt.Println(style.OK(fmt.Sprintf("Conversation %s unarchived.", args[0])))
		return nil
	},
}

var convModelCmd = &cobra.Command{
	Use:   "model <id> [model-name]",
	Short: "Set (or reset) the model for a conversation",
	Long:  "Set the model for a conversation. Omit [model-name] or pass --reset to fall back to the user/system default.",
	Args:  cobra.RangeArgs(1, 2),
	RunE: func(cmd *cobra.Command, args []string) error {
		reset, _ := cmd.Flags().GetBool("reset")
		body := map[string]any{}
		if !reset && len(args) == 2 {
			body["selectedModel"] = args[1]
		} else if !reset {
			return fmt.Errorf("model-name is required (or pass --reset)")
		}

		c := newClient()
		var resp map[string]any
		if err := c.JSON("PATCH", "/api/v1/agent/conversations/"+args[0]+"/model", body, &resp); err != nil {
			return err
		}
		if selected, _ := resp["selectedModel"].(string); selected != "" {
			fmt.Println(style.OK(fmt.Sprintf("Conversation %s model set to %s.", args[0], style.Bold(selected))))
		} else {
			fmt.Println(style.OK(fmt.Sprintf("Conversation %s model reset to default.", args[0])))
		}
		return nil
	},
}

// ── Sharing ───────────────────────────────────────────────────────────────────

var convShareCmd = &cobra.Command{
	Use:   "share",
	Short: "Manage the share link for a conversation",
}

func printShare(share map[string]any) {
	w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
	fmt.Fprintln(w, style.Header("TOKEN\tMODE\tACCESS\tEXPIRES\tCREATED"))
	expires := "never"
	if v, _ := share["expiresAt"].(string); v != "" {
		expires = v
	}
	whitelist, _ := share["whitelist"].([]any)
	access := str(share, "accessType")
	if len(whitelist) > 0 {
		access = fmt.Sprintf("%s (%d)", access, len(whitelist))
	}
	fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\n",
		str(share, "token"), style.Status(str(share, "shareMode")), access, expires, str(share, "createdAt"))
	w.Flush()
}

var convShareCreateCmd = &cobra.Command{
	Use:   "create <id>",
	Short: "Create (or replace) a read-only share link for a conversation",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		accessType, _ := cmd.Flags().GetString("access")
		expireDays, _ := cmd.Flags().GetInt("expire-days")
		whitelistCSV, _ := cmd.Flags().GetString("whitelist")

		body := map[string]any{"accessType": accessType}
		if expireDays > 0 {
			body["expireDays"] = expireDays
		}
		if whitelistCSV != "" {
			body["whitelist"] = strings.Split(whitelistCSV, ",")
		}

		c := newClient()
		var share map[string]any
		if err := c.JSON("POST", "/api/v1/agent/conversations/"+args[0]+"/share", body, &share); err != nil {
			return err
		}
		fmt.Println(style.OK("Share link created."))
		printShare(share)
		return nil
	},
}

var convShareGetCmd = &cobra.Command{
	Use:   "get <id>",
	Short: "Get the current share link for a conversation",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		var share map[string]any
		if err := c.JSON("GET", "/api/v1/agent/conversations/"+args[0]+"/share", nil, &share); err != nil {
			return err
		}
		printShare(share)
		return nil
	},
}

var convShareRevokeCmd = &cobra.Command{
	Use:   "revoke <id>",
	Short: "Revoke the share link for a conversation",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c := newClient()
		if err := c.JSON("DELETE", "/api/v1/agent/conversations/"+args[0]+"/share", nil, nil); err != nil {
			return err
		}
		fmt.Println(style.OK(fmt.Sprintf("Share link for conversation %s revoked.", args[0])))
		return nil
	},
}

func init() {
	convListCmd.Flags().Bool("archived", false, "List archived conversations")
	convModelCmd.Flags().Bool("reset", false, "Reset the conversation to the user/system default model")

	convShareCreateCmd.Flags().String("access", "EVERYONE", "Access type: EVERYONE or WHITELIST")
	convShareCreateCmd.Flags().Int("expire-days", 0, "Days until the share link expires (0 = never)")
	convShareCreateCmd.Flags().String("whitelist", "", "Comma-separated emails (required when --access=WHITELIST)")

	conversationCmd.AddCommand(convListCmd)
	conversationCmd.AddCommand(convGetCmd)
	conversationCmd.AddCommand(convDeleteCmd)
	conversationCmd.AddCommand(convArchiveCmd)
	conversationCmd.AddCommand(convUnarchiveCmd)
	conversationCmd.AddCommand(convModelCmd)

	convShareCmd.AddCommand(convShareCreateCmd, convShareGetCmd, convShareRevokeCmd)
	conversationCmd.AddCommand(convShareCmd)

	rootCmd.AddCommand(conversationCmd)
}
