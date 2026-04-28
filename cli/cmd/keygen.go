package cmd

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(&cobra.Command{
		Use:   "keygen",
		Short: "Generate an Ed25519 signing keypair for use with agent-openapi",
		RunE: func(_ *cobra.Command, _ []string) error {
			pub, priv, err := ed25519.GenerateKey(rand.Reader)
			if err != nil {
				return fmt.Errorf("key generation failed: %w", err)
			}

			home, _ := os.UserHomeDir()
			keyDir := filepath.Join(home, ".config", "agent-system")
			if err := os.MkdirAll(keyDir, 0755); err != nil {
				return err
			}
			keyPath := filepath.Join(keyDir, "signing_key")
			if err := os.WriteFile(keyPath, []byte(base64.StdEncoding.EncodeToString(priv)), 0600); err != nil {
				return fmt.Errorf("failed to save private key: %w", err)
			}

			pubB64 := base64.StdEncoding.EncodeToString(pub)
			fmt.Printf("%s Private key saved to %s\n\n", g("✓"), b(keyPath))
			fmt.Printf("%s\n%s\n\n", b("Public key (register in agent-openapi/keys.json):"), c(pubB64))
			fmt.Printf("1. Add to agent-openapi/keys.json:\n")
			fmt.Printf(`   {"id": "my-key", "public_key": "%s", "email": "you@example.com"}`+"\n\n", pubB64)
			fmt.Printf("2. Tell the CLI which key ID to use:\n")
			fmt.Printf("   %s\n", b("agent-system config --key-id my-key"))
			return nil
		},
	})
}
