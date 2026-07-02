# iotforge CLI

CLI tool for managing IoT-forge platform resources from the terminal.

Supports interactive human login and headless API token mode for AI agents.

## Install

```bash
# Download the binary for your platform from releases
# Or build from source:
go build -o iotforge ./cmd/iotforge/
```

## Quick Start

```bash
# Login interactively
iotforge login

# Check status
iotforge auth status

# List devices
iotforge device list

# Query telemetry
iotforge telemetry query 101 --from 2026-01-01T00:00:00Z --metric temperature

# List dispatch work orders
iotforge dispatch list --status PENDING
```

## AI Agent / Headless Mode

```bash
export IOTFORGE_ENDPOINT=https://iot.example.com
export IOTFORGE_API_TOKEN=your-api-token
export IOTFORGE_TENANT_ID=T001

iotforge device list --output json
```

## Output Formats

All commands support `--output table` (default), `--output json`, and `--output yaml`.

## Global Flags

| Flag | Env | Description |
|---|---|---|
| `--endpoint` | `IOTFORGE_ENDPOINT` | API server URL |
| `--output` | `IOTFORGE_OUTPUT` | Output format (table/json/yaml) |
| `--api-token` | `IOTFORGE_API_TOKEN` | API token for headless mode |
