package dispatch

import (
	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/config"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

// NewCmd creates the parent dispatch command.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "dispatch",
		Short: "Manage dispatch work orders",
	}
	cmd.AddCommand(newListCmd())
	cmd.AddCommand(newGetCmd())
	cmd.AddCommand(newUpdateCmd())
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
