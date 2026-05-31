package cmd

import (
	"bufio"
	"fmt"
	"os"
	"strings"

	"github.com/spf13/cobra"
)

var chatCmd = &cobra.Command{
	Use:   "chat",
	Short: "Chat with the RAG agent",
	Long: `Start an interactive chat session with the RAG agent.
Press Ctrl+C or type 'exit' to quit.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		return runREPL()
	},
}

var chatAskCmd = &cobra.Command{
	Use:   "ask <question>",
	Short: "Send a single query to the agent",
	Args:  cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		convID, _ := cmd.Flags().GetString("conversation")
		question := strings.Join(args, " ")
		return askAgent(question, convID)
	},
}

func runREPL() error {
	c := newClient()
	_ = c
	reader := bufio.NewReader(os.Stdin)
	var conversationID string

	fmt.Println("RAG Agent — type 'exit' to quit, 'new' to start a new conversation.")
	if conversationID != "" {
		fmt.Printf("Conversation: %s\n", conversationID)
	}
	fmt.Println()

	for {
		fmt.Print("You: ")
		line, err := reader.ReadString('\n')
		if err != nil {
			return nil
		}
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		if line == "exit" || line == "quit" {
			fmt.Println("Bye!")
			return nil
		}
		if line == "new" {
			conversationID = ""
			fmt.Println("Started a new conversation.")
			continue
		}

		body := map[string]any{"query": line}
		if conversationID != "" {
			body["conversationId"] = conversationID
		}

		var resp map[string]any
		if err := c.JSON("POST", "/api/v1/agent/query", body, &resp); err != nil {
			fmt.Fprintf(os.Stderr, "error: %v\n", err)
			continue
		}

		answer, _ := resp["answer"].(string)
		fmt.Printf("\nAgent: %s\n\n", answer)

		// Persist conversation ID across turns
		if meta, ok := resp["metadata"].(map[string]any); ok {
			if id, ok := meta["conversationId"].(string); ok && id != "" {
				conversationID = id
			}
		}
	}
}

func askAgent(question, conversationID string) error {
	c := newClient()
	body := map[string]any{"query": question}
	if conversationID != "" {
		body["conversationId"] = conversationID
	}

	var resp map[string]any
	if err := c.JSON("POST", "/api/v1/agent/query", body, &resp); err != nil {
		return err
	}

	answer, _ := resp["answer"].(string)
	fmt.Println(answer)

	if meta, ok := resp["metadata"].(map[string]any); ok {
		if id, ok := meta["conversationId"].(string); ok && id != "" {
			fmt.Printf("\n[conversation: %s]\n", id)
		}
	}
	return nil
}

func init() {
	chatAskCmd.Flags().StringP("conversation", "c", "", "Continue an existing conversation by ID")
	chatCmd.AddCommand(chatAskCmd)
	rootCmd.AddCommand(chatCmd)
}
