package client

import (
	"context"
	"fmt"
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
