package telemetry

import (
	"context"
	"fmt"
	"strconv"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/dto"
	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

var (
	from     string
	to       string
	metric   string
	interval string
)

func newQueryCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "query <device-id>",
		Short: "Query telemetry data for a device",
		Args:  cobra.ExactArgs(1),
		RunE:  runQuery,
	}
	cmd.Flags().StringVar(&from, "from", "", "Start time (ISO8601, required)")
	cmd.Flags().StringVar(&to, "to", "", "End time (default: now)")
	cmd.Flags().StringVar(&metric, "metric", "", "Filter specific metric (e.g. temperature)")
	cmd.Flags().StringVar(&interval, "interval", "", "Aggregation interval (e.g. 1m, 5m, 1h)")
	cmd.MarkFlagRequired("from")
	return cmd
}

func runQuery(cmd *cobra.Command, args []string) error {
	deviceID, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid device ID: %s", args[0])
	}

	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	resp, err := c.QueryTelemetry(context.Background(), deviceID, &client.QueryTelemetryFilter{
		From:     from,
		To:       to,
		Metric:   metric,
		Interval: interval,
	})
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		renderTelemetryTable(resp)
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{resp})
	}
	return nil
}

func renderTelemetryTable(resp *dto.TelemetryQueryResponse) {
	rows := make([]any, len(resp.Readings))
	for i, r := range resp.Readings {
		rows[i] = []string{
			r.Timestamp,
			r.Metric,
			fmt.Sprintf("%.2f", r.Value),
			r.Unit,
		}
	}
	output.NewTableRenderer(output.FormatTable, []output.TableColumn{
		{Name: "TIMESTAMP", Func: func(r any) string { return r.([]string)[0] }},
		{Name: "METRIC", Func: func(r any) string { return r.([]string)[1] }},
		{Name: "VALUE", Func: func(r any) string { return r.([]string)[2] }},
		{Name: "UNIT", Func: func(r any) string { return r.([]string)[3] }},
	}).Render(rows)
}
