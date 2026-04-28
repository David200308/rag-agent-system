package cmd

import (
	"encoding/json"
	"fmt"

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
	if _, err := api.Post("/api/v1/auth/request-otp", map[string]string{"email": email}); err != nil {
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
	if config.Token() == "" {
		fmt.Println(y("Not logged in."))
		return fmt.Errorf("not authenticated")
	}
	raw, err := api.Get("/api/v1/auth/validate")
	if err != nil {
		return err
	}
	var res struct {
		Valid bool   `json:"valid"`
		Email string `json:"email"`
	}
	if err := json.Unmarshal(raw, &res); err != nil {
		return err
	}
	if res.Valid {
		fmt.Printf("%s Logged in as %s\n", g("✓"), b(res.Email))
		return nil
	}
	fmt.Println(y("Session expired.") + " Please log in again.")
	_ = config.ClearAuth()
	return fmt.Errorf("session expired")
}
