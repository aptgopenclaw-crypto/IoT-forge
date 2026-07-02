package main

import (
	"fmt"
	"time"

	"iot-forge-cli/pkg/cliutil"

	"github.com/spf13/cobra"
)

var authStatusCmd = &cobra.Command{
	Use:   "status",
	Short: "Show current authentication status",
	RunE:  runAuthStatus,
}

func init() {
	authCmd := &cobra.Command{
		Use:   "auth",
		Short: "Authentication commands",
	}
	authCmd.AddCommand(authStatusCmd)
	rootCmd.AddCommand(authCmd)
}

func runAuthStatus(cmd *cobra.Command, args []string) error {
	cfg, err := cliutil.ResolveConfig()
	if err != nil {
		fmt.Println("Not logged in (config not found)")
		return nil
	}

	fmt.Printf("Endpoint:  %s\n", cfg.Endpoint)
	if cfg.CurrentTenant != nil {
		fmt.Printf("Tenant:    %s (%s)\n", cfg.CurrentTenant.Name, cfg.CurrentTenant.ID)
	} else {
		fmt.Println("Tenant:    (not selected)")
	}

	if cfg.AccessToken != "" {
		expiry := time.Unix(cfg.TokenExpiry, 0)
		remaining := time.Until(expiry)
		if remaining > 0 {
			fmt.Printf("Token:     valid (expires in %s)\n", remaining.Round(time.Second))
		} else {
			fmt.Println("Token:     expired")
		}
	} else if cfg.APIToken != "" {
		fmt.Println("Auth:      API token (headless mode)")
	} else {
		fmt.Println("Auth:      not authenticated")
	}
	return nil
}
