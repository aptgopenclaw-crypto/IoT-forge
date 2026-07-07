package dto

type DeviceResponse struct {
	ID             int64            `json:"id"`
	Name           string           `json:"name,omitempty"`
	DeviceCode     string           `json:"deviceCode"`
	DeviceName     string           `json:"deviceName,omitempty"`
	DeviceType     string           `json:"deviceType"`
	Status         DeviceStatus     `json:"status"`
	SerialNumber   string           `json:"serialNumber,omitempty"`
	TenantID       string           `json:"tenantId,omitempty"`
	DeptID         *int64           `json:"deptId,omitempty"`
	DeptName       string           `json:"deptName,omitempty"`
	ContractID     *int64           `json:"contractId,omitempty"`
	ContractCode   string           `json:"contractCode,omitempty"`
	CircuitID      *int64           `json:"circuitId,omitempty"`
	CircuitNumber  string           `json:"circuitNumber,omitempty"`
	ParentDeviceID *int64           `json:"parentDeviceId,omitempty"`
	CreatedAt      string           `json:"createdAt,omitempty"`
	UpdatedAt      string           `json:"updatedAt,omitempty"`
	Children       []DeviceResponse `json:"children,omitempty"`
}

type DeviceRequest struct {
	Name             string         `json:"name,omitempty"`
	DeviceType       string         `json:"deviceType"`
	DeviceCode       string         `json:"deviceCode"`
	DeviceName       string         `json:"deviceName,omitempty"`
	SerialNumber     string         `json:"serialNumber,omitempty"`
	DeptID           *int64         `json:"deptId,omitempty"`
	ContractID       *int64         `json:"contractId,omitempty"`
	CircuitID        *int64         `json:"circuitId,omitempty"`
	ParentDeviceID   *int64         `json:"parentDeviceId,omitempty"`
	ConnectivityType string         `json:"connectivityType,omitempty"`
	InstalledAt      string         `json:"installedAt,omitempty"`
	Properties       map[string]any `json:"properties,omitempty"`
}

type DeviceStatsResponse struct {
	TotalByType    map[string]int64 `json:"totalByType,omitempty"`
	TotalByStatus  map[string]int64 `json:"totalByStatus,omitempty"`
	ByType         map[string]int64 `json:"byType"`
	ByStatus       map[string]int64 `json:"byStatus"`
	OnlineRate     float64          `json:"onlineRate"`
	OpenDispatches int64            `json:"openDispatches,omitempty"`
	OpenWorkOrders int64            `json:"openWorkOrders"`
}
