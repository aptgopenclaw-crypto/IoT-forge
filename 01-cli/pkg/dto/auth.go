package dto

type LoginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type LoginResponse struct {
	TemporaryToken string `json:"temporaryToken"`
	ExpiresIn      int64  `json:"expiresIn"`
}

type TenantInfo struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type SelectTenantRequest struct {
	TemporaryToken string `json:"temporaryToken"`
	TenantID       string `json:"tenantId"`
}

type SelectTenantResponse struct {
	AccessToken  string `json:"accessToken"`
	RefreshToken string `json:"refreshToken"`
	ExpiresIn    int64  `json:"expiresIn"`
}

type RefreshTokenRequest struct {
	RefreshToken string `json:"refreshToken"`
}

type RefreshTokenResponse struct {
	AccessToken string `json:"accessToken"`
	ExpiresIn   int64  `json:"expiresIn"`
}
