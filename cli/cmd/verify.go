package cmd

import (
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
		Short: "Verify OTP to confirm email ownership",
		Args:  cobra.ExactArgs(1),
		RunE: func(_ *cobra.Command, args []string) error {
			em := config.Email()
			if em == "" {
				return fmt.Errorf("no pending login — run: agent-system login <email>")
			}
			if _, err := api.Post("/v1/auth/verify-otp", map[string]string{
				"email": em,
				"code":  args[0],
			}); err != nil {
				return err
			}
			fmt.Printf("%s Email verified: %s\n", g("✓"), b(em))
			fmt.Printf("Next: %s\n", c("agent-system keygen   # then register your public key in keys.json"))
			return nil
		},
	})
	rootCmd.AddCommand(verifyCmd)
}
