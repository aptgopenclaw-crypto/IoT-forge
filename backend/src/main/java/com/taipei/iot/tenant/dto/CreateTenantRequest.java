package com.taipei.iot.tenant.dto;

import com.taipei.iot.auth.provider.AuthType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTenantRequest {

	@NotBlank
	@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$", message = "場域代碼須為 2-30 字元，大寫英文開頭，僅含大寫英文、數字、底線")
	private String tenantCode;

	@NotBlank
	@Size(max = 200)
	private String tenantName;

	private String deploymentMode = "CLOUD";

	/**
	 * 初始認證方式。預設 {@link AuthType#LOCAL}。 創建場域時同步寫入 tenant_auth_config，避免必須另外呼叫 auth-config
	 * API。
	 */
	@NotNull
	private AuthType initialAuthMethod = AuthType.LOCAL;

	// ── 初始管理員帳號（選填，若填寫則一併建立帳號）──
	@Email
	private String adminEmail;

	private String adminDisplayName;

	@Size(min = 8)
	private String adminPassword;

	@AssertTrue(message = "若提供 adminEmail 則 adminPassword 為必填，反之亦然")
	private boolean isAdminFieldsConsistent() {
		boolean hasEmail = adminEmail != null && !adminEmail.isBlank();
		boolean hasPass = adminPassword != null && !adminPassword.isBlank();
		return hasEmail == hasPass;
	}

}
