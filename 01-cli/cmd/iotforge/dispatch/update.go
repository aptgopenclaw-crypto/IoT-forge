package dispatch

import (
	"context"
	"fmt"
	"os"
	"strconv"

	"iot-forge-cli/pkg/cliutil"
	"iot-forge-cli/pkg/dto"
	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

var filePath string

func newUpdateCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "update <id> -f <file.json>",
		Short: "Update a dispatch work order",
		Args:  cobra.ExactArgs(1),
		RunE:  runUpdate,
	}
	cmd.Flags().StringVarP(&filePath, "file", "f", "", "JSON or YAML file with dispatch data")
	cmd.MarkFlagRequired("file")
	return cmd
}

func runUpdate(cmd *cobra.Command, args []string) error {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid dispatch ID: %s", args[0])
	}

	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read file: %w", err)
	}

	var req dto.DispatchRequest
	if err := cliutil.ParseInput(data, &req); err != nil {
		return err
	}

	cfg, err := cliutil.ResolveConfig()
	if err != nil {
		return err
	}

	c := cliutil.BuildClient(cfg)
	result, err := c.UpdateDispatch(context.Background(), id, &req)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		fmt.Printf("✓ Dispatch %d updated: %s\n", result.ID, result.Title)
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{result})
	}
	return nil
}
