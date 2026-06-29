package com.taipei.iot.auth.provider.ldap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * LDAP/AD provider configuration, deserialized from
 * {@code tenant_auth_config.config_json}.
 *
 * <p>
 * 設定範例（存入 DB 前由 {@code AuthConfigEncryptor} 加密整個 JSON）：
 *
 * <pre>{@code
 * {
 *   "url":      "ldaps://ad.example.gov.tw:636",
 *   "base_dn":  "DC=example,DC=gov,DC=tw",
 *   "user_dn_pattern": "mail={0},OU=Users,DC=example,DC=gov,DC=tw"
 * }
 * }</pre>
 *
 * <ul>
 * <li>{@code url}: LDAP/LDAPS 連線 URL，必填</li>
 * <li>{@code base_dn}: 搜尋基底 DN，必填</li>
 * <li>{@code user_dn_pattern}: 使用者 DN 樣板，{0} 會被 email 取代； 若未設定則預設為
 * {@code mail={0},{base_dn}}</li>
 * <li>{@code connect_timeout_ms}: 連線逾時（毫秒），預設 5000</li>
 * <li>{@code read_timeout_ms}: 讀取逾時（毫秒），預設 10000</li>
 * </ul>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LdapConfig {

	@JsonProperty("url")
	private String url;

	@JsonProperty("base_dn")
	private String baseDn;

	/** DN pattern to bind with; {0} is replaced by the login identifier (email). */
	@JsonProperty("user_dn_pattern")
	private String userDnPattern;

	@JsonProperty("connect_timeout_ms")
	private int connectTimeoutMs = 5000;

	@JsonProperty("read_timeout_ms")
	private int readTimeoutMs = 10000;

	/**
	 * Build the full user DN from the pattern and identifier. Falls back to
	 * {@code mail={identifier},{baseDn}} if no pattern configured.
	 */
	public String buildUserDn(String identifier) {
		String pattern = (userDnPattern != null && !userDnPattern.isBlank()) ? userDnPattern : "mail={0}," + baseDn;
		return pattern.replace("{0}", identifier);
	}

}
