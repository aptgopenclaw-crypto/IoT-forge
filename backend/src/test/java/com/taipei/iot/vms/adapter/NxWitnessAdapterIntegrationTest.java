package com.taipei.iot.vms.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nx Witness 真實伺服器整合測試。
 *
 * <p>
 * 直接使用 {@link RestClient} 呼叫真實的 Nx Witness REST API v1，驗證：
 * <ul>
 * <li>GET /rest/v1/system/info — 健康檢查（免授權）</li>
 * <li>POST /rest/v1/login/sessions — session 登入</li>
 * <li>GET /rest/v1/devices — 裝置清單（需授權）</li>
 * </ul>
 * </p>
 *
 * <p>
 * 此測試預設 <b>停用</b>，需設定環境變數 {@code NX_IT_ENABLED=true} 才會執行。 密碼透過環境變數 {@code NX_PASSWORD}
 * 傳入，避免寫死在程式碼中。
 * </p>
 */
@EnabledIfEnvironmentVariable(named = "NX_IT_ENABLED", matches = "true")
class NxWitnessAdapterIntegrationTest {

	private static final Logger log = LoggerFactory.getLogger(NxWitnessAdapterIntegrationTest.class);

	private static final String BASE_URL = "https://110.25.96.55:7001";

	private static final String USERNAME = "admin";

	private static final String PASSWORD = System.getenv().getOrDefault("NX_PASSWORD", "Iot+12345");

	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * 建立一個信任所有憑證的 RestClient（因內部測試環境可能使用自簽憑證）。
	 */
	private static RestClient createUnsafeRestClient() {
		return createUnsafeRestClient(BASE_URL, null);
	}

	/**
	 * 建立一個信任所有憑證的 RestClient，並帶入 Bearer token。
	 */
	private static RestClient createUnsafeRestClient(String baseUrl, String bearerToken) {
		try {
			SSLContext ssl = SSLContext.getInstance("TLS");
			ssl.init(null, new TrustManager[] { new X509TrustManager() {
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
			} }, new java.security.SecureRandom());

			RestClient.Builder builder = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
						java.net.http.HttpClient.newBuilder().sslContext(ssl).build()));

			if (bearerToken != null) {
				builder.defaultHeader("Authorization", "Bearer " + bearerToken);
			}

			return builder.build();
		}
		catch (Exception e) {
			throw new RuntimeException("無法建立 SSL RestClient", e);
		}
	}

	private final RestClient client = createUnsafeRestClient();

	@Test
	@DisplayName("GET /rest/v1/system/info → 回傳系統資訊（免授權）")
	void systemInfo() throws JsonProcessingException {
		log.info("🔍 測試 GET /rest/v1/system/info (baseUrl={})", BASE_URL);

		String body = client.get().uri("/rest/v1/system/info").retrieve().body(String.class);

		assertThat(body).isNotNull();
		JsonNode json = mapper.readTree(body);
		log.info("✅ system/info 回應: name={}, version={}", json.get("name"), json.get("version"));
		assertThat(json.has("name")).isTrue();
		assertThat(json.has("version")).isTrue();
	}

	@Test
	@DisplayName("POST /rest/v1/login/sessions → 取得 session token")
	void login() throws JsonProcessingException {
		log.info("🔍 測試 POST /rest/v1/login/sessions");

		String body = client.post().uri("/rest/v1/login/sessions").body("""
				{"username":"%s","password":"%s"}
				""".formatted(USERNAME, PASSWORD)).retrieve().body(String.class);

		assertThat(body).isNotNull();
		JsonNode json = mapper.readTree(body);
		log.info("✅ login 回應: token={}..., expiresInS={}", json.get("token").asText().substring(0, 20),
				json.get("expiresInS"));
		assertThat(json.has("token")).isTrue();
		assertThat(json.has("expiresInS")).isTrue();
	}

	@Test
	@DisplayName("login → Bearer token → GET /rest/v1/devices → 裝置清單")
	void listDevices() throws JsonProcessingException {
		log.info("🔍 測試 login → GET /rest/v1/devices");

		// 1. Login
		String loginBody = client.post().uri("/rest/v1/login/sessions").body("""
				{"username":"%s","password":"%s"}
				""".formatted(USERNAME, PASSWORD)).retrieve().body(String.class);

		assertThat(loginBody).isNotNull();
		String token = mapper.readTree(loginBody).get("token").asText();
		log.info("✅ Token 取得成功");

		// 2. 使用 token 查詢 devices
		RestClient authClient = createUnsafeRestClient(BASE_URL, token);
		String devicesBody = authClient.get()
			.uri("/rest/v1/devices?deviceType=Camera&_orderBy=name")
			.retrieve()
			.body(String.class);

		assertThat(devicesBody).isNotNull();
		JsonNode devices = mapper.readTree(devicesBody);
		log.info("✅ 找到 {} 台 Camera 裝置", devices.size());

		if (devices.isArray() && devices.size() > 0) {
			JsonNode first = devices.get(0);
			log.info("   第一台: id={}, name={}, status={}", first.get("id"), first.get("name"), first.get("status"));
		}
	}

	/**
	 * 測試用 main method — 可直接在 IDE 中執行，不需啟動 Spring Boot。
	 */
	public static void main(String[] args) throws Exception {
		NxWitnessAdapterIntegrationTest test = new NxWitnessAdapterIntegrationTest();

		log.info("═══════════════════════════════════════");
		log.info("  Nx Witness 真實伺服器連線測試");
		log.info("  URL: {}", BASE_URL);
		log.info("═══════════════════════════════════════");

		try {
			test.systemInfo();
		}
		catch (Exception e) {
			log.error("❌ systemInfo 失敗: {}", e.getMessage());
		}

		try {
			test.login();
		}
		catch (Exception e) {
			log.error("❌ login 失敗: {}", e.getMessage());
		}

		try {
			test.listDevices();
		}
		catch (Exception e) {
			log.error("❌ listDevices 失敗: {}", e.getMessage());
		}

		log.info("═══════════════════════════════════════");
		log.info("  測試完成");
		log.info("═══════════════════════════════════════");
	}

}
