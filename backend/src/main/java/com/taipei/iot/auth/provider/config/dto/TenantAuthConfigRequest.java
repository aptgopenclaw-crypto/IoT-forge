package com.taipei.iot.auth.provider.config.dto;

import com.taipei.iot.auth.provider.AuthType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class TenantAuthConfigRequest {

	@NotNull
	private AuthType authType;

	/** Provider-specific configuration (e.g. LDAP url/baseDn, OIDC issuer/clientId) */
	private Map<String, Object> config;

	private Boolean fallbackLocal;

}
