package com.taipei.iot.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ClamAV 病毒掃描 — 透過 clamd INSTREAM 協定掃描檔案。
 * <p>
 * 當 {@code virus-scan.enabled=true} 時啟用。
 * </p>
 *
 * <h3>clamd INSTREAM 協定</h3>
 * <ol>
 * <li>發送 {@code zINSTREAM\0}</li>
 * <li>發送 chunk: 4 bytes (big-endian length) + data</li>
 * <li>發送 zero-length chunk (4 bytes of 0) 結束</li>
 * <li>讀取回應: {@code stream: OK\0} 或 {@code stream: <virus> FOUND\0}</li>
 * </ol>
 *
 * <h3>N-4 防護（2026-05-27）：StreamMaxLength 截斷防範</h3>
 * <p>
 * clamd 的 {@code StreamMaxLength}（預設 25MB）會在 INSTREAM 上限被觸及時<b>靜默截斷</b> 並只掃描已收到的前段資料。若惡意
 * payload 藏在尾部即可繞過掃描。
 * </p>
 * <p>
 * 本實作在連線前先檢查 {@code Files.size(path)}：
 * </p>
 * <ul>
 * <li>若超過 {@code virus-scan.clamav.stream-max-length}（預設 25MB），直接回傳
 * {@link ScanResult#ERROR}（fail-closed）並記 WARN，不嘗試 INSTREAM。</li>
 * <li>呼叫端（如 {@link FileUploadTemplate}）應視為「無法判定 → 拒絕上傳」。</li>
 * <li>此值<b>必須</b>與 clamd 設定一致；若 clamd 端調高，請同步調整本設定。</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "virus-scan.enabled", havingValue = "true")
public class ClamAvVirusScanService implements VirusScanService {

	private final String host;

	private final int port;

	private final int timeout;

	private final long streamMaxLength;

	private static final int CHUNK_SIZE = 8192;

	public ClamAvVirusScanService(@Value("${virus-scan.clamav.host:localhost}") String host,
			@Value("${virus-scan.clamav.port:3310}") int port, @Value("${virus-scan.clamav.timeout:5000}") int timeout,
			@Value("${virus-scan.clamav.stream-max-length:26214400}") long streamMaxLength) {
		this.host = host;
		this.port = port;
		this.timeout = timeout;
		this.streamMaxLength = streamMaxLength;
	}

	@Override
	public ScanResult scan(String filePath) {
		// N-10: 只記錄檔名，不暴露完整路徑（避免攻擊者推斷儲存結構）
		String fileName = Path.of(filePath).getFileName().toString();

		// N-4: 連線前先檢查檔案大小，避免 clamd StreamMaxLength 截斷後靜默放行
		try {
			long size = Files.size(Path.of(filePath));
			if (size > streamMaxLength) {
				log.warn(
						"File {} size {} bytes exceeds clamd StreamMaxLength {} — "
								+ "INSTREAM would be truncated, returning ERROR (fail-closed).",
						fileName, size, streamMaxLength);
				log.debug("Full path of oversized file: {}", filePath);
				return ScanResult.ERROR;
			}
		}
		catch (IOException e) {
			log.error("Failed to read file size before ClamAV scan ({}): {}", fileName, e.getMessage());
			log.debug("Full path of unreadable file: {}", filePath);
			return ScanResult.ERROR;
		}

		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), timeout);
			socket.setSoTimeout(timeout);

			OutputStream out = socket.getOutputStream();
			InputStream in = socket.getInputStream();

			// 1. Send INSTREAM command
			out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
			out.flush();

			// 2. Send file in chunks
			try (InputStream fileStream = Files.newInputStream(Path.of(filePath))) {
				byte[] buffer = new byte[CHUNK_SIZE];
				int bytesRead;
				while ((bytesRead = fileStream.read(buffer)) != -1) {
					out.write(ByteBuffer.allocate(4).putInt(bytesRead).array());
					out.write(buffer, 0, bytesRead);
				}
			}

			// 3. Send zero-length chunk to signal end
			out.write(new byte[] { 0, 0, 0, 0 });
			out.flush();

			// 4. Read response
			byte[] response = in.readAllBytes();
			String result = new String(response, StandardCharsets.US_ASCII).trim();

			// N-10: 只記錄檔名，不暴露完整路徑（避免攻擊者推斷儲存結構）
			log.info("ClamAV scan result for {}: {}", fileName, result);
			log.debug("ClamAV scan full path: {}, result: {}", filePath, result);

			if (result.endsWith("OK")) {
				return ScanResult.CLEAN;
			}
			else if (result.contains("FOUND")) {
				log.warn("Virus detected in file {}: {}", fileName, result);
				return ScanResult.INFECTED;
			}
			else {
				log.error("Unexpected ClamAV response: {}", result);
				return ScanResult.ERROR;
			}
		}
		catch (IOException e) {
			log.error("ClamAV connection failed ({}:{}): {}", host, port, e.getMessage());
			return ScanResult.ERROR;
		}
	}

	/**
	 * F-4：clamd PING 健康度檢查。
	 * <p>
	 * 透過 {@code zPING\0} 命令確認 clamd 是否在指定 host:port 上回應 {@code PONG}。 供
	 * {@code ClamAvHealthIndicator} 暴露於 {@code /actuator/health/clamav}。
	 * </p>
	 * @return {@code true} 表示 clamd 在 timeout 內正確回應 {@code PONG}； 其餘情況（連線失敗 / IO error /
	 * 非預期回應）回 {@code false}
	 */
	public boolean ping() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), timeout);
			socket.setSoTimeout(timeout);

			socket.getOutputStream().write("zPING\0".getBytes(StandardCharsets.US_ASCII));
			socket.getOutputStream().flush();

			byte[] response = socket.getInputStream().readAllBytes();
			String result = new String(response, StandardCharsets.US_ASCII).trim();
			// clamd 回應 "PONG"（z prefix 命令以 \0 結尾，trim 後即 "PONG"）
			boolean ok = "PONG".equals(result);
			if (!ok) {
				log.warn("ClamAV PING unexpected response: '{}'", result);
			}
			return ok;
		}
		catch (IOException e) {
			log.warn("ClamAV PING failed ({}:{}): {}", host, port, e.getMessage());
			return false;
		}
	}

	/** F-4：health indicator 用，回 clamd host */
	public String getHost() {
		return host;
	}

	/** F-4：health indicator 用，回 clamd port */
	public int getPort() {
		return port;
	}

	/** F-4：health indicator 用，回設定的 stream-max-length（bytes） */
	public long getStreamMaxLength() {
		return streamMaxLength;
	}

}
