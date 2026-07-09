package com.taipei.iot.vms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * VMS 整合設定。
 *
 * <p>
 * 對應 {@code application.yml} 中 {@code vms.*} 前綴的設定值。
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "vms")
@Data
public class VmsProperties {

	/** 媒體伺服器（ZLMediaKit / SRS）設定 */
	private MediaServer mediaServer = new MediaServer();

	/** 串流快取 TTL（秒），預設 5 分鐘 */
	private int streamCacheTtlSeconds = 300;

	/** 串流無人觀看逾時（秒），預設 60 秒 */
	private int streamIdleTimeoutSeconds = 60;

	/** VMS webhook IP whitelist，逗號分隔 */
	private String webhookAllowedIps = "127.0.0.1";

	@Data
	public static class MediaServer {

		/** 媒體伺服器類型：zlmediakit / srs */
		private String type = "zlmediakit";

		/** 媒體伺服器 REST API 入口 */
		private String apiUrl = "http://localhost:8080";

		/** 對外公開的播放 URL */
		private String publicUrl = "http://mediaserver:8080";

		/** ZLMediaKit API secret（選用） */
		private String secret = "";

	}

	@Data
	public static class WebRtc {

		/** 是否啟用 WebRTC 播放（預設 true） */
		private boolean enabled = true;

		/** ZLMediaKit WebRTC 連接埠（UDP） */
		private int port = 8000;

		/** STUN 伺服器（選用，穿透 NAT） */
		private String stunServer = "";

	}

}
