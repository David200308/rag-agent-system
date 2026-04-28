package cmd

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(&cobra.Command{
		Use:   "uninstall",
		Short: "Remove all local CLI data (config, signing key)",
		Long: `Removes ~/.config/agent-system/ which contains:
  - config.json  (base URL, cached email, session token)
  - signing_key  (Ed25519 private key for agent-openapi, if generated)

The binary itself must be removed manually.`,
		RunE: func(_ *cobra.Command, _ []string) error {
			home, err := os.UserHomeDir()
			if err != nil {
				return err
			}
			dir := filepath.Join(home, ".config", "agent-system")

			if _, err := os.Stat(dir); os.IsNotExist(err) {
				fmt.Println(d("Nothing to remove — config directory does not exist."))
				return nil
			}

			fmt.Printf("This will permanently delete %s\n", b(dir))
			fmt.Print("Continue? [y/N] ")
			sc := bufio.NewScanner(os.Stdin)
			sc.Scan()
			if strings.ToLower(strings.TrimSpace(sc.Text())) != "y" {
				fmt.Println("Aborted.")
				return nil
			}

			if err := os.RemoveAll(dir); err != nil {
				return fmt.Errorf("failed to remove %s: %w", dir, err)
			}
			fmt.Printf("%s Removed %s\n", g("✓"), b(dir))

			self, err := os.Executable()
			if err == nil {
				fmt.Printf("\nTo finish, remove the binary:\n  %s\n", c("rm "+self))
			}
			return nil
		},
	})
}
