package com.taipei.iot.vms.adapter;

import jakarta.annotation.PostConstruct;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.cert.X509Certificate;
import org.springframework.stereotype.Component;

/**
 * VMS 內部 HTTPS 連線 SSL 工具。
 *
 * <p>
 * Nx Witness 等 VMS 伺服器通常使用自簽憑證，Java 預設 SSLContext 會拒絕連線。 此工具使用
 * {@link X509ExtendedTrustManager} 信任所有憑證，同時跳過 hostname 驗證（JDK {@code HttpClient} 需要
 * extended 版本的 trust manager 才能完全繞過 hostname verification）。 僅用於內部 VMS 通訊（不涉及外部網路）。
 * </p>
 */
@Component
class VmsSslUtil {

	private static final SSLContext INSECURE_SSL_CONTEXT;

	private static final X509ExtendedTrustManager TRUST_ALL_MANAGER;

	private static final HostnameVerifier TRUST_ALL_HOSTNAME = (host, session) -> true;

	static {
		try {
			// 必須使用 X509ExtendedTrustManager，否則 JDK HttpClient 會繞過 override
			// 並執行內建的 hostname verification
			TRUST_ALL_MANAGER = new X509ExtendedTrustManager() {
				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
				}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
				}

				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
				}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
				}

				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType) {
				}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType) {
				}

				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}
			};

			SSLContext ssl = SSLContext.getInstance("TLS");
			ssl.init(null, new TrustManager[] { TRUST_ALL_MANAGER }, new java.security.SecureRandom());
			INSECURE_SSL_CONTEXT = ssl;

			// 設定為 JVM 預設，使 HttpURLConnection-based client 也能信任自簽憑證並跳過 hostname 驗證
			SSLContext.setDefault(INSECURE_SSL_CONTEXT);
			HttpsURLConnection.setDefaultHostnameVerifier(TRUST_ALL_HOSTNAME);
		}
		catch (Exception e) {
			throw new RuntimeException("無法初始化 VMS SSLContext", e);
		}
	}

	/**
	 * 確保 static initializer 在 Spring 啟動時被執行（設定 JVM 全域 SSL 預設）。 單純觸發類別載入，所有初始化已在 static
	 * block 完成。
	 */
	@PostConstruct
	void init() {
		// static initializer 已在類別載入時執行
	}

	/** 回傳信任所有憑證的 SSLContext（僅限內部 VMS 使用）。 */
	static SSLContext sslContext() {
		return INSECURE_SSL_CONTEXT;
	}

	static X509ExtendedTrustManager trustManager() {
		return TRUST_ALL_MANAGER;
	}

	/** 回傳信任所有 hostname 的 HostnameVerifier（僅限內部 VMS 使用）。 */
	static HostnameVerifier hostnameVerifier() {
		return TRUST_ALL_HOSTNAME;
	}

	private VmsSslUtil() {
	}

}
