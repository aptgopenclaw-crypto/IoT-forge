package main

import (
	"context"
	"fmt"
	"os"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/config"

	"github.com/spf13/cobra"
)

var logoutCmd = &cobra.Command{
	Use:   "logout",
	Short: "Log out and clear stored credentials",
	RunE:  runLogout,
}

func init() {
	rootCmd.AddCommand(logoutCmd)
}

func runLogout(cmd *cobra.Command, args []string) error {
	cfg, err := resolveConfig()
	if err != nil {
		// If config is unreadable, just clear it
		if err := config.Clear(); err != nil {
			return fmt.Errorf("clear config: %w", err)
		}
		fmt.Println("✓ Logged out")
		return nil
	}

	if cfg.AccessToken != "" || cfg.APIToken != "" {
		c := client.New(cfg.Endpoint,
			client.WithToken(cfg.AccessToken),
			client.WithAPIToken(cfg.APIToken),
		)
		if err := c.Logout(context.Background()); err != nil {
			fmt.Fprintf(os.Stderr, "Warning: server logout failed: %v\n", err)
		}
	}

	if err := config.Clear(); err != nil {
		return fmt.Errorf("clear config: %w", err)
	}

	fmt.Println("✓ Logged out")
	return nil
}
