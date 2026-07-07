package telemetrysim

import "time"

// SimulatorConfig holds all runtime configuration for the telemetry simulator.
type SimulatorConfig struct {
	// Connection
	BaseURL   string
	APIKey    string
	APISecret string

	// Target scope
	TenantID    string
	DeviceType  string // empty = all types
	DeviceCode  string // empty = all devices
	DeviceLimit int    // max devices to simulate; 0 = no limit

	// Timing
	Interval time.Duration // send interval (default: 5s)
	Duration time.Duration // total run duration; 0 = run until interrupted

	// Sending
	BatchSize int // how many devices per batch request (default: 20)

	// Behaviour
	Mode        string // "normal" | "anomaly"
	RuleProfile string // anomaly profile: "low-voltage" | "low-power-factor" | "poor-signal" | "invalid-schema"
	Seed        int64  // random seed; 0 = use time-based seed
	DryRun      bool   // generate payloads but do not send
	Verbose     bool   // print per-request details
}

// DefaultConfig returns a SimulatorConfig with sensible defaults.
func DefaultConfig() SimulatorConfig {
	return SimulatorConfig{
		BaseURL:     "http://localhost:8080",
		DeviceLimit: 50,
		Interval:    5 * time.Second,
		Duration:    0,
		BatchSize:   20,
		Mode:        "normal",
	}
}
