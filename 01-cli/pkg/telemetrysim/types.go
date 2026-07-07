package telemetrysim

// SimDevice is a device selected for simulation, holding only the fields
// needed to generate and send telemetry.
type SimDevice struct {
	DeviceID   int64
	DeviceCode string
	DeviceType string
	TenantID   string
}

// FieldSchema represents a single telemetry field parsed from
// device_templates.schema.telemetry.fields[].
//
// The stored format is:
//
//	{"fields": [{"key":"voltage","type":"number","minimum":0,"maximum":300,"required":true}, ...]}
//
// This is NOT standard JSON Schema – "type" uses "text" instead of "string",
// and required is a per-field boolean instead of a top-level array.
type FieldSchema struct {
	Name     string   // field.key
	Title    string   // field.title (display label, optional)
	Type     string   // "number" | "text" | "date"
	Required bool     // field.required
	Enum     []string // field.enum (optional)
	Minimum  *float64 // field.minimum (optional)
	Maximum  *float64 // field.maximum (optional)
}

// DeviceSchema maps a deviceType to its parsed telemetry field definitions.
type DeviceSchema map[string][]FieldSchema

// SimPlan is the prepared simulation plan built before the event loop starts.
// It maps each device to the field schemas that will be used to generate values.
type SimPlan struct {
	Devices      []SimDevice
	SchemaByType DeviceSchema
	// SkippedTypes lists deviceTypes that had no telemetry schema and were excluded.
	SkippedTypes []string
}

// SimSummary tracks running statistics across the simulation lifetime.
type SimSummary struct {
	Sent             int64
	Success          int64
	Failed           int64
	ValidationFailed int64
	TotalLatencyMs   int64
	SentBatches      int64
	LastError        string
}

// AvgLatencyMs returns the average request latency in milliseconds.
func (s *SimSummary) AvgLatencyMs() float64 {
	if s.SentBatches == 0 {
		return 0
	}
	return float64(s.TotalLatencyMs) / float64(s.SentBatches)
}

// Record registers the results from a single batch send.
func (s *SimSummary) Record(results []BatchResult) {
	for _, r := range results {
		s.Sent++
		if r.Success {
			s.Success++
		} else {
			s.Failed++
			if r.IsValidation {
				s.ValidationFailed++
			}
			s.LastError = r.ErrorMsg
		}
	}
}

// BatchResult is the per-device outcome from a single send attempt.
type BatchResult struct {
	DeviceCode   string
	Success      bool
	IsValidation bool
	ErrorMsg     string
}
