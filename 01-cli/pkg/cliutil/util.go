package cliutil

import (
	"encoding/json"
	"fmt"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/config"

	"github.com/spf13/viper"
	"sigs.k8s.io/yaml"
)

// ResolveConfig loads the config file and overlays with viper (env vars + flags).
func ResolveConfig() (*config.Config, error) {
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
	if v := viper.GetString("api-token"); v != "" {
		cfg.APIToken = v
	}
	return cfg, nil
}

// BuildClient creates an API client from config.
func BuildClient(cfg *config.Config) *client.Client {
	return client.NewClientFromConfig(cfg)
}

// ParseInput attempts to unmarshal data as JSON, then YAML.
func ParseInput(data []byte, target any) error {
	if err := json.Unmarshal(data, target); err == nil {
		return nil
	}
	if err := yaml.Unmarshal(data, target); err == nil {
		return nil
	}
	return fmt.Errorf("file must be valid JSON or YAML")
}
