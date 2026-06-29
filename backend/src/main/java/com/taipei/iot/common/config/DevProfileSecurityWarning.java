package com.taipei.iot.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * N-13 防護：dev / test profile 啟動時輸出顯眼的安全警告，提醒 application-dev.yml 內含明文憑證（DB password、Redis
 * password、JWT secret、SMTP credentials）。
 *
 * <p>
 * 此警告確保：
 * <ul>
 * <li>開發者在切換部署環境時意識到 dev profile 不適合生產環境</li>
 * <li>CI/CD log 中可被 grep 偵測，避免意外以 dev profile 部署至正式環境</li>
 * </ul>
 *
 * <p>
 * 若部署流程變更（如 Docker compose 直接帶 dev profile 到 staging）， 需重新評估此風險。參照 auth-v2 N-2 同源議題。
 * </p>
 */
@Slf4j
@Component
@Profile({ "dev", "test" })
public class DevProfileSecurityWarning {

	@PostConstruct
	void warnPlaintextCredentials() {
		log.warn("╔══════════════════════════════════════════════════════════════════╗");
		log.warn("║  [SECURITY] Dev/Test profile active — plaintext credentials     ║");
		log.warn("║  are embedded in application-dev.yml (DB, Redis, JWT, SMTP).    ║");
		log.warn("║  This profile MUST NOT be used in production deployments.       ║");
		log.warn("║  Ensure env vars or Vault injection are used for prod secrets.  ║");
		log.warn("╚══════════════════════════════════════════════════════════════════╝");
	}

}
