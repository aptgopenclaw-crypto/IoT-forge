package device

import (
	"context"
	"encoding/json"
	"fmt"
	"os"

	"iot-forge-cli/pkg/dto"
	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
	"sigs.k8s.io/yaml"
)

var filePath string

func newCreateCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "create -f <file.json>",
		Short: "Create a new device",
		RunE:  runCreate,
	}
	cmd.Flags().StringVarP(&filePath, "file", "f", "", "JSON or YAML file with device data")
	cmd.MarkFlagRequired("file")
	return cmd
}

func runCreate(cmd *cobra.Command, args []string) error {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read file: %w", err)
	}

	var req dto.DeviceRequest
	if err := parseInput(data, &req); err != nil {
		return err
	}

	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	device, err := c.CreateDevice(context.Background(), &req)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		fmt.Printf("✓ Device created: %s (ID: %d)\n", device.Name, device.ID)
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{device})
	}
	return nil
}

func parseInput(data []byte, target any) error {
	if err := json.Unmarshal(data, target); err == nil {
		return nil
	}
	if err := yaml.Unmarshal(data, target); err == nil {
		return nil
	}
	return fmt.Errorf("file must be valid JSON or YAML")
}
