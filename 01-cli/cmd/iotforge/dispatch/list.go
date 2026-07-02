package dispatch

import (
	"context"
	"fmt"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/dto"
	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

var (
	statusFilter   string
	priorityFilter string
	keywordFilter  string
	page           int
	size           int
)

func newListCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List dispatch work orders",
		RunE:  runList,
	}
	cmd.Flags().StringVar(&statusFilter, "status", "", "Filter by status (PENDING, APPROVED, IN_PROGRESS, COMPLETED, REJECTED)")
	cmd.Flags().StringVar(&priorityFilter, "priority", "", "Filter by priority (LOW, MEDIUM, HIGH, URGENT)")
	cmd.Flags().StringVar(&keywordFilter, "keyword", "", "Search keyword")
	cmd.Flags().IntVar(&page, "page", 0, "Page number (0-based)")
	cmd.Flags().IntVar(&size, "size", 20, "Page size")
	return cmd
}

func runList(cmd *cobra.Command, args []string) error {
	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	filter := &client.ListDispatchFilter{
		Keyword: keywordFilter,
		Page:    page,
		Size:    size,
	}
	if statusFilter != "" {
		filter.Status = dto.DispatchStatus(statusFilter)
	}
	if priorityFilter != "" {
		filter.Priority = dto.DispatchPriority(priorityFilter)
	}

	dispatches, total, err := c.ListDispatches(context.Background(), filter)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		rows := make([]any, len(dispatches))
		for i, d := range dispatches {
			rows[i] = []string{
				fmt.Sprintf("%d", d.ID),
				d.Title,
				string(d.Status),
				string(d.Priority),
				d.AssignedTo,
				d.CreatedAt,
			}
		}
		output.NewTableRenderer(fmtFmt, []output.TableColumn{
			{Name: "ID", Func: func(r any) string { return r.([]string)[0] }},
			{Name: "TITLE", Func: func(r any) string { return r.([]string)[1] }},
			{Name: "STATUS", Func: func(r any) string { return r.([]string)[2] }},
			{Name: "PRIORITY", Func: func(r any) string { return r.([]string)[3] }},
			{Name: "ASSIGNEE", Func: func(r any) string { return r.([]string)[4] }},
			{Name: "CREATED", Func: func(r any) string { return r.([]string)[5] }},
		}).Render(rows)
	} else {
		rows := make([]any, len(dispatches))
		for i, d := range dispatches {
			rows[i] = d
		}
		output.NewTableRenderer(fmtFmt, nil).Render(rows)
	}

	fmt.Printf("\nTotal: %d dispatch orders (page %d)\n", total, page)
	return nil
}
