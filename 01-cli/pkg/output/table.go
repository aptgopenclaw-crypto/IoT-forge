package output

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"text/tabwriter"

	"sigs.k8s.io/yaml"
)

var DefaultWriter io.Writer = os.Stdout

type Format string

const (
	FormatTable Format = "table"
	FormatJSON  Format = "json"
	FormatYAML  Format = "yaml"
)

func ParseFormat(s string) Format {
	switch s {
	case "json":
		return FormatJSON
	case "yaml", "yml":
		return FormatYAML
	default:
		return FormatTable
	}
}

// TableRenderer renders a slice of structs as a table.
type TableRenderer struct {
	Format   Format
	Columns  []TableColumn
	NoHeader bool
}

type TableColumn struct {
	Name string
	Func func(any) string
}

func NewTableRenderer(fmt Format, columns []TableColumn) *TableRenderer {
	return &TableRenderer{Format: fmt, Columns: columns}
}

func (r *TableRenderer) Render(data []any) error {
	switch r.Format {
	case FormatJSON:
		return renderJSON(DefaultWriter, data)
	case FormatYAML:
		return renderYAML(DefaultWriter, data)
	default:
		return r.renderTable(data)
	}
}

func (r *TableRenderer) renderTable(data []any) error {
	w := tabwriter.NewWriter(DefaultWriter, 0, 0, 3, ' ', 0)

	if !r.NoHeader {
		for i, c := range r.Columns {
			if i > 0 {
				fmt.Fprint(w, "\t")
			}
			fmt.Fprint(w, c.Name)
		}
		fmt.Fprintln(w)
	}

	for _, row := range data {
		for i, c := range r.Columns {
			if i > 0 {
				fmt.Fprint(w, "\t")
			}
			fmt.Fprint(w, c.Func(row))
		}
		fmt.Fprintln(w)
	}
	return w.Flush()
}

func renderJSON(w io.Writer, v any) error {
	enc := json.NewEncoder(w)
	enc.SetIndent("", "  ")
	return enc.Encode(v)
}

func renderYAML(w io.Writer, v any) error {
	data, err := yaml.Marshal(v)
	if err != nil {
		return fmt.Errorf("marshal yaml: %w", err)
	}
	_, err = w.Write(data)
	return err
}
