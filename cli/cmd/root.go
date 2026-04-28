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
			changed := false

			if u, _ := cmd.Flags().GetString("url"); u != "" {
				if err := config.SetURL(u); err != nil {
					return err
				}
				fmt.Println(g("url set to") + " " + b(u))
				changed = true
			}
			if id, _ := cmd.Flags().GetString("key-id"); id != "" {
				if err := config.SetKeyID(id); err != nil {
					return err
				}
				fmt.Println(g("key_id set to") + " " + b(id))
				changed = true
			}
			if changed {
				return nil
			}

			em := config.Email()
			if em == "" {
				em = "(not set)"
			}
			keyID := config.KeyID()
			if keyID == "" {
				keyID = "(not set)"
			}
			fmt.Printf("url    : %s\n", c(config.URL()))
			fmt.Printf("key_id : %s\n", c(keyID))
			fmt.Printf("email  : %s\n", c(em))
			return nil
		},
	}
	cfgCmd.Flags().String("url", "", "Set the agent-openapi gateway URL")
	cfgCmd.Flags().String("key-id", "", "Set the Ed25519 key ID (must match keys.json)")
	rootCmd.AddCommand(cfgCmd)
}
