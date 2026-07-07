package telemetrysim

import (
	"fmt"
	"math/rand"
	"time"
)

const optionalGenerateProbability = 0.70

// GenerateValues produces a telemetry values map for a single device tick.
//
//   - mode "normal"  → all required fields + optional fields at 70% probability
//   - mode "anomaly" → same as normal, but anomalyOverrides overwrite specific
//     fields; ruleProfile "invalid-schema" drops all required fields instead
func GenerateValues(fields []FieldSchema, mode, ruleProfile string, rnd *rand.Rand) map[string]any {
	values := make(map[string]any, len(fields))

	invalidSchema := mode == "anomaly" && ruleProfile == "invalid-schema"

	for _, f := range fields {
		// Decide whether to include this field
		if invalidSchema && f.Required {
			// Intentionally omit all required fields to trigger schema validation failure
			continue
		}
		if !f.Required && rnd.Float64() > optionalGenerateProbability {
			continue
		}

		// Anomaly override (non-invalid-schema profiles)
		if mode == "anomaly" && ruleProfile != "invalid-schema" {
			if forced, ok := anomalyOverride(ruleProfile, f.Name); ok {
				values[f.Name] = forced
				continue
			}
		}

		values[f.Name] = generateField(f, mode, rnd)
	}
	return values
}

// generateField produces a single value for a field based on its schema.
func generateField(f FieldSchema, mode string, rnd *rand.Rand) any {
	switch f.Type {
	case "number", "integer", "float":
		return generateNumber(f, rnd)
	case "text", "string":
		return generateText(f, rnd)
	case "date", "datetime", "timestamp":
		return time.Now().UTC().Format(time.RFC3339)
	case "boolean", "bool":
		return rnd.Intn(2) == 1
	default:
		// Unknown type: return a generic string
		return fmt.Sprintf("SIM-%s", f.Name)
	}
}

// generateNumber produces a float64 in the field's [min, max] range.
// Precedence: schema min/max → heuristic → generic 0–100.
func generateNumber(f FieldSchema, rnd *rand.Rand) float64 {
	min, max := resolveRange(f)

	if min == max {
		return round2(min)
	}
	if min > max {
		min, max = max, min
	}
	v := min + rnd.Float64()*(max-min)

	if f.Type == "integer" {
		return float64(int64(v))
	}
	return round2(v)
}

// resolveRange returns the effective [min, max] for a numeric field.
func resolveRange(f FieldSchema) (float64, float64) {
	hasMin := f.Minimum != nil
	hasMax := f.Maximum != nil

	if hasMin && hasMax {
		return *f.Minimum, *f.Maximum
	}
	if hasMin && !hasMax {
		// Only minimum: generate a value in [min, min*3] or [min, min+100]
		lo := *f.Minimum
		hi := lo*3 + 100 // safe for both 0 and negative minimums
		return lo, hi
	}
	if !hasMin && hasMax {
		hi := *f.Maximum
		lo := hi - 100
		return lo, hi
	}
	// Neither: look up heuristic
	return heuristicFor(f.Name)
}

// generateText produces a string value.
//   - With enum: random pick from the list
//   - Without enum: "SIM-{fieldName}-{4-char hex}"
func generateText(f FieldSchema, rnd *rand.Rand) string {
	if len(f.Enum) > 0 {
		return f.Enum[rnd.Intn(len(f.Enum))]
	}
	return fmt.Sprintf("SIM-%s-%04x", f.Name, rnd.Intn(0x10000))
}

// round2 rounds a float64 to 2 decimal places.
func round2(v float64) float64 {
	return float64(int64(v*100+0.5)) / 100
}
