package client

import (
	"context"

	"iot-forge-cli/pkg/dto"
)

// Login authenticates with username and password, returns a temporary token.
func (c *Client) Login(ctx context.Context, username, password string) (string, error) {
	req := dto.LoginRequest{Username: username, Password: password}
	var resp dto.LoginResponse
	if err := c.doRequest(ctx, "POST", "/v1/auth/login", req, &resp); err != nil {
		return "", err
	}
	return resp.TemporaryToken, nil
}

// ListTenants returns the tenants available for the current temporary token.
func (c *Client) ListTenants(ctx context.Context) ([]dto.TenantInfo, error) {
	var tenants []dto.TenantInfo
	if err := c.doRequest(ctx, "POST", "/v1/auth/tenants", nil, &tenants); err != nil {
		return nil, err
	}
	return tenants, nil
}

// SelectTenant exchanges a temporary token for an access/refresh token pair.
func (c *Client) SelectTenant(ctx context.Context, tempToken, tenantID string) (*dto.SelectTenantResponse, error) {
	req := dto.SelectTenantRequest{
		TemporaryToken: tempToken,
		TenantID:       tenantID,
	}
	var resp dto.SelectTenantResponse
	if err := c.doRequest(ctx, "POST", "/v1/auth/tenant/select", req, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// RefreshToken exchanges a refresh token for a new access token.
func (c *Client) RefreshToken(ctx context.Context, refreshToken string) (*dto.RefreshTokenResponse, error) {
	req := dto.RefreshTokenRequest{RefreshToken: refreshToken}
	var resp dto.RefreshTokenResponse
	if err := c.doRequest(ctx, "POST", "/v1/auth/refresh", req, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// Logout invalidates the current session on the server.
func (c *Client) Logout(ctx context.Context) error {
	return c.doRequest(ctx, "POST", "/v1/auth/logout", nil, nil)
}
