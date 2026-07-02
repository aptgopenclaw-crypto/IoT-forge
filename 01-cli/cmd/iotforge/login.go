package main

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"syscall"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/config"

	"github.com/spf13/cobra"
	"golang.org/x/term"
)

var loginCmd = &cobra.Command{
	Use:   "login",
	Short: "Log in to IoT-forge",
	Long: `Authenticate with your IoT-forge account.
You will be prompted for username and password, then select a tenant.`,
	RunE: runLogin,
}

func init() {
	rootCmd.AddCommand(loginCmd)
}

func runLogin(cmd *cobra.Command, args []string) error {
	cfg, err := resolveConfig()
	if err != nil {
		return fmt.Errorf("config error: %w", err)
	}

	fmt.Print("Username: ")
	var username string
	if _, err := fmt.Scanln(&username); err != nil {
		return fmt.Errorf("read username: %w", err)
	}

	fmt.Print("Password: ")
	passwordBytes, err := term.ReadPassword(int(syscall.Stdin))
	if err != nil {
		return fmt.Errorf("read password: %w", err)
	}
	password := string(passwordBytes)
	fmt.Println()

	if username == "" || password == "" {
		return fmt.Errorf("username and password are required")
	}

	c := client.New(cfg.Endpoint)
	tempToken, err := c.Login(context.Background(), username, password)
	if err != nil {
		return fmt.Errorf("login failed: %w", err)
	}

	fmt.Println("✓ Authenticated")
	fmt.Println()

	c2 := client.New(cfg.Endpoint, client.WithToken(tempToken))
	tenants, err := c2.ListTenants(context.Background())
	if err != nil {
		return fmt.Errorf("list tenants failed: %w", err)
	}

	if len(tenants) == 0 {
		return fmt.Errorf("no tenants available for this account")
	}

	fmt.Println("Available tenants:")
	for i, t := range tenants {
		fmt.Printf("  %d. %s (%s)\n", i+1, t.Name, t.ID)
	}

	fmt.Print("Select tenant [1]: ")
	var selStr string
	if _, err := fmt.Scanln(&selStr); err != nil {
		selStr = "1"
	}
	sel := 1
	if v, err := strconv.Atoi(strings.TrimSpace(selStr)); err == nil && v >= 1 && v <= len(tenants) {
		sel = v
	}

	selectedTenant := tenants[sel-1]

	resp, err := c2.SelectTenant(context.Background(), tempToken, selectedTenant.ID)
	if err != nil {
		return fmt.Errorf("select tenant failed: %w", err)
	}

	cfg.AccessToken = resp.AccessToken
	cfg.RefreshToken = resp.RefreshToken
	cfg.TokenExpiry = resp.ExpiresIn
	cfg.CurrentTenant = &config.TenantInfo{
		ID:   selectedTenant.ID,
		Name: selectedTenant.Name,
	}
	cfg.APIToken = ""

	if err := config.Save(cfg); err != nil {
		return fmt.Errorf("save config: %w", err)
	}

	fmt.Printf("\n✓ Logged in as %s @ %s (%s)\n", username, selectedTenant.Name, selectedTenant.ID)
	return nil
}
