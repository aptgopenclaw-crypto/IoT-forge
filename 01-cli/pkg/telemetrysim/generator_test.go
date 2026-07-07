package telemetrysim_test

import (
	"math/rand"
	"testing"

	"iot-forge-cli/pkg/telemetrysim"
)

func ptr(f float64) *float64 { return &f }

// ── number generation ─────────────────────────────────────────────────────────

func TestGenerateValues_Number_WithMinMax(t *testing.T) {
	fields := []telemetrysim.FieldSchema{
		{Name: "voltage", Type: "number", Required: true, Minimum: ptr(0), Maximum: ptr(300)},
	}
	rnd := rand.New(rand.NewSource(42))
	values := telemetrysim.GenerateValues(fields, "normal", "", rnd)

	v, ok := values["voltage"].(float64)
	if !ok {
		t.Fatalf("voltage not float64: %T", values["voltage"])
	}
	if v < 0 || v > 300 {
		t.Errorf("voltage %v out of [0, 300]", v)
	}
}

func TestGenerateValues_Number_OnlyMin(t *testing.T) {
	fields := []telemetrysim.FieldSchema{
		{Name: "current", Type: "number", Required: true, Minimum: ptr(0)},
	}
	rnd := rand.New(rand.NewSource(1))
	values := telemetrysim.GenerateValues(fields, "normal", "", rnd)

	v, ok := values["current"].(float64)
	if !ok {
		t.Fatalf("current not float64")
	}
	if v < 0 {
		t.Errorf("current %v below minimum 0", v)
	}
}

// ── enum / text generation ────────────────────────────────────────────────────

func TestGenerateValues_TextEnum(t *testing.T) {
	allowed := []string{"on", "off", "fault"}
	fields := []telemetrysim.FieldSchema{
		{Name: "switch", Type: "text", Required: true, Enum: allowed},
	}
	rnd := rand.New(rand.NewSource(7))
	for i := 0; i < 30; i++ {
		values := telemetrysim.GenerateValues(fields, "normal", "", rnd)
		s, ok := values["switch"].(string)
		if !ok {
			t.Fatalf("switch not string: %T", values["switch"])
		}
		found := false
		for _, a := range allowed {
			if s == a {
				found = true
				break
			}
		}
		if !found {
			t.Errorf("switch value %q not in enum %v", s, allowed)
		}
	}
}

// ── optional field generation rate ───────────────────────────────────────────

func TestGenerateValues_OptionalFieldRate(t *testing.T) {
	fields := []telemetrysim.FieldSchema{
		{Name: "rssi", Type: "number", Required: false, Minimum: ptr(-150), Maximum: ptr(0)},
	}
	rnd := rand.New(rand.NewSource(99))
	count := 0
	total := 200
	for i := 0; i < total; i++ {
		values := telemetrysim.GenerateValues(fields, "normal", "", rnd)
		if _, ok := values["rssi"]; ok {
			count++
		}
	}
	rate := float64(count) / float64(total)
	// Expect roughly 70% ± 15%
	if rate < 0.55 || rate > 0.85 {
		t.Errorf("optional field rate %0.2f outside expected 55%%–85%%", rate)
	}
}

// ── anomaly profile ───────────────────────────────────────────────────────────

func TestGenerateValues_Anomaly_LowVoltage(t *testing.T) {
	fields := []telemetrysim.FieldSchema{
		{Name: "voltage", Type: "number", Required: true, Minimum: ptr(0), Maximum: ptr(300)},
		{Name: "current", Type: "number", Required: true, Minimum: ptr(0)},
	}
	rnd := rand.New(rand.NewSource(3))
	values := telemetrysim.GenerateValues(fields, "anomaly", "low-voltage", rnd)

	v, ok := values["voltage"].(float64)
	if !ok {
		t.Fatalf("voltage not float64")
	}
	if v >= 180 {
		t.Errorf("low-voltage profile: expected voltage < 180, got %v", v)
	}
}

func TestGenerateValues_Anomaly_InvalidSchema_DropsRequired(t *testing.T) {
	fields := []telemetrysim.FieldSchema{
		{Name: "voltage", Type: "number", Required: true, Minimum: ptr(0), Maximum: ptr(300)},
		{Name: "rssi", Type: "number", Required: false, Minimum: ptr(-150), Maximum: ptr(0)},
	}
	rnd := rand.New(rand.NewSource(5))
	values := telemetrysim.GenerateValues(fields, "anomaly", "invalid-schema", rnd)

	if _, ok := values["voltage"]; ok {
		t.Error("invalid-schema: required field 'voltage' should be dropped")
	}
}
