package device

import (
	"context"
	"fmt"
	"strconv"

	"github.com/spf13/cobra"
)

func newDecommissionCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "decommission <id>",
		Short: "Mark a device as decommissioned",
		Args:  cobra.ExactArgs(1),
		RunE:  runDecommission,
	}
}

func runDecommission(cmd *cobra.Command, args []string) error {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid device ID: %s", args[0])
	}

	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	if err := c.DecommissionDevice(context.Background(), id); err != nil {
		return err
	}

	fmt.Printf("✓ Device %d decommissioned\n", id)
	return nil
}
