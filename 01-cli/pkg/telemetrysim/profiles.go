package telemetrysim

// fieldHeuristic defines a fallback numeric range for common telemetry field
// names when the schema provides no minimum/maximum constraints.
type fieldHeuristic struct {
	Min float64
	Max float64
}

// builtinHeuristics maps lowercase field key substrings to sensible ranges.
// The longest matching key wins during lookup.
var builtinHeuristics = map[string]fieldHeuristic{
	"brightness":  {30, 100},
	"voltage":     {200, 240},
	"current":     {0.1, 5.0},
	"power":       {0.05, 1.5},
	"powerfactor": {0.85, 0.99},
	"pf":          {0.85, 0.99},
	"rssi":        {-90, -50},
	"rsrp":        {-110, -80},
	"rsrq":        {-15, -5},
	"temperature": {15, 40},
	"humidity":    {30, 80},
	"signal":      {-90, -50},
	"cell":        {0, 0}, // text field, will not be used for number
}

// heuristicFor returns the built-in range for a field name, or a generic
// 0–100 fallback if no match is found.
func heuristicFor(name string) (float64, float64) {
	lower := toLower(name)
	// Exact match first
	if h, ok := builtinHeuristics[lower]; ok {
		return h.Min, h.Max
	}
	// Substring match (longest first via linear scan – small map, fine)
	bestLen := 0
	bestMin, bestMax := 0.0, 100.0
	for k, h := range builtinHeuristics {
		if contains(lower, k) && len(k) > bestLen {
			bestLen = len(k)
			bestMin, bestMax = h.Min, h.Max
		}
	}
	return bestMin, bestMax
}

// anomalyOverrides maps ruleProfile names to per-field forced values.
// Values that are nil mean "skip this field" (simulate missing required field).
var anomalyOverrides = map[string]map[string]interface{}{
	"low-voltage": {
		"voltage": 120.0,
	},
	"low-power-factor": {
		"powerFactor": 0.55,
		"pf":          0.55,
	},
	"poor-signal": {
		"rssi": -135.0,
		"rsrp": -140.0,
		"rsrq": -25.0,
	},
	"high-brightness": {
		"brightness": 100.0,
	},
	// invalid-schema: handled separately in generator – drops required fields
}

// anomalyOverride returns the forced value for a field in the given ruleProfile.
// ok is true when the field should be overridden.
func anomalyOverride(profile, fieldName string) (interface{}, bool) {
	overrides, exists := anomalyOverrides[profile]
	if !exists {
		return nil, false
	}
	v, ok := overrides[fieldName]
	return v, ok
}

// ── tiny string helpers (avoid importing strings to keep deps minimal) ────────

func toLower(s string) string {
	b := make([]byte, len(s))
	for i := range s {
		c := s[i]
		if c >= 'A' && c <= 'Z' {
			c += 'a' - 'A'
		}
		b[i] = c
	}
	return string(b)
}

func contains(s, sub string) bool {
	if len(sub) > len(s) {
		return false
	}
	for i := 0; i <= len(s)-len(sub); i++ {
		if s[i:i+len(sub)] == sub {
			return true
		}
	}
	return false
}
