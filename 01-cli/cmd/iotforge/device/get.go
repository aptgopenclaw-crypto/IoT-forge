package device

import (
	"context"
	"fmt"
	"strconv"

	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

var tree bool

func newGetCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "get <id>",
		Short: "Get device details",
		Args:  cobra.ExactArgs(1),
		RunE:  runGet,
	}
	cmd.Flags().BoolVar(&tree, "tree", false, "Include device composition tree")
	return cmd
}

func runGet(cmd *cobra.Command, args []string) error {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid device ID: %s", args[0])
	}

	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	device, err := c.GetDevice(context.Background(), id, tree)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		output.NewTableRenderer(fmtFmt, []output.TableColumn{
			{Name: "FIELD", Func: func(r any) string { return r.([]string)[0] }},
			{Name: "VALUE", Func: func(r any) string { return r.([]string)[1] }},
		}).Render([]any{
			[]string{"ID", fmt.Sprintf("%d", device.ID)},
			[]string{"Name", device.Name},
			[]string{"Type", device.DeviceType},
			[]string{"Status", string(device.Status)},
			[]string{"Serial", device.SerialNumber},
			[]string{"Tenant", device.TenantID},
			[]string{"Created", device.CreatedAt},
		})
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{device})
	}
	return nil
}
