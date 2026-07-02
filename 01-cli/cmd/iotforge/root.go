package main

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
	"iot-forge-cli/pkg/config"
)

var (
	cfgFile   string
	endpoint  string
	outputFmt string
	apiToken  string
)

var rootCmd = &cobra.Command{
	Use:   "iotforge",
	Short: "IoT-forge CLI — manage IoT resources from your terminal",
	Long: `iotforge is a CLI tool for managing IoT-forge platform resources.
It supports interactive login and AI agent API token modes.
All commands respect your RBAC permissions and tenant data scope.`,
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		if cmd.Name() == "login" || cmd.Name() == "logout" {
			return nil
		}
		return nil
	},
}

func Execute() {
	if err := rootCmd.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func init() {
	cobra.OnInitialize(initConfig)

	rootCmd.PersistentFlags().StringVar(&endpoint, "endpoint", "", "API server URL (env: IOTFORGE_ENDPOINT)")
	rootCmd.PersistentFlags().StringVarP(&outputFmt, "output", "o", "", "Output format: table, json, yaml (env: IOTFORGE_OUTPUT)")
	rootCmd.PersistentFlags().StringVar(&apiToken, "api-token", "", "API token for headless mode (env: IOTFORGE_API_TOKEN)")

	viper.BindEnv("endpoint", "IOTFORGE_ENDPOINT")
	viper.BindEnv("output", "IOTFORGE_OUTPUT")
	viper.BindEnv("api_token", "IOTFORGE_API_TOKEN")

	viper.BindPFlag("endpoint", rootCmd.PersistentFlags().Lookup("endpoint"))
	viper.BindPFlag("output", rootCmd.PersistentFlags().Lookup("output"))
	viper.BindPFlag("api_token", rootCmd.PersistentFlags().Lookup("api-token"))
}

func initConfig() {
	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Warning: cannot load config: %v\n", err)
		cfg = &config.Config{Endpoint: "http://localhost:8080", DefaultOutput: "table"}
	}
	viper.SetDefault("endpoint", cfg.Endpoint)
	viper.SetDefault("output", cfg.DefaultOutput)
	viper.SetDefault("api_token", cfg.APIToken)
}

func resolveConfig() (*config.Config, error) {
	cfg, err := config.Load()
	if err != nil {
		return nil, err
	}
	if v := viper.GetString("endpoint"); v != "" {
		cfg.Endpoint = v
	}
	if v := viper.GetString("output"); v != "" {
		cfg.DefaultOutput = v
	}
	if v := viper.GetString("api_token"); v != "" {
		cfg.APIToken = v
	}
	return cfg, nil
}
