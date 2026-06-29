package com.taipei.iot.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {

	private String accessToken;

	private String refreshToken;

	private boolean needsSelection;

	@JsonProperty("isSuperAdmin")
	private boolean isSuperAdmin;

	private List<TenantOption> tenants;

	/**
	 * [Phase 3] When true, {@code accessToken} carries a short-lived password-change
	 * temporary token rather than the regular access token, and the client must redirect
	 * the user to the force-change-password flow.
	 */
	private boolean passwordChangeRequired;

}
