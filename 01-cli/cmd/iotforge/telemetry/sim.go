package telemetry

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/cliutil"
	"iot-forge-cli/pkg/telemetrysim"

	"github.com/spf13/cobra"
)

func newSimCmd() *cobra.Command {
	var (
		apiKey      string
		apiSecret   string
		tenantID    string
		deviceType  string
		deviceCode  string
		deviceLimit int
		intervalMs  int
		durationSec int
		batchSize   int
		mode        string
		ruleProfile string
		seed        int64
		dryRun      bool
		verbose     bool
		// Inline mode (bypasses JWT auth)
		schemaJSON  string
		deviceCodes string
	)

	cmd := &cobra.Command{
		Use:   "sim",
		Short: "Simulate telemetry data and send it to the IoT-forge ingest API",
		Long: `sim continuously generates random telemetry values based on each device's
schema and sends them to the M2M HTTP ingest endpoint.

Examples:
  # Normal mode — send valid data every 5 seconds for 2 minutes
  iotforge telemetry sim \
    --api-key KEY --api-secret SECRET --tenant-id T_xxxx \
    --interval-ms 5000 --duration-sec 120

  # Anomaly mode — trigger low-voltage rules
  iotforge telemetry sim \
    --api-key KEY --api-secret SECRET --tenant-id T_xxxx \
    --mode anomaly --rule-profile low-voltage --verbose

  # Dry run — preview payloads without sending
  iotforge telemetry sim \
    --api-key KEY --api-secret SECRET --tenant-id T_xxxx \
    --dry-run --verbose

  # Inline mode — provide schema and devices directly, no JWT login needed
  iotforge telemetry sim \
    --api-key KEY --api-secret SECRET \
    --schema-json '{"fields":[{"key":"voltage","type":"number","minimum":0,"maximum":300,"required":true}]}' \
    --device-codes "SL-001,SL-002" \
    --interval-ms 5000 --duration-sec 120 --verbose`,
		RunE: func(cmd *cobra.Command, args []string) error {
			// ── Validation ──────────────────────────────────────────────────
			if apiKey == "" || apiSecret == "" {
				return fmt.Errorf("--api-key and --api-secret are required")
			}

			validModes := map[string]bool{"normal": true, "anomaly": true}
			if !validModes[mode] {
				return fmt.Errorf("invalid --mode %q: must be 'normal' or 'anomaly'", mode)
			}

			// ── Build config ─────────────────────────────────────────────────
			cfg := telemetrysim.DefaultConfig()
			cfg.APIKey = apiKey
			cfg.APISecret = apiSecret
			cfg.TenantID = tenantID
			cfg.DeviceType = deviceType
			cfg.DeviceCode = deviceCode
			if deviceLimit > 0 {
				cfg.DeviceLimit = deviceLimit
			}
			cfg.Interval = time.Duration(intervalMs) * time.Millisecond
			if durationSec > 0 {
				cfg.Duration = time.Duration(durationSec) * time.Second
			}
			if batchSize > 0 {
				cfg.BatchSize = batchSize
			}
			cfg.Mode = mode
			cfg.RuleProfile = ruleProfile
			cfg.Seed = seed
			cfg.DryRun = dryRun
			cfg.Verbose = verbose

			// ── Signal handling (Ctrl-C) ──────────────────────────────────────
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()

			sigs := make(chan os.Signal, 1)
			signal.Notify(sigs, syscall.SIGINT, syscall.SIGTERM)
			go func() {
				<-sigs
				fmt.Println("\nInterrupt received, stopping…")
				cancel()
			}()

			// ── Inline mode (--schema-json + --device-codes) ──────────────────
			if schemaJSON != "" {
				if deviceCodes == "" {
					return fmt.Errorf("--device-codes is required when using --schema-json")
				}
				return runInline(ctx, cfg, schemaJSON, deviceCodes)
			}

			// ── Normal mode (JWT auth for device/schema APIs) ───────────────
			if tenantID == "" {
				return fmt.Errorf("--tenant-id is required when not using --schema-json")
			}
			return runWithJWT(ctx, cfg)
		},
	}

	// Required
	cmd.Flags().StringVar(&apiKey, "api-key", "", "Ingest API key (X-API-Key)")
	cmd.Flags().StringVar(&apiSecret, "api-secret", "", "Ingest API secret (X-API-Secret)")
	cmd.Flags().StringVar(&tenantID, "tenant-id", "", "Target tenant ID (required for JWT mode)")

	// Scope filters
	cmd.Flags().StringVar(&deviceType, "device-type", "", "Simulate only devices of this type")
	cmd.Flags().StringVar(&deviceCode, "device-code", "", "Simulate only this device")
	cmd.Flags().IntVar(&deviceLimit, "device-limit", 0, "Max number of devices to simulate (default: 50)")

	// Timing
	cmd.Flags().IntVar(&intervalMs, "interval-ms", 5000, "Send interval in milliseconds")
	cmd.Flags().IntVar(&durationSec, "duration-sec", 0, "Total run duration in seconds (0 = run until Ctrl-C)")

	// Batch
	cmd.Flags().IntVar(&batchSize, "batch-size", 0, "Devices per batch request (default: 20)")

	// Behaviour
	cmd.Flags().StringVar(&mode, "mode", "normal", "Simulation mode: normal | anomaly")
	cmd.Flags().StringVar(&ruleProfile, "rule-profile", "", "Anomaly profile: low-voltage | low-power-factor | poor-signal | high-brightness | invalid-schema")
	cmd.Flags().Int64Var(&seed, "seed", 0, "Random seed (0 = time-based)")
	cmd.Flags().BoolVar(&dryRun, "dry-run", false, "Generate payloads but do not send")
	cmd.Flags().BoolVar(&verbose, "verbose", false, "Print per-request details")

	// Inline mode (bypasses JWT auth)
	cmd.Flags().StringVar(&schemaJSON, "schema-json", "", "Telemetry schema JSON (bypasses JWT auth)")
	cmd.Flags().StringVar(&deviceCodes, "device-codes", "", "Comma-separated device codes (requires --schema-json)")

	return cmd
}

// runInline starts the simulator using inline schema + device codes, no JWT needed.
func runInline(ctx context.Context, cfg telemetrysim.SimulatorConfig, schemaJSON, deviceCodes string) error {
	fields, err := telemetrysim.ParseSchemaJSON(schemaJSON)
	if err != nil {
		return fmt.Errorf("parse --schema-json: %w", err)
	}
	if len(fields) == 0 {
		return fmt.Errorf("no fields found in schema")
	}

	codes := telemetrysim.ParseDeviceCodes(deviceCodes)
	if len(codes) == 0 {
		return fmt.Errorf("no device codes found in --device-codes")
	}

	deviceType := cfg.DeviceType
	if deviceType == "" {
		deviceType = "inline"
	}

	plan := telemetrysim.BuildInlinePlan(codes, deviceType, fields)
	fmt.Printf("Inline mode: %d devices, %d fields\n", len(codes), len(fields))
	for _, d := range plan.Devices {
		fmt.Printf("  %s\n", d.DeviceCode)
	}
	fmt.Println()

	// Build a client for ingest calls (no JWT needed — only uses API key)
	c := client.New(cfg.BaseURL)

	return telemetrysim.RunWithPlan(ctx, c, cfg, plan)
}

// runWithJWT starts the simulator using JWT auth to load device/schema APIs.
func runWithJWT(ctx context.Context, cfg telemetrysim.SimulatorConfig) error {
	cliCfg, err := cliutil.ResolveConfig()
	if err != nil {
		return fmt.Errorf("config error: %w", err)
	}
	if cliCfg.Endpoint != "" {
		cfg.BaseURL = cliCfg.Endpoint
	}
	c := cliutil.BuildClient(cliCfg)

	return telemetrysim.Run(ctx, c, cfg)
}
