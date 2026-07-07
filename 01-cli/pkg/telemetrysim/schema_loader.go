package telemetrysim

import (
	"context"
	"fmt"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/dto"
)

// LoadPlan queries the target tenant's devices and their telemetry schemas,
// returning a SimPlan ready for the event loop.
//
// Logic:
//  1. If cfg.DeviceCode is set → fetch only that device
//  2. If cfg.DeviceType is set → list devices of that type
//  3. Otherwise → list all devices, capped to cfg.DeviceLimit
//  4. De-duplicate by deviceType, then fetch schema per type
//  5. Parse fields[] into []FieldSchema
//  6. DeviceTypes with no/empty telemetry schema are skipped and recorded
func LoadPlan(ctx context.Context, c *client.Client, cfg SimulatorConfig) (*SimPlan, error) {
	devices, err := fetchDevices(ctx, c, cfg)
	if err != nil {
		return nil, fmt.Errorf("fetch devices: %w", err)
	}
	if len(devices) == 0 {
		return nil, fmt.Errorf("no devices found for tenant=%q deviceType=%q deviceCode=%q",
			cfg.TenantID, cfg.DeviceType, cfg.DeviceCode)
	}

	schemaByType, skipped, err := fetchSchemas(ctx, c, devices)
	if err != nil {
		return nil, fmt.Errorf("fetch schemas: %w", err)
	}

	// Filter out devices whose type has no schema
	skippedSet := make(map[string]bool, len(skipped))
	for _, t := range skipped {
		skippedSet[t] = true
	}
	var simDevices []SimDevice
	for _, d := range devices {
		if skippedSet[d.DeviceType] {
			continue
		}
		simDevices = append(simDevices, SimDevice{
			DeviceID:   d.ID,
			DeviceCode: d.DeviceCode,
			DeviceType: d.DeviceType,
			TenantID:   cfg.TenantID,
		})
	}
	if len(simDevices) == 0 {
		return nil, fmt.Errorf("all devices were skipped (no telemetry schema defined for any deviceType)")
	}

	return &SimPlan{
		Devices:      simDevices,
		SchemaByType: schemaByType,
		SkippedTypes: skipped,
	}, nil
}

// fetchDevices retrieves the devices to simulate based on config filters.
func fetchDevices(ctx context.Context, c *client.Client, cfg SimulatorConfig) ([]dto.DeviceResponse, error) {
	limit := cfg.DeviceLimit
	if limit <= 0 {
		limit = 200
	}

	filter := &client.ListDevicesFilter{
		DeviceType: cfg.DeviceType,
		Keyword:    cfg.DeviceCode,
		Size:       limit,
	}

	devices, _, err := c.ListDevices(ctx, filter)
	return devices, err
}

// fetchSchemas queries the telemetry schema for each unique deviceType present
// in the device list, parses fields[], and returns:
//   - schemaByType: deviceType → []FieldSchema
//   - skipped: deviceTypes with no usable telemetry schema
func fetchSchemas(ctx context.Context, c *client.Client, devices []dto.DeviceResponse) (DeviceSchema, []string, error) {
	// Collect unique device types
	typeSet := make(map[string]struct{})
	for _, d := range devices {
		typeSet[d.DeviceType] = struct{}{}
	}

	schemaByType := make(DeviceSchema, len(typeSet))
	var skipped []string

	for deviceType := range typeSet {
		raw, err := c.GetTelemetrySchema(ctx, deviceType)
		if err != nil {
			// If the API returns 404 or empty, skip this type
			skipped = append(skipped, deviceType)
			continue
		}

		fields, err := parseTelemetryFields(raw)
		if err != nil || len(fields) == 0 {
			skipped = append(skipped, deviceType)
			continue
		}

		schemaByType[deviceType] = fields
	}

	return schemaByType, skipped, nil
}

// parseTelemetryFields parses the raw schema map returned by GetTelemetrySchema.
//
// Expected format (from device_templates.schema.telemetry):
//
//	{
//	  "fields": [
//	    {"key":"voltage","type":"number","minimum":0,"maximum":300,"required":true},
//	    {"key":"controllerSerial","type":"text","required":true},
//	    ...
//	  ]
//	}
func parseTelemetryFields(raw map[string]any) ([]FieldSchema, error) {
	rawFields, ok := raw["fields"]
	if !ok {
		return nil, nil // no fields key → empty schema
	}

	arr, ok := rawFields.([]interface{})
	if !ok {
		return nil, fmt.Errorf("schema.fields is not an array")
	}

	var fields []FieldSchema
	for i, item := range arr {
		m, ok := item.(map[string]interface{})
		if !ok {
			return nil, fmt.Errorf("fields[%d] is not an object", i)
		}

		key := stringField(m, "key")
		if key == "" {
			continue // skip unnamed fields
		}

		fs := FieldSchema{
			Name:     key,
			Title:    stringField(m, "title"),
			Type:     stringField(m, "type"),
			Required: boolField(m, "required"),
			Minimum:  floatPtr(m, "minimum"),
			Maximum:  floatPtr(m, "maximum"),
		}

		if enumRaw, ok := m["enum"].([]interface{}); ok {
			for _, e := range enumRaw {
				fs.Enum = append(fs.Enum, fmt.Sprintf("%v", e))
			}
		}

		fields = append(fields, fs)
	}
	return fields, nil
}

// ── helpers ──────────────────────────────────────────────────────────────────

func stringField(m map[string]interface{}, key string) string {
	if v, ok := m[key]; ok {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return ""
}

func boolField(m map[string]interface{}, key string) bool {
	if v, ok := m[key]; ok {
		if b, ok := v.(bool); ok {
			return b
		}
	}
	return false
}

func floatPtr(m map[string]interface{}, key string) *float64 {
	if v, ok := m[key]; ok {
		switch n := v.(type) {
		case float64:
			return &n
		case int:
			f := float64(n)
			return &f
		case int64:
			f := float64(n)
			return &f
		}
	}
	return nil
}
