package dto

type DispatchResponse struct {
	ID          int64            `json:"id"`
	Title       string           `json:"title"`
	Description string           `json:"description,omitempty"`
	Status      DispatchStatus   `json:"status"`
	Priority    DispatchPriority `json:"priority"`
	AssignedTo  string           `json:"assignedTo,omitempty"`
	DeviceID    *int64           `json:"deviceId,omitempty"`
	TenantID    string           `json:"tenantId"`
	CreatedAt   string           `json:"createdAt"`
	UpdatedAt   string           `json:"updatedAt,omitempty"`
}

type DispatchRequest struct {
	Title       string           `json:"title,omitempty"`
	Description string           `json:"description,omitempty"`
	Status      DispatchStatus   `json:"status,omitempty"`
	Priority    DispatchPriority `json:"priority,omitempty"`
	AssignedTo  string           `json:"assignedTo,omitempty"`
	DeviceID    *int64           `json:"deviceId,omitempty"`
}
