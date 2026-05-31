package cmd

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/spf13/cobra"

	"agent-cli/internal/client"
	"agent-cli/internal/config"
)

// Version is overridden at build time via -ldflags "-X agent-cli/cmd.Version=x.y.z"
var Version = "dev"

var (
	serverFlag string
	tokenFlag  string
	cfg        *config.Config
)

var rootCmd = &cobra.Command{
	Use:   "agent-cli",
	Short: "CLI for the RAG Agent System",
	Long: `agent-cli — interact with your RAG Agent System from the terminal.

Commands:
  auth         Login, logout, and check authentication status
  chat         Send queries to the agent (interactive or single-shot)
  conversation Manage your conversations
  workflow     View workflows and their run history
  financial    Manage your financial portfolio`,
	SilenceUsage: true,
}

func Execute() {
	var err error
	cfg, err = config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}
	if err := rootCmd.Execute(); err != nil {
		os.Exit(1)
	}
}

func init() {
	rootCmd.PersistentFlags().StringVar(&serverFlag, "server", "", "API server base URL (overrides config)")
	rootCmd.PersistentFlags().StringVar(&tokenFlag, "token", "", "Bearer token (overrides config)")

	cobra.OnInitialize(func() {
		if serverFlag != "" {
			cfg.Server = serverFlag
		}
		if tokenFlag != "" {
			cfg.Token = tokenFlag
		}
	})
}

func newClient() *client.Client {
	client.Version = Version
	return client.New(cfg)
}

func printJSON(v any) {
	enc := json.NewEncoder(os.Stdout)
	enc.SetIndent("", "  ")
	_ = enc.Encode(v)
}

func errorf(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "error: "+format+"\n", args...)
	os.Exit(1)
}
