package dto

type TelemetryQueryRequest struct {
	DeviceID string `json:"-"` // path parameter
	From     string `json:"from"`
	To       string `json:"to,omitempty"`
	Metric   string `json:"metric,omitempty"`
	Interval string `json:"interval,omitempty"`
}

type TelemetryReading struct {
	Timestamp string  `json:"timestamp"`
	Metric    string  `json:"metric"`
	Value     float64 `json:"value"`
	Unit      string  `json:"unit,omitempty"`
}

type TelemetryQueryResponse struct {
	DeviceID string             `json:"deviceId"`
	Readings []TelemetryReading `json:"readings"`
}
