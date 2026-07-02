package device

import (
	"context"
	"fmt"
	"os"

	"iot-forge-cli/pkg/cliutil"
	"iot-forge-cli/pkg/dto"
	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
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
	if err := cliutil.ParseInput(data, &req); err != nil {
		return err
	}

	cfg, err := cliutil.ResolveConfig()
	if err != nil {
		return err
	}

	c := cliutil.BuildClient(cfg)
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
