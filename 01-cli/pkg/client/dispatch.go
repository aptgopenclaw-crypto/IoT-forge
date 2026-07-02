package client

import (
	"context"
	"fmt"
	"net/url"
	"strconv"

	"iot-forge-cli/pkg/dto"
)

// ListDispatchFilter represents filter parameters for listing dispatch orders.
type ListDispatchFilter struct {
	Status   dto.DispatchStatus
	Priority dto.DispatchPriority
	Keyword  string
	Page     int
	Size     int
}

// ListDispatches returns a paginated list of dispatch orders.
func (c *Client) ListDispatches(ctx context.Context, filter *ListDispatchFilter) ([]dto.DispatchResponse, int64, error) {
	params := url.Values{}
	if filter != nil {
		if filter.Status != "" {
			params.Set("status", string(filter.Status))
		}
		if filter.Priority != "" {
			params.Set("priority", string(filter.Priority))
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

	path := "/v1/auth/dispatch?" + params.Encode()
	var pageResp dto.PageResponse
	if err := c.doRequest(ctx, "GET", path, nil, &pageResp); err != nil {
		return nil, 0, err
	}

	var dispatches []dto.DispatchResponse
	if err := unmarshalRaw(pageResp.Content, &dispatches); err != nil {
		return nil, 0, fmt.Errorf("parse dispatches: %w", err)
	}
	return dispatches, pageResp.TotalElements, nil
}

// GetDispatch returns a single dispatch order by ID.
func (c *Client) GetDispatch(ctx context.Context, id int64) (*dto.DispatchResponse, error) {
	var dispatch dto.DispatchResponse
	if err := c.doRequest(ctx, "GET", fmt.Sprintf("/v1/auth/dispatch/%d", id), nil, &dispatch); err != nil {
		return nil, err
	}
	return &dispatch, nil
}

// UpdateDispatch updates an existing dispatch order.
func (c *Client) UpdateDispatch(ctx context.Context, id int64, req *dto.DispatchRequest) (*dto.DispatchResponse, error) {
	var dispatch dto.DispatchResponse
	if err := c.doRequest(ctx, "PUT", fmt.Sprintf("/v1/auth/dispatch/%d", id), req, &dispatch); err != nil {
		return nil, err
	}
	return &dispatch, nil
}
