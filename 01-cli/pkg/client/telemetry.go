package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"

	"iot-forge-cli/pkg/dto"
)

// QueryTelemetryFilter represents filter parameters for telemetry queries.
type QueryTelemetryFilter struct {
	From     string
	To       string
	Metric   string
	Interval string
}

// QueryTelemetry returns telemetry readings for a device.
func (c *Client) QueryTelemetry(ctx context.Context, deviceID int64, filter *QueryTelemetryFilter) (*dto.TelemetryQueryResponse, error) {
	params := url.Values{}
	if filter != nil {
		if filter.From != "" {
			params.Set("from", filter.From)
		}
		if filter.To != "" {
			params.Set("to", filter.To)
		}
		if filter.Metric != "" {
			params.Set("metric", filter.Metric)
		}
		if filter.Interval != "" {
			params.Set("interval", filter.Interval)
		}
	}

	path := fmt.Sprintf("/v1/auth/telemetry/%d?%s", deviceID, params.Encode())
	var resp dto.TelemetryQueryResponse
	if err := c.doRequest(ctx, "GET", path, nil, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// GetTelemetrySchema returns the telemetry schema section for a device type.
func (c *Client) GetTelemetrySchema(ctx context.Context, deviceType string) (map[string]any, error) {
	path := fmt.Sprintf("/v1/auth/device-templates/%s/schema/telemetry", url.PathEscape(deviceType))
	var schema map[string]any
	if err := c.doRequest(ctx, "GET", path, nil, &schema); err != nil {
		return nil, err
	}
	return schema, nil
}

// IngestTelemetryBatch sends a telemetry batch through the M2M ingest API using API key/secret auth.
func (c *Client) IngestTelemetryBatch(ctx context.Context, req []dto.TelemetryIngestRequest, apiKey, apiSecret string) ([]dto.TelemetryIngestResult, error) {
	data, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("marshal ingest batch: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, c.endpoint+"/v1/ingest/telemetry/batch", bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("create ingest batch request: %w", err)
	}
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")
	httpReq.Header.Set("X-API-Key", apiKey)
	httpReq.Header.Set("X-API-Secret", apiSecret)

	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("http ingest batch request: %w", err)
	}
	defer resp.Body.Close()

	respData, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read ingest batch response: %w", err)
	}

	if resp.StatusCode >= 400 {
		var errResp dto.BaseResponse
		if json.Unmarshal(respData, &errResp) == nil && errResp.ErrorCode != "" {
			return nil, &APIError{StatusCode: resp.StatusCode, ErrorCode: errResp.ErrorCode, Message: errResp.ErrorMsg}
		}
		return nil, fmt.Errorf("http %d: %s", resp.StatusCode, string(respData))
	}

	var wrapped dto.BaseResponse
	if err := json.Unmarshal(respData, &wrapped); err != nil {
		return nil, fmt.Errorf("parse ingest batch response: %w", err)
	}
	if wrapped.ErrorCode != "" && wrapped.ErrorCode != "00000" {
		return nil, &APIError{StatusCode: resp.StatusCode, ErrorCode: wrapped.ErrorCode, Message: wrapped.ErrorMsg}
	}

	var results []dto.TelemetryIngestResult
	if len(wrapped.Body) > 0 {
		if err := json.Unmarshal(wrapped.Body, &results); err != nil {
			return nil, fmt.Errorf("parse ingest batch response body: %w", err)
		}
	}
	return results, nil
}
