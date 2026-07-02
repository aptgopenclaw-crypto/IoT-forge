package device

import (
	"context"
	"fmt"

	"iot-forge-cli/pkg/cliutil"
	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/dto"
	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

var (
	deviceType string
	status     string
	keyword    string
	page       int
	size       int
)

func newListCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List devices",
		RunE:  runList,
	}
	cmd.Flags().StringVar(&deviceType, "type", "", "Filter by device type")
	cmd.Flags().StringVar(&status, "status", "", "Filter by status (ACTIVE, INACTIVE, DECOMMISSIONED)")
	cmd.Flags().StringVar(&keyword, "keyword", "", "Search keyword")
	cmd.Flags().IntVar(&page, "page", 0, "Page number (0-based)")
	cmd.Flags().IntVar(&size, "size", 20, "Page size")
	return cmd
}

func runList(cmd *cobra.Command, args []string) error {
	cfg, err := cliutil.ResolveConfig()
	if err != nil {
		return fmt.Errorf("config error: %w", err)
	}

	c := cliutil.BuildClient(cfg)

	filter := &client.ListDevicesFilter{
		DeviceType: deviceType,
		Keyword:    keyword,
		Page:       page,
		Size:       size,
	}
	if status != "" {
		filter.Status = dto.DeviceStatus(status)
	}

	devices, total, err := c.ListDevices(context.Background(), filter)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		output.NewTableRenderer(fmtFmt, []output.TableColumn{
			{Name: "ID", Func: func(r any) string { return fmt.Sprintf("%d", r.(dto.DeviceResponse).ID) }},
			{Name: "NAME", Func: func(r any) string { return r.(dto.DeviceResponse).Name }},
			{Name: "TYPE", Func: func(r any) string { return r.(dto.DeviceResponse).DeviceType }},
			{Name: "STATUS", Func: func(r any) string { return string(r.(dto.DeviceResponse).Status) }},
			{Name: "CREATED", Func: func(r any) string { return r.(dto.DeviceResponse).CreatedAt }},
		}).Render(toAnySlice(devices))
	} else {
		if err := output.NewTableRenderer(fmtFmt, nil).Render(toAnySlice(devices)); err != nil {
			return err
		}
	}

	fmt.Printf("\nTotal: %d devices (page %d)\n", total, page)
	return nil
}
