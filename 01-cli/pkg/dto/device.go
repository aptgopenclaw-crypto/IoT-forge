package dto

type DeviceResponse struct {
	ID           int64           `json:"id"`
	Name         string          `json:"name"`
	DeviceType   string          `json:"deviceType"`
	Status       DeviceStatus    `json:"status"`
	SerialNumber string          `json:"serialNumber,omitempty"`
	TenantID     string          `json:"tenantId"`
	ParentID     *int64          `json:"parentId,omitempty"`
	CreatedAt    string          `json:"createdAt"`
	UpdatedAt    string          `json:"updatedAt,omitempty"`
	Children     []DeviceResponse `json:"children,omitempty"`
}

type DeviceRequest struct {
	Name         string         `json:"name"`
	DeviceType   string         `json:"deviceType"`
	SerialNumber string         `json:"serialNumber,omitempty"`
	Status       DeviceStatus   `json:"status,omitempty"`
	ParentID     *int64         `json:"parentId,omitempty"`
	Properties   map[string]any `json:"properties,omitempty"`
}

type DeviceStatsResponse struct {
	TotalByType    map[string]int64 `json:"totalByType"`
	TotalByStatus  map[string]int64 `json:"totalByStatus"`
	OnlineRate     float64          `json:"onlineRate"`
	OpenDispatches int64            `json:"openDispatches"`
}
