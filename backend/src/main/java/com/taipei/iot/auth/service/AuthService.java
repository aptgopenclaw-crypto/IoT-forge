package com.taipei.iot.auth.service;

import com.taipei.iot.auth.dto.request.ForgotPasswordRequest;
import com.taipei.iot.auth.dto.request.ForceChangePasswordRequest;
import com.taipei.iot.auth.dto.request.LoginRequest;
import com.taipei.iot.auth.dto.request.ResetPasswordRequest;
import com.taipei.iot.auth.dto.request.SelectTenantRequest;
import com.taipei.iot.auth.dto.request.SwitchTenantRequest;
import com.taipei.iot.auth.dto.response.LoginResult;
import com.taipei.iot.auth.dto.response.TokenResult;
import com.taipei.iot.auth.dto.response.UserInfoDto;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

	LoginResult login(LoginRequest request, HttpServletRequest httpRequest);

	TokenResult selectTenant(String userId, SelectTenantRequest request, HttpServletRequest httpRequest);

	TokenResult switchTenant(String userId, SwitchTenantRequest request, HttpServletRequest httpRequest);

	TokenResult refreshToken(String refreshToken, HttpServletRequest httpRequest);

	void logout(String refreshToken);

	UserInfoDto getCurrentUser(String userId, String tenantId);

	void forgotPassword(ForgotPasswordRequest request);

	void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest);

	/**
	 * [Phase 3] Consume a password-change temporary token and update the user's password.
	 * On success, returns a normal {@link LoginResult} mirroring the post-authentication
	 * branch that would have been taken at login time (single-tenant → full
	 * access+refresh; multi-tenant / super-admin → tenant selection temp token).
	 */
	LoginResult forceChangePassword(String passwordChangeToken, ForceChangePasswordRequest request,
			HttpServletRequest httpRequest);

}
