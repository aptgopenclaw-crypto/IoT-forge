package dto

import "encoding/json"

// BaseResponse wraps all IoT-forge API responses.
type BaseResponse struct {
	ErrorCode string          `json:"errorCode"`
	ErrorMsg  string          `json:"errorMsg"`
	Timestamp int64           `json:"timestamp"`
	Body      json.RawMessage `json:"body"`
}

// PageResponse wraps paginated list results.
type PageResponse struct {
	Content       json.RawMessage `json:"content"`
	TotalElements int64           `json:"totalElements"`
	TotalPages    int             `json:"totalPages"`
	Page          int             `json:"page"`
	Size          int             `json:"size"`
}

type DeviceStatus string

const (
	DeviceStatusActive       DeviceStatus = "ACTIVE"
	DeviceStatusInactive      DeviceStatus = "INACTIVE"
	DeviceStatusDecommissioned DeviceStatus = "DECOMMISSIONED"
)

type DispatchStatus string

const (
	DispatchStatusPending    DispatchStatus = "PENDING"
	DispatchStatusApproved   DispatchStatus = "APPROVED"
	DispatchStatusInProgress DispatchStatus = "IN_PROGRESS"
	DispatchStatusCompleted  DispatchStatus = "COMPLETED"
	DispatchStatusRejected   DispatchStatus = "REJECTED"
)

type DispatchPriority string

const (
	DispatchPriorityLow    DispatchPriority = "LOW"
	DispatchPriorityMedium DispatchPriority = "MEDIUM"
	DispatchPriorityHigh   DispatchPriority = "HIGH"
	DispatchPriorityUrgent DispatchPriority = "URGENT"
)
