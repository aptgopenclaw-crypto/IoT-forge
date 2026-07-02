package client

import (
	"context"
	"fmt"
	"net/url"
	"strconv"

	"iot-forge-cli/pkg/dto"
)

// ListDevicesFilter represents filter parameters for listing devices.
type ListDevicesFilter struct {
	DeviceType string
	Status     dto.DeviceStatus
	Keyword    string
	Page       int
	Size       int
}

// ListDevices returns a paginated list of devices matching the filter.
func (c *Client) ListDevices(ctx context.Context, filter *ListDevicesFilter) ([]dto.DeviceResponse, int64, error) {
	params := url.Values{}
	if filter != nil {
		if filter.DeviceType != "" {
			params.Set("deviceType", filter.DeviceType)
		}
		if filter.Status != "" {
			params.Set("status", string(filter.Status))
		}
		if filter.Keyword != "" {
			params.Set("keyword", filter.Keyword)
		}
		params.Set("page", strconv.Itoa(filter.Page))
		if filter.Size > 0 {
			params.Set("size", strconv.Itoa(filter.Size))
		}
	}
	if params.Get("size") == "" {
		params.Set("size", "20")
	}

	path := "/v1/auth/devices?" + params.Encode()
	var pageResp dto.PageResponse
	if err := c.doRequest(ctx, "GET", path, nil, &pageResp); err != nil {
		return nil, 0, err
	}

	var devices []dto.DeviceResponse
	if err := unmarshalRaw(pageResp.Content, &devices); err != nil {
		return nil, 0, fmt.Errorf("parse devices: %w", err)
	}
	return devices, pageResp.TotalElements, nil
}

// GetDevice returns a single device by ID. If tree is true, includes the device composition tree.
func (c *Client) GetDevice(ctx context.Context, id int64, tree bool) (*dto.DeviceResponse, error) {
	path := fmt.Sprintf("/v1/auth/devices/%d", id)
	if tree {
		path = fmt.Sprintf("/v1/auth/devices/tree/%d", id)
	}
	var device dto.DeviceResponse
	if err := c.doRequest(ctx, "GET", path, nil, &device); err != nil {
		return nil, err
	}
	return &device, nil
}

// CreateDevice creates a new device.
func (c *Client) CreateDevice(ctx context.Context, req *dto.DeviceRequest) (*dto.DeviceResponse, error) {
	var device dto.DeviceResponse
	if err := c.doRequest(ctx, "POST", "/v1/auth/devices", req, &device); err != nil {
		return nil, err
	}
	return &device, nil
}

// UpdateDevice updates an existing device.
func (c *Client) UpdateDevice(ctx context.Context, id int64, req *dto.DeviceRequest) (*dto.DeviceResponse, error) {
	var device dto.DeviceResponse
	if err := c.doRequest(ctx, "PUT", fmt.Sprintf("/v1/auth/devices/%d", id), req, &device); err != nil {
		return nil, err
	}
	return &device, nil
}

// DeleteDevice deletes a device. Fails if the device has children.
func (c *Client) DeleteDevice(ctx context.Context, id int64) error {
	return c.doRequest(ctx, "DELETE", fmt.Sprintf("/v1/auth/devices/%d", id), nil, nil)
}

// DecommissionDevice marks a device as decommissioned.
func (c *Client) DecommissionDevice(ctx context.Context, id int64) error {
	return c.doRequest(ctx, "POST", fmt.Sprintf("/v1/auth/devices/%d/decommission", id), nil, nil)
}

// GetDeviceStats returns device statistics summary.
func (c *Client) GetDeviceStats(ctx context.Context) (*dto.DeviceStatsResponse, error) {
	var stats dto.DeviceStatsResponse
	if err := c.doRequest(ctx, "GET", "/v1/auth/devices/stats", nil, &stats); err != nil {
		return nil, err
	}
	return &stats, nil
}
