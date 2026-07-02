package device

import (
	"iot-forge-cli/pkg/cliutil"
	"context"
	"fmt"

	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

func newStatsCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "stats",
		Short: "Device statistics summary",
		RunE:  runStats,
	}
}

func runStats(cmd *cobra.Command, args []string) error {
	cfg, err := cliutil.ResolveConfig()
	if err != nil {
		return err
	}

	c := cliutil.BuildClient(cfg)
	stats, err := c.GetDeviceStats(context.Background())
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		var rows []any
		for k, v := range stats.TotalByType {
			rows = append(rows, []string{"Type: " + k, fmt.Sprintf("%d", v)})
		}
		for k, v := range stats.TotalByStatus {
			rows = append(rows, []string{"Status: " + k, fmt.Sprintf("%d", v)})
		}
		rows = append(rows, []string{"Online Rate", fmt.Sprintf("%.1f%%", stats.OnlineRate*100)})
		rows = append(rows, []string{"Open Dispatches", fmt.Sprintf("%d", stats.OpenDispatches)})

		output.NewTableRenderer(fmtFmt, []output.TableColumn{
			{Name: "METRIC", Func: func(r any) string { return r.([]string)[0] }},
			{Name: "VALUE", Func: func(r any) string { return r.([]string)[1] }},
		}).Render(rows)
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{stats})
	}
	return nil
}
