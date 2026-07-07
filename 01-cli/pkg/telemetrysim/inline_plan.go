package telemetrysim

import (
	"encoding/json"
	"fmt"
	"strings"
)

// BuildInlinePlan creates a SimPlan from directly-provided schema JSON and device codes,
// bypassing the need for JWT-authenticated device/schema API calls.
//
// deviceCodes is a comma-separated list of device codes, all sharing the same device type.
// schemaFields is the parsed fields[] array from the device_templates telemetry schema.
func BuildInlinePlan(deviceCodes []string, deviceType string, schemaFields []FieldSchema) *SimPlan {
	devices := make([]SimDevice, len(deviceCodes))
	for i, code := range deviceCodes {
		devices[i] = SimDevice{
			DeviceID:   int64(i + 1),
			DeviceCode: strings.TrimSpace(code),
			DeviceType: deviceType,
		}
	}

	schemaByType := make(DeviceSchema)
	schemaByType[deviceType] = schemaFields

	return &SimPlan{
		Devices:      devices,
		SchemaByType: schemaByType,
		SkippedTypes: nil,
	}
}

// ParseDeviceCodes splits a comma-separated string into a slice of device codes.
func ParseDeviceCodes(raw string) []string {
	parts := strings.Split(raw, ",")
	codes := make([]string, 0, len(parts))
	for _, p := range parts {
		c := strings.TrimSpace(p)
		if c != "" {
			codes = append(codes, c)
		}
	}
	return codes
}

// ParseSchemaJSON parses a raw JSON string (the { "fields": [...] } format)
// into a []FieldSchema.
func ParseSchemaJSON(rawJSON string) ([]FieldSchema, error) {
	var raw map[string]any
	if err := json.Unmarshal([]byte(rawJSON), &raw); err != nil {
		return nil, fmt.Errorf("parse schema JSON: %w", err)
	}
	return parseTelemetryFields(raw)
}
