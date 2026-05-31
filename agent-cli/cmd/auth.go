package cmd

import (
	"bufio"
	"fmt"
	"os"
	"strings"

	"github.com/spf13/cobra"

	"agent-cli/internal/identity"
)

var authCmd = &cobra.Command{
	Use:   "auth",
	Short: "Login, logout, and check authentication status",
}

var authLoginCmd = &cobra.Command{
	Use:   "login",
	Short: "Authenticate via email OTP",
	RunE: func(cmd *cobra.Command, args []string) error {
		reader := bufio.NewReader(os.Stdin)

		fmt.Print("Email: ")
		email, _ := reader.ReadString('\n')
		email = strings.TrimSpace(email)
		if email == "" {
			return fmt.Errorf("email is required")
		}

		c := newClient()
		var otpResp map[string]string
		if err := c.JSON("POST", "/api/v1/auth/request-otp",
			map[string]string{"email": email}, &otpResp); err != nil {
			return err
		}
		if msg, ok := otpResp["message"]; ok {
			fmt.Println(msg)
		}

		fmt.Print("Code: ")
		code, _ := reader.ReadString('\n')
		code = strings.TrimSpace(code)
		if code == "" {
			return fmt.Errorf("code is required")
		}

		var verifyResp map[string]string
		if err := c.JSON("POST", "/api/v1/auth/verify-otp",
			map[string]string{"email": email, "code": code}, &verifyResp); err != nil {
			return err
		}

		token, ok := verifyResp["token"]
		if !ok || token == "" {
			return fmt.Errorf("no token in response")
		}

		cfg.Token = token
		cfg.Email = email
		if err := cfg.Save(); err != nil {
			return fmt.Errorf("failed to save config: %w", err)
		}

		// Register CLI public key with the server so requests can be signed
		pubKey, err := identity.PublicKeyBase64()
		if err != nil {
			fmt.Fprintf(os.Stderr, "warning: could not generate CLI key: %v\n", err)
		} else {
			c.Token = token // use the fresh token for this call
			var keyResp map[string]string
			if err := c.JSON("POST", "/api/v1/auth/register-key",
				map[string]string{"publicKey": pubKey}, &keyResp); err != nil {
				fmt.Fprintf(os.Stderr, "warning: key registration failed: %v\n", err)
			} else {
				fmt.Printf("CLI key registered (fingerprint: %s)\n", keyResp["fingerprint"])
			}
		}

		fmt.Printf("Logged in as %s\n", email)
		return nil
	},
}

var authLogoutCmd = &cobra.Command{
	Use:   "logout",
	Short: "Clear the stored authentication token",
	RunE: func(cmd *cobra.Command, args []string) error {
		cfg.Token = ""
		if err := cfg.Save(); err != nil {
			return fmt.Errorf("failed to save config: %w", err)
		}
		fmt.Println("Logged out.")
		return nil
	},
}

var authStatusCmd = &cobra.Command{
	Use:   "status",
	Short: "Check if the current token is valid",
	RunE: func(cmd *cobra.Command, args []string) error {
		if cfg.Token == "" {
			fmt.Println("Not logged in.")
			return nil
		}
		c := newClient()
		var resp map[string]any
		if err := c.JSON("GET", "/api/v1/auth/validate", nil, &resp); err != nil {
			return err
		}
		if valid, _ := resp["valid"].(bool); valid {
			fmt.Printf("Logged in as %s\n", resp["email"])
			if fp, err := identity.PublicKeyBase64(); err == nil {
				fmt.Printf("CLI key fingerprint: %s…\n", fp[:8])
			}
		} else {
			fmt.Println("Token is invalid or expired. Run: agent-cli auth login")
		}
		return nil
	},
}

var authConfigCmd = &cobra.Command{
	Use:   "config",
	Short: "Show or set the server URL",
	RunE: func(cmd *cobra.Command, args []string) error {
		url, _ := cmd.Flags().GetString("url")
		if url != "" {
			cfg.Server = url
			if err := cfg.Save(); err != nil {
				return err
			}
			fmt.Printf("Server set to: %s\n", url)
			return nil
		}
		fmt.Printf("Server: %s\n", cfg.Server)
		if cfg.Token != "" {
			fmt.Println("Token:  (set)")
		} else {
			fmt.Println("Token:  (not set)")
		}
		return nil
	},
}

func init() {
	authConfigCmd.Flags().String("url", "", "Set the API server URL")

	authCmd.AddCommand(authLoginCmd)
	authCmd.AddCommand(authLogoutCmd)
	authCmd.AddCommand(authStatusCmd)
	authCmd.AddCommand(authConfigCmd)

	rootCmd.AddCommand(authCmd)
}
