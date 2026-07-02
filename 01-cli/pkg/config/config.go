package config

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

type TenantInfo struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type Config struct {
	Endpoint      string      `json:"endpoint"`
	DefaultOutput string      `json:"default_output"`
	CurrentTenant *TenantInfo `json:"current_tenant,omitempty"`
	AccessToken   string      `json:"access_token,omitempty"`
	TokenExpiry   int64       `json:"token_expiry,omitempty"`
	RefreshToken  string      `json:"refresh_token,omitempty"`
	APIToken      string      `json:"api_token,omitempty"`
}

func ConfigDir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("cannot find home dir: %w", err)
	}
	return filepath.Join(home, ".iotforge"), nil
}

func ConfigPath() (string, error) {
	dir, err := ConfigDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "config.json"), nil
}

func Load() (*Config, error) {
	path, err := ConfigPath()
	if err != nil {
		return nil, err
	}
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return &Config{Endpoint: "http://localhost:8080", DefaultOutput: "table"}, nil
		}
		return nil, fmt.Errorf("read config: %w", err)
	}
	var cfg Config
	if err := json.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("parse config: %w", err)
	}
	if cfg.Endpoint == "" {
		cfg.Endpoint = "http://localhost:8080"
	}
	if cfg.DefaultOutput == "" {
		cfg.DefaultOutput = "table"
	}
	return &cfg, nil
}

func Save(cfg *Config) error {
	dir, err := ConfigDir()
	if err != nil {
		return err
	}
	if err := os.MkdirAll(dir, 0700); err != nil {
		return fmt.Errorf("create config dir: %w", err)
	}
	path, err := ConfigPath()
	if err != nil {
		return err
	}
	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return fmt.Errorf("marshal config: %w", err)
	}
	if err := os.WriteFile(path, data, 0600); err != nil {
		return fmt.Errorf("write config: %w", err)
	}
	return nil
}

func Clear() error {
	path, err := ConfigPath()
	if err != nil {
		return err
	}
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("remove config: %w", err)
	}
	return nil
}
