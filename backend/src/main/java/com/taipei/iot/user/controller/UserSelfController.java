package com.taipei.iot.user.controller;

import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.annotation.RateLimit;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.user.dto.request.ChangePasswordRequest;
import com.taipei.iot.user.dto.request.UpdateOwnProfileRequest;
import com.taipei.iot.user.service.UserSelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1/auth/user")
@RequiredArgsConstructor
@Tag(name = "UserSelf", description = "使用者自助功能：更新個人資料、修改密碼")
public class UserSelfController {

	private final UserSelfService userSelfService;

	private final JwtUtil jwtUtil;

	@PutMapping("/my")
	@Operation(summary = "更新我的個人資料", description = "由登入者更新自己的基本資料")
	public BaseResponse<UserEntity> updateOwnProfile(Authentication authentication,
			@RequestBody UpdateOwnProfileRequest req) {
		String userId = (String) authentication.getPrincipal();
		UserEntity updated = userSelfService.updateOwnProfile(userId, req);
		return BaseResponse.success(updated);
	}

	@PostMapping("/change-password")
	@RateLimit(key = "change-password", limit = 5, period = 300)
	@Operation(summary = "修改密碼", description = "由登入者修改自己的密碼；若有 refresh token，會一起驗證當前 session")
	public BaseResponse<Void> changePassword(Authentication authentication,
			@Valid @RequestBody ChangePasswordRequest req,
			@CookieValue(name = "refresh_token", required = false) String refreshToken) {
		String userId = (String) authentication.getPrincipal();
		String currentSessionJti = extractJtiSafely(refreshToken);
		userSelfService.changePassword(userId, req, currentSessionJti);
		return BaseResponse.success(null);
	}

	private String extractJtiSafely(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank())
			return null;
		try {
			return jwtUtil.parseToken(refreshToken).getId();
		}
		catch (Exception e) {
			log.debug("Failed to extract JTI from refresh token: {}", e.getMessage());
			return null;
		}
	}

}
