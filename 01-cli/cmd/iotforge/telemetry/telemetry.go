package telemetry

import "github.com/spf13/cobra"

// NewCmd creates the parent telemetry command.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "telemetry",
		Short: "Query device telemetry data and run telemetry simulation",
	}
	cmd.AddCommand(newQueryCmd())
	cmd.AddCommand(newSimCmd())
	return cmd
}
