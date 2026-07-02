package dispatch

import (
	"iot-forge-cli/pkg/cliutil"
	"context"
	"fmt"
	"strconv"

	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

func newGetCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "get <id>",
		Short: "Get dispatch work order details",
		Args:  cobra.ExactArgs(1),
		RunE:  runGet,
	}
}

func runGet(cmd *cobra.Command, args []string) error {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid dispatch ID: %s", args[0])
	}

	cfg, err := cliutil.ResolveConfig()
	if err != nil {
		return err
	}

	c := cliutil.BuildClient(cfg)
	dispatch, err := c.GetDispatch(context.Background(), id)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		output.NewTableRenderer(fmtFmt, []output.TableColumn{
			{Name: "FIELD", Func: func(r any) string { return r.([]string)[0] }},
			{Name: "VALUE", Func: func(r any) string { return r.([]string)[1] }},
		}).Render([]any{
			[]string{"ID", fmt.Sprintf("%d", dispatch.ID)},
			[]string{"Title", dispatch.Title},
			[]string{"Description", dispatch.Description},
			[]string{"Status", string(dispatch.Status)},
			[]string{"Priority", string(dispatch.Priority)},
			[]string{"Assignee", dispatch.AssignedTo},
			[]string{"Tenant", dispatch.TenantID},
			[]string{"Created", dispatch.CreatedAt},
		})
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{dispatch})
	}
	return nil
}
