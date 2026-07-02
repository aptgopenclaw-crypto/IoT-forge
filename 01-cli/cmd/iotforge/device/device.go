package device

import (
	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/config"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

// NewCmd creates the parent device command.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "device",
		Short: "Manage IoT devices",
		Long:  "Create, read, update, delete, and manage IoT devices.",
	}
	cmd.AddCommand(newListCmd())
	cmd.AddCommand(newGetCmd())
	cmd.AddCommand(newCreateCmd())
	cmd.AddCommand(newUpdateCmd())
	cmd.AddCommand(newDeleteCmd())
	cmd.AddCommand(newDecommissionCmd())
	cmd.AddCommand(newStatsCmd())
	return cmd
}

func resolveConfig() (*config.Config, error) {
	cfg, err := config.Load()
	if err != nil {
		return nil, err
	}
	if v := viper.GetString("endpoint"); v != "" {
		cfg.Endpoint = v
	}
	if v := viper.GetString("api_token"); v != "" {
		cfg.APIToken = v
	}
	return cfg, nil
}

func buildClient(cfg *config.Config) *client.Client {
	return client.NewClientFromConfig(cfg)
}

// toAnySlice converts a typed slice to []any.
func toAnySlice[T any](s []T) []any {
	result := make([]any, len(s))
	for i, v := range s {
		result[i] = v
	}
	return result
}
