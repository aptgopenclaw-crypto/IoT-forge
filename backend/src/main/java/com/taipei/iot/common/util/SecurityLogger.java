package com.taipei.iot.common.util;

import com.taipei.iot.common.enums.SecurityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * 統一安全事件日誌工具類。
 * <p>
 * 所有安全相關事件（登入失敗、CAPTCHA 錯誤、JWT 異常、速率限制、403 存取拒絕等） 均透過此工具類輸出，統一加上 {@code [SECURITY]}
 * prefix，便於：
 * <ul>
 * <li>{@code grep "[SECURITY]" security.log} 快速過濾攻擊行為</li>
 * <li>設定告警規則（例如 5 分鐘內超過 50 筆 [SECURITY] → 通知）</li>
 * <li>區分「正常使用者操作失敗」和「疑似攻擊行為」</li>
 * </ul>
 *
 * <p>
 * Logger name 為 {@code SECURITY}，對應 logback-spring.xml 中的獨立 appender， 安全事件會同時輸出到 console
 * 和 {@code security.log}。
 *
 * <h3>F-7：結構化 JSON 輸出（2026-05-28）</h3>
 * <p>
 * 使用 SLF4J 2.x Fluent API（{@link LoggingEventBuilder#addKeyValue(String, Object)}）
 * 在每筆安全事件上附加結構化欄位：
 * </p>
 * <ul>
 * <li>{@code security_event} — 事件類型（如 {@code LOGIN_FAILED}）</li>
 * <li>{@code security_ip} — 來源 IP（已 sanitize）</li>
 * <li>{@code security_category} — 固定 {@code "SECURITY"}</li>
 * <li>動態欄位 — 依 {@code details} 中的 {@code key=value} 對拆解，prefix {@code security_}。 若
 * detail 無 {@code =} 則欄位名為 {@code security_detail_N}</li>
 * </ul>
 * <p>
 * 搭配 logback-spring.xml 中的 {@code LogstashEncoder} JSON appender（profile
 * {@code json-log}）， 即可輸出 JSON 格式供 Loki / ELK / Splunk 直接索引，無須正則切欄位。
 * </p>
 * <p>
 * 純文字 appender 仍輸出 {@code [SECURITY] EVENT ip=IP detail1 detail2}，不影響既有日誌流。
 * </p>
 */
public final class SecurityLogger {

	private static final Logger log = LoggerFactory.getLogger("SECURITY");

	private SecurityLogger() {
	}

	/**
	 * 清理日誌內容中的控制字元，防止 CRLF 日誌注入攻擊（CWE-117）。
	 *
	 * <h3>為什麼需要這個？</h3>
	 * <p>
	 * 日誌檔是純文字，換行符號（{@code \r\n}）就是「下一筆日誌」的分隔符號。 如果攻擊者能在輸入欄位（如 email、password）中插入
	 * {@code %0d%0a}（URL 編碼的 \r\n）， 就能在日誌中偽造任意內容：
	 * </p>
	 * <pre>
	 *   // 攻擊者送出的 email：
	 *   attacker@evil.com\r\n[SECURITY] LOGIN_FAILED ip=192.168.1.50 email=admin@company.com
	 *
	 *   // 日誌中會變成兩筆，第二筆是偽造的：
	 *   [SECURITY] LOGIN_FAILED ip=1.2.3.4 email=attacker@evil.com
	 *   [SECURITY] LOGIN_FAILED ip=192.168.1.50 email=admin@company.com  ← 假的！
	 * </pre>
	 * <p>
	 * 這會導致：嫁禍他人、淹沒真實攻擊紀錄、誤導安全調查、污染 ELK/Grafana 索引。
	 * </p>
	 *
	 * <h3>Unicode line separator / NULL byte 補強 [common v2 N-1]</h3>
	 * <p>
	 * 除了 ASCII 的 {@code \r \n \t}，許多 SIEM / log parser（logstash、grok、jq、Splunk）
	 * 也會把以下字元視為換行或字串終止符；若不一併過濾，攻擊者仍可塞入這些字元偽造日誌行：
	 * </p>
	 * <ul>
	 * <li>{@code U+2028} LINE SEPARATOR</li>
	 * <li>{@code U+2029} PARAGRAPH SEPARATOR</li>
	 * <li>{@code U+0000} NULL byte（C 字串終止符；部分後端會在此截斷）</li>
	 * </ul>
	 * @param input 任何要寫入日誌的外部輸入值
	 * @return 移除上述控制字元後的安全字串；null 輸入回傳 "null"
	 */
	static String sanitize(String input) {
		if (input == null) {
			return "null";
		}
		return input.replace("\r", "")
			.replace("\n", "")
			.replace("\t", "")
			.replace("\u2028", "")
			.replace("\u2029", "")
			.replace("\u0000", "");
	}

	/**
	 * 記錄安全警告事件。
	 * @param event 安全事件類型
	 * @param ip 來源 IP
	 * @param details 額外的 key=value 資訊（例如 "email=test@example.com", "reason=bad_password"）
	 */
	public static void warn(SecurityEvent event, String ip, String... details) {
		LoggingEventBuilder builder = log.atWarn();
		attachStructuredFields(builder, event, ip, details);
		builder.log("[SECURITY] {} ip={} {}", event.name(), sanitize(ip), sanitizeDetails(details));
	}

	/**
	 * 記錄安全資訊事件（低風險但需記錄，例如密碼重設請求）。
	 * @param event 安全事件類型
	 * @param ip 來源 IP
	 * @param details 額外的 key=value 資訊
	 */
	public static void info(SecurityEvent event, String ip, String... details) {
		LoggingEventBuilder builder = log.atInfo();
		attachStructuredFields(builder, event, ip, details);
		builder.log("[SECURITY] {} ip={} {}", event.name(), sanitize(ip), sanitizeDetails(details));
	}

	/**
	 * F-7：將結構化欄位附加到 SLF4J LoggingEventBuilder。
	 * <p>
	 * JSON encoder（如 LogstashEncoder）會將 key-value pairs 直接輸出為 JSON 欄位； 純文字 encoder
	 * 會忽略它們，只使用 formatted message。
	 * </p>
	 */
	static void attachStructuredFields(LoggingEventBuilder builder, SecurityEvent event, String ip, String... details) {
		builder.addKeyValue("security_event", event.name());
		builder.addKeyValue("security_ip", sanitize(ip));
		builder.addKeyValue("security_category", "SECURITY");

		if (details != null) {
			int unnamedIndex = 0;
			for (String detail : details) {
				if (detail == null) {
					continue;
				}
				String sanitized = sanitize(detail);
				int eqIdx = sanitized.indexOf('=');
				if (eqIdx > 0 && eqIdx < sanitized.length() - 1) {
					String key = sanitized.substring(0, eqIdx);
					String value = sanitized.substring(eqIdx + 1);
					builder.addKeyValue("security_" + key, value);
				}
				else {
					builder.addKeyValue("security_detail_" + unnamedIndex, sanitized);
					unnamedIndex++;
				}
			}
		}
	}

	/**
	 * 對所有 detail 值逐一清理後合併。
	 */
	static String sanitizeDetails(String... details) {
		if (details == null || details.length == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < details.length; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(sanitize(details[i]));
		}
		return sb.toString();
	}

}
