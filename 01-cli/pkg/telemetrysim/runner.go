package telemetrysim

import (
	"context"
	"fmt"
	"math/rand"
	"time"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/dto"
)

// Run executes the full simulation loop:
//  1. Initialise random source
//  2. Load SimPlan (devices + schemas)
//  3. Tick at cfg.Interval, sending one batch per tick
//  4. Stop when cfg.Duration elapses or ctx is cancelled
//  5. Print final summary
func Run(ctx context.Context, c *client.Client, cfg SimulatorConfig) error {
	// ── 1. Random source ──────────────────────────────────────────────────────
	seed := cfg.Seed
	if seed == 0 {
		seed = time.Now().UnixNano()
	}
	rnd := rand.New(rand.NewSource(seed))

	// ── 2. Load plan ──────────────────────────────────────────────────────────
	fmt.Printf("Loading devices and schemas (tenant=%s)…\n", cfg.TenantID)
	plan, err := LoadPlan(ctx, c, cfg)
	if err != nil {
		return fmt.Errorf("load plan: %w", err)
	}
	printPlanSummary(plan, cfg)

	// ── 3. Context with optional duration ────────────────────────────────────
	runCtx := ctx
	if cfg.Duration > 0 {
		var cancel context.CancelFunc
		runCtx, cancel = context.WithTimeout(ctx, cfg.Duration)
		defer cancel()
	}

	// ── 4. Event loop ─────────────────────────────────────────────────────────
	ticker := time.NewTicker(cfg.Interval)
	defer ticker.Stop()

	summary := &SimSummary{}
	startTime := time.Now()

	fmt.Printf("Simulation started. mode=%s interval=%s batchSize=%d\n",
		cfg.Mode, cfg.Interval, cfg.BatchSize)
	if cfg.DryRun {
		fmt.Println("  *** DRY RUN – payloads will not be sent ***")
	}
	fmt.Println()

	for {
		select {
		case <-runCtx.Done():
			printFinalSummary(summary, time.Since(startTime))
			return nil

		case <-ticker.C:
			tick(runCtx, c, plan, cfg, rnd, summary)
		}
	}
}

// tick performs one simulation cycle: pick devices → generate → send.
func tick(
	ctx context.Context,
	c *client.Client,
	plan *SimPlan,
	cfg SimulatorConfig,
	rnd *rand.Rand,
	summary *SimSummary,
) {
	// Pick up to batchSize devices (round-robin across all devices)
	batch := pickDevices(plan.Devices, cfg.BatchSize, rnd)

	// Generate values for each device
	valuesByCode := make(map[string]map[string]any, len(batch))
	for _, dev := range batch {
		fields := plan.SchemaByType[dev.DeviceType]
		values := GenerateValues(fields, cfg.Mode, cfg.RuleProfile, rnd)
		valuesByCode[dev.DeviceCode] = values
	}

	// Build payloads
	payloads := BuildPayloads(batch, valuesByCode)

	if cfg.DryRun {
		printDryRunPayloads(payloads)
		// Still update sent count so summary is meaningful
		for range payloads {
			summary.Sent++
			summary.Success++
		}
		return
	}

	SendBatch(ctx, c, payloads, cfg, summary)

	if !cfg.Verbose {
		printInlineProgress(summary)
	}
}

// pickDevices selects up to n devices from the plan.
// Uses a random shuffle to avoid always favouring the first devices.
func pickDevices(devices []SimDevice, n int, rnd *rand.Rand) []SimDevice {
	if n <= 0 || n >= len(devices) {
		return devices
	}
	// Partial Fisher-Yates to pick n elements
	pool := make([]SimDevice, len(devices))
	copy(pool, devices)
	for i := 0; i < n; i++ {
		j := i + rnd.Intn(len(pool)-i)
		pool[i], pool[j] = pool[j], pool[i]
	}
	return pool[:n]
}

// ── display helpers ───────────────────────────────────────────────────────────

func printPlanSummary(plan *SimPlan, cfg SimulatorConfig) {
	fmt.Printf("  Devices:  %d\n", len(plan.Devices))

	typeCount := make(map[string]int)
	for _, d := range plan.Devices {
		typeCount[d.DeviceType]++
	}
	for t, n := range typeCount {
		fmt.Printf("    %-30s %d devices\n", t, n)
	}

	if len(plan.SkippedTypes) > 0 {
		fmt.Printf("  Skipped (no schema): %v\n", plan.SkippedTypes)
	}
	fmt.Println()
}

func printInlineProgress(s *SimSummary) {
	fmt.Printf("\r  sent=%-6d ok=%-6d fail=%-6d avgLatency=%.0fms",
		s.Sent, s.Success, s.Failed, s.AvgLatencyMs())
}

func printFinalSummary(s *SimSummary, elapsed time.Duration) {
	fmt.Printf("\n\n─── Simulation complete ────────────────────────────────────\n")
	fmt.Printf("  Elapsed:          %s\n", elapsed.Round(time.Second))
	fmt.Printf("  Batches sent:     %d\n", s.SentBatches)
	fmt.Printf("  Records sent:     %d\n", s.Sent)
	fmt.Printf("  Success:          %d\n", s.Success)
	fmt.Printf("  Failed:           %d\n", s.Failed)
	if s.ValidationFailed > 0 {
		fmt.Printf("  Validation fail:  %d\n", s.ValidationFailed)
	}
	fmt.Printf("  Avg latency:      %.0f ms\n", s.AvgLatencyMs())
	if s.LastError != "" {
		fmt.Printf("  Last error:       %s\n", s.LastError)
	}
	fmt.Println("────────────────────────────────────────────────────────────")
}

func printDryRunPayloads(payloads []dto.TelemetryIngestRequest) {
	for _, p := range payloads {
		fmt.Printf("  [dry-run] device=%s ts=%s values=%v\n", p.DeviceCode, p.TS, p.Values)
	}
}
