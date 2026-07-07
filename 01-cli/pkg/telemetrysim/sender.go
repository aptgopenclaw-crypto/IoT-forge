package telemetrysim

import (
	"context"
	"fmt"
	"time"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/dto"
)

// SendBatch sends one batch of telemetry payloads via the M2M ingest API and
// returns a BatchResult per device. It also accumulates latency into summary.
func SendBatch(
	ctx context.Context,
	c *client.Client,
	payloads []dto.TelemetryIngestRequest,
	cfg SimulatorConfig,
	summary *SimSummary,
) []BatchResult {
	if len(payloads) == 0 {
		return nil
	}

	start := time.Now()
	results, err := c.IngestTelemetryBatch(ctx, payloads, cfg.APIKey, cfg.APISecret)
	latency := time.Since(start).Milliseconds()

	summary.SentBatches++
	summary.TotalLatencyMs += latency

	if err != nil {
		// Entire batch failed (network / auth error)
		batchResults := make([]BatchResult, len(payloads))
		for i, p := range payloads {
			batchResults[i] = BatchResult{
				DeviceCode: p.DeviceCode,
				Success:    false,
				ErrorMsg:   err.Error(),
			}
		}
		if cfg.Verbose {
			fmt.Printf("  [batch ERROR] %d devices, latency=%dms err=%v\n",
				len(payloads), latency, err)
		}
		summary.Record(batchResults)
		return batchResults
	}

	// Map API results back to BatchResult
	batchResults := make([]BatchResult, len(results))
	for i, r := range results {
		br := BatchResult{
			DeviceCode:   r.DeviceCode,
			Success:      r.Success,
			IsValidation: !r.Success && len(r.ValidationErrors) > 0,
		}
		if !r.Success {
			br.ErrorMsg = buildErrorMsg(r)
		}
		batchResults[i] = br
	}

	if cfg.Verbose {
		successCount := 0
		for _, br := range batchResults {
			if br.Success {
				successCount++
			}
		}
		fmt.Printf("  [batch] %d/%d ok, latency=%dms\n",
			successCount, len(batchResults), latency)
		for _, br := range batchResults {
			if !br.Success {
				fmt.Printf("    ✗ %s: %s\n", br.DeviceCode, br.ErrorMsg)
			}
		}
	}

	summary.Record(batchResults)
	return batchResults
}

// BuildPayloads turns a slice of (device, values) pairs into ingest requests.
func BuildPayloads(devices []SimDevice, valuesByCode map[string]map[string]any) []dto.TelemetryIngestRequest {
	payloads := make([]dto.TelemetryIngestRequest, 0, len(devices))
	for _, d := range devices {
		vals, ok := valuesByCode[d.DeviceCode]
		if !ok || len(vals) == 0 {
			continue
		}
		payloads = append(payloads, dto.TelemetryIngestRequest{
			DeviceCode: d.DeviceCode,
			TS:         time.Now().UTC().Format(time.RFC3339),
			Values:     vals,
		})
	}
	return payloads
}

func buildErrorMsg(r dto.TelemetryIngestResult) string {
	if len(r.ValidationErrors) > 0 {
		return fmt.Sprintf("[%s] validation: %v", r.ErrorCode, r.ValidationErrors)
	}
	if r.Message != "" {
		return fmt.Sprintf("[%s] %s", r.ErrorCode, r.Message)
	}
	return r.ErrorCode
}
