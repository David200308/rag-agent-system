package cmd

import (
	"encoding/json"
	"fmt"

	"cli/api"
	"cli/config"

	"github.com/spf13/cobra"
)

var verifyCmd = &cobra.Command{
	Use:   "verify",
	Short: "Verify commands",
}

func init() {
	verifyCmd.AddCommand(&cobra.Command{
		Use:   "otp <code>",
		Short: "Verify OTP and save session token",
		Args:  cobra.ExactArgs(1),
		RunE: func(_ *cobra.Command, args []string) error {
			em := config.Email()
			if em == "" {
				return fmt.Errorf("no pending login — run: agent-system login <email>")
			}
			raw, err := api.Post("/api/v1/auth/verify-otp", map[string]string{
				"email": em,
				"code":  args[0],
			})
			if err != nil {
				return err
			}
			var res struct {
				Token string `json:"token"`
			}
			if err := json.Unmarshal(raw, &res); err != nil {
				return err
			}
			if res.Token == "" {
				return fmt.Errorf("verification failed — check your code and try again")
			}
			if err := config.SetToken(res.Token); err != nil {
				return err
			}
			fmt.Printf("%s Authenticated as %s\n", g("✓"), b(em))
			return nil
		},
	})
	rootCmd.AddCommand(verifyCmd)
}
