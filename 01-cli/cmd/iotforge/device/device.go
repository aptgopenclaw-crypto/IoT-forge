package device

import "github.com/spf13/cobra"

// NewCmd creates the parent device command.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "device",
		Short: "Manage IoT devices",
		Long:  "Create, read, update, delete, and manage IoT devices.",
	}
	cmd.AddCommand(newListCmd())
	cmd.AddCommand(newGetCmd())
	cmd.AddCommand(newCreateCmd())
	cmd.AddCommand(newUpdateCmd())
	cmd.AddCommand(newDeleteCmd())
	cmd.AddCommand(newDecommissionCmd())
	cmd.AddCommand(newStatsCmd())
	return cmd
}

// toAnySlice converts a typed slice to []any.
func toAnySlice[T any](s []T) []any {
	result := make([]any, len(s))
	for i, v := range s {
		result[i] = v
	}
	return result
}
