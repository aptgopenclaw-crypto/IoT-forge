package dto

type TelemetryIngestRequest struct {
	DeviceCode   string         `json:"deviceCode,omitempty"`
	ExternalCode string         `json:"externalCode,omitempty"`
	TS           string         `json:"ts,omitempty"`
	Values       map[string]any `json:"values"`
}

type TelemetryIngestResult struct {
	Success          bool     `json:"success"`
	DeviceCode       string   `json:"deviceCode"`
	DeviceID         *int64   `json:"deviceId,omitempty"`
	ErrorCode        string   `json:"errorCode,omitempty"`
	Message          string   `json:"message,omitempty"`
	ValidationErrors []string `json:"validationErrors,omitempty"`
}
