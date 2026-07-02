package dispatch

import "github.com/spf13/cobra"

// NewCmd creates the parent dispatch command.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "dispatch",
		Short: "Manage dispatch work orders",
	}
	cmd.AddCommand(newListCmd())
	cmd.AddCommand(newGetCmd())
	cmd.AddCommand(newUpdateCmd())
	return cmd
}
