package cmd

import (
	"fmt"
	"os"

	"cli/config"

	"github.com/spf13/cobra"
)

var rootCmd = &cobra.Command{
	Use:   "agent-system",
	Short: "CLI for the RAG Agent System",
}

func Execute() {
	if err := rootCmd.Execute(); err != nil {
		os.Exit(1)
	}
}

func SetVersion(v string) {
	rootCmd.Version = v
}

func init() {
	rootCmd.AddCommand(&cobra.Command{
		Use:   "login <email>",
		Short: "Shortcut for auth login",
		Args:  cobra.ExactArgs(1),
		RunE:  func(_ *cobra.Command, args []string) error { return doLogin(args[0]) },
	})
	rootCmd.AddCommand(&cobra.Command{
		Use:   "logout",
		Short: "Shortcut for auth logout",
		RunE:  func(_ *cobra.Command, _ []string) error { return doLogout() },
	})
	rootCmd.AddCommand(&cobra.Command{
		Use:   "status",
		Short: "Show current authentication status",
		RunE:  func(_ *cobra.Command, _ []string) error { return doStatus() },
	})

	cfgCmd := &cobra.Command{
		Use:   "config",
		Short: "Show or update CLI configuration",
		RunE: func(cmd *cobra.Command, _ []string) error {
			u, _ := cmd.Flags().GetString("base-url")
			if u != "" {
				if err := config.SetBaseURL(u); err != nil {
					return err
				}
				fmt.Println(g("base_url set to") + " " + b(u))
				return nil
			}
			em := config.Email()
			if em == "" {
				em = "(not set)"
			}
			tok := "(not set)"
			if config.Token() != "" {
				tok = "(set)"
			}
			fmt.Printf("base_url : %s\n", c(config.BaseURL()))
			fmt.Printf("email    : %s\n", c(em))
			fmt.Printf("token    : %s\n", c(tok))
			return nil
		},
	}
	cfgCmd.Flags().String("base-url", "", "Set the backend URL")
	rootCmd.AddCommand(cfgCmd)
}
