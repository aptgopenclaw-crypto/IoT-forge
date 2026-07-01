package com.taipei.iot.ingest.source.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.ingest.client.IngestClientPrincipal;
import com.taipei.iot.ingest.client.TelemetryIngestClient;
import com.taipei.iot.ingest.client.TelemetryIngestClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * HTTP ingest 機器對機器認證 filter。讀取 {@code X-API-Key} / {@code X-API-Secret} 標頭，查
 * {@link TelemetryIngestClient} 並以 BCrypt 比對 secret；成功則置入帶 {@code INGEST} 權限的
 * {@code Authentication}（principal 為 {@link IngestClientPrincipal}）並設定租戶情境。
 * <p>
 * 「憑證不存在」與「secret 不符」回傳相同錯誤碼，避免帳號列舉。失敗以 {@link BaseResponse} JSON 短路回應。
 */
public class IngestApiKeyAuthFilter extends OncePerRequestFilter {

	static final String API_KEY_HEADER = "X-API-Key";

	static final String API_SECRET_HEADER = "X-API-Secret";

	private final TelemetryIngestClientRepository clientRepository;

	private final PasswordEncoder passwordEncoder;

	private final ObjectMapper objectMapper;

	public IngestApiKeyAuthFilter(TelemetryIngestClientRepository clientRepository, PasswordEncoder passwordEncoder,
			ObjectMapper objectMapper) {
		this.clientRepository = clientRepository;
		this.passwordEncoder = passwordEncoder;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String apiKey = request.getHeader(API_KEY_HEADER);
		String apiSecret = request.getHeader(API_SECRET_HEADER);

		if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
			writeError(response, ErrorCode.IOT_INGEST_CREDENTIALS_INVALID);
			return;
		}

		Optional<TelemetryIngestClient> clientOpt = clientRepository.findByApiKey(apiKey);
		if (clientOpt.isEmpty() || !passwordEncoder.matches(apiSecret, clientOpt.get().getSecretHash())) {
			writeError(response, ErrorCode.IOT_INGEST_CREDENTIALS_INVALID);
			return;
		}

		TelemetryIngestClient client = clientOpt.get();
		if (!client.isEnabled()) {
			writeError(response, ErrorCode.IOT_INGEST_CLIENT_DISABLED);
			return;
		}

		IngestClientPrincipal principal = new IngestClientPrincipal(client.getId(), client.getTenantId(),
				client.getClientName());
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null,
				List.of(new SimpleGrantedAuthority("INGEST")));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		try {
			TenantContext.setCurrentTenantId(client.getTenantId());
			filterChain.doFilter(request, response);
		}
		finally {
			TenantContext.clear();
			SecurityContextHolder.clearContext();
		}
	}

	private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getHttpStatus());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(objectMapper.writeValueAsString(BaseResponse.fail(errorCode)));
	}

}
