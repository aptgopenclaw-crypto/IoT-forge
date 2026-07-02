package device

import (
	"iot-forge-cli/pkg/cliutil"
	"context"
	"fmt"
	"strconv"

	"github.com/spf13/cobra"
)

func newDeleteCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "delete <id>",
		Short: "Delete a device (fails if device has children)",
		Args:  cobra.ExactArgs(1),
		RunE:  runDelete,
	}
}

func runDelete(cmd *cobra.Command, args []string) error {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid device ID: %s", args[0])
	}

	cfg, err := cliutil.ResolveConfig()
	if err != nil {
		return err
	}

	c := cliutil.BuildClient(cfg)
	if err := c.DeleteDevice(context.Background(), id); err != nil {
		return err
	}

	fmt.Printf("✓ Device %d deleted\n", id)
	return nil
}
