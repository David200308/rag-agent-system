package cmd

import (
	"fmt"
	"os"
	"path/filepath"

	"cli/api"
	"cli/config"

	"github.com/spf13/cobra"
)

var authCmd = &cobra.Command{
	Use:   "auth",
	Short: "Authentication commands",
}

func init() {
	authCmd.AddCommand(&cobra.Command{
		Use:   "login <email>",
		Short: "Request a one-time password",
		Args:  cobra.ExactArgs(1),
		RunE:  func(_ *cobra.Command, args []string) error { return doLogin(args[0]) },
	})
	authCmd.AddCommand(&cobra.Command{
		Use:   "logout",
		Short: "Clear stored credentials",
		RunE:  func(_ *cobra.Command, _ []string) error { return doLogout() },
	})
	authCmd.AddCommand(&cobra.Command{
		Use:   "status",
		Short: "Show current login status",
		RunE:  func(_ *cobra.Command, _ []string) error { return doStatus() },
	})
	rootCmd.AddCommand(authCmd)
}

func doLogin(email string) error {
	if _, err := api.Post("/v1/auth/request-otp", map[string]string{"email": email}); err != nil {
		return err
	}
	if err := config.SetEmail(email); err != nil {
		return err
	}
	fmt.Printf("%s OTP sent to %s\n", g("✓"), b(email))
	fmt.Printf("Run: %s\n", c("agent-system verify otp <code>"))
	return nil
}

func doLogout() error {
	if err := config.ClearAuth(); err != nil {
		return err
	}
	fmt.Println(g("Logged out.") + " Credentials cleared.")
	return nil
}

func doStatus() error {
	keyID := config.KeyID()
	home, _ := os.UserHomeDir()
	keyPath := filepath.Join(home, ".config", "agent-system", "signing_key")

	_, keyErr := os.Stat(keyPath)
	hasKey := keyErr == nil

	if !hasKey || keyID == "" {
		fmt.Println(y("Not ready."))
		if !hasKey {
			fmt.Printf("  Signing key missing — run: %s\n", c("agent-system keygen"))
		}
		if keyID == "" {
			fmt.Printf("  Key ID not set    — run: %s\n", c("agent-system config --key-id <id>"))
		}
		return fmt.Errorf("not configured")
	}

	em := config.Email()
	if em == "" {
		em = "(not set)"
	}
	fmt.Printf("%s Ready\n", g("✓"))
	fmt.Printf("  email   : %s\n", c(em))
	fmt.Printf("  key_id  : %s\n", c(keyID))
	fmt.Printf("  gateway : %s\n", c(config.URL()))
	return nil
}
