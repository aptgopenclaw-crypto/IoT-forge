package com.taipei.iot.common.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N-4 防護驗證：oversized file / unreadable file 必須在 INSTREAM 連線前 fail-closed。
 * <p>
 * 不依賴真實 clamd；採用不存在的 host:port 確保「若未被前置攔截，會掉到 socket ERROR」， 並以 ListAppender 驗證實際的 WARN
 * 訊息，確認攔截路徑正確。
 * </p>
 */
class ClamAvVirusScanServiceTest {

	/** 連線會失敗的 port（loopback + 高機率未監聽） */
	private static final String UNREACHABLE_HOST = "127.0.0.1";

	private static final int UNREACHABLE_PORT = 1;

	private static final int FAST_TIMEOUT = 200;

	private ListAppender<ILoggingEvent> appender;

	private Logger logger;

	@BeforeEach
	void setUp() {
		logger = (Logger) LoggerFactory.getLogger(ClamAvVirusScanService.class);
		appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void tearDown() {
		logger.detachAppender(appender);
	}

	@Test
	void scan_fileExceedsStreamMaxLength_returnsErrorFailClosed(@TempDir Path tempDir) throws IOException {
		// streamMaxLength = 10 bytes；建 11 bytes 檔案
		Path big = tempDir.resolve("big.bin");
		Files.write(big, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 });

		ClamAvVirusScanService service = new ClamAvVirusScanService(UNREACHABLE_HOST, UNREACHABLE_PORT, FAST_TIMEOUT,
				/* streamMaxLength */ 10L);

		VirusScanService.ScanResult result = service.scan(big.toString());

		assertThat(result).isEqualTo(VirusScanService.ScanResult.ERROR);
		assertWarnLogContains("exceeds clamd StreamMaxLength");
		assertWarnLogContains("fail-closed");
	}

	@Test
	void scan_fileAtExactlyStreamMaxLength_proceedsPastSizeCheck(@TempDir Path tempDir) throws IOException {
		// size == streamMaxLength 應允許繼續（嚴格 > 才攔截）；
		// 實際 socket 會連線失敗 → 仍回 ERROR，但 WARN 不應包含 size 攔截訊息
		Path exact = tempDir.resolve("exact.bin");
		Files.write(exact, new byte[] { 1, 2, 3, 4, 5 });

		ClamAvVirusScanService service = new ClamAvVirusScanService(UNREACHABLE_HOST, UNREACHABLE_PORT, FAST_TIMEOUT,
				/* streamMaxLength */ 5L);

		VirusScanService.ScanResult result = service.scan(exact.toString());

		assertThat(result).isEqualTo(VirusScanService.ScanResult.ERROR);
		// 不該被 size check 攔截 — 沒有 StreamMaxLength WARN
		assertThat(allWarnMessages()).as("size==threshold 不該觸發 StreamMaxLength 攔截")
			.noneMatch(m -> m.contains("exceeds clamd StreamMaxLength"));
	}

	@Test
	void scan_missingFile_returnsErrorFailClosed() {
		ClamAvVirusScanService service = new ClamAvVirusScanService(UNREACHABLE_HOST, UNREACHABLE_PORT, FAST_TIMEOUT,
				26214400L);

		VirusScanService.ScanResult result = service.scan("/nonexistent/path/does-not-exist-" + System.nanoTime());

		assertThat(result).isEqualTo(VirusScanService.ScanResult.ERROR);
		boolean loggedSizeRead = appender.list.stream()
			.filter(e -> e.getLevel() == Level.ERROR)
			.map(ILoggingEvent::getFormattedMessage)
			.anyMatch(m -> m.contains("Failed to read file size before ClamAV scan"));
		assertThat(loggedSizeRead).as("missing file 應在 size check 階段被攔截並記 ERROR").isTrue();
	}

	@Test
	void scan_smallFile_attemptsConnectionAndFailsCleanly(@TempDir Path tempDir) throws IOException {
		// 小檔案不應被 size check 攔截；socket 連線會失敗 → ERROR
		Path small = tempDir.resolve("small.txt");
		Files.write(small, "hello".getBytes());

		ClamAvVirusScanService service = new ClamAvVirusScanService(UNREACHABLE_HOST, UNREACHABLE_PORT, FAST_TIMEOUT,
				26214400L);

		VirusScanService.ScanResult result = service.scan(small.toString());

		assertThat(result).isEqualTo(VirusScanService.ScanResult.ERROR);
		// 應走到 socket connection ERROR，而非 size check
		boolean loggedSocketError = appender.list.stream()
			.filter(e -> e.getLevel() == Level.ERROR)
			.map(ILoggingEvent::getFormattedMessage)
			.anyMatch(m -> m.contains("ClamAV connection failed"));
		assertThat(loggedSocketError).isTrue();
	}

	// ─── N-10: file path masking ─────────────────────────────────────────────

	@Test
	void scan_oversizedFile_logsOnlyFilenameInWarn_notFullPath(@TempDir Path tempDir) throws IOException {
		// N-10: WARN 訊息不應洩露完整路徑結構
		Path subDir = tempDir.resolve("uploads/2026-05/tenant-3");
		Files.createDirectories(subDir);
		Path big = subDir.resolve("secret-doc.pdf");
		Files.write(big, new byte[11]);

		ClamAvVirusScanService service = new ClamAvVirusScanService(UNREACHABLE_HOST, UNREACHABLE_PORT, FAST_TIMEOUT,
				/* streamMaxLength */ 10L);

		service.scan(big.toString());

		// WARN log 應包含檔名但不含完整目錄結構
		List<String> warnMsgs = appender.list.stream()
			.filter(e -> e.getLevel() == Level.WARN)
			.map(ILoggingEvent::getFormattedMessage)
			.toList();
		assertThat(warnMsgs).anyMatch(m -> m.contains("secret-doc.pdf") || m.contains("exceeds"));
		// 不應包含完整的敏感路徑結構
		assertThat(warnMsgs).noneMatch(m -> m.contains("tenant-3/secret-doc.pdf"));
	}

	@Test
	void scan_connectionError_logsOnlyHostPort_notFilePath(@TempDir Path tempDir) throws IOException {
		// N-10: connection error log 不應暴露完整儲存路徑
		Path subDir = tempDir.resolve("secret/path/structure");
		Files.createDirectories(subDir);
		Path file = subDir.resolve("data.bin");
		Files.write(file, "hello".getBytes());

		ClamAvVirusScanService service = new ClamAvVirusScanService(UNREACHABLE_HOST, UNREACHABLE_PORT, FAST_TIMEOUT,
				26214400L);

		service.scan(file.toString());

		// ERROR log 應只含 host:port，不含完整 file path 目錄
		List<String> errorMsgs = appender.list.stream()
			.filter(e -> e.getLevel() == Level.ERROR)
			.map(ILoggingEvent::getFormattedMessage)
			.toList();
		assertThat(errorMsgs).anyMatch(m -> m.contains(UNREACHABLE_HOST));
		assertThat(errorMsgs).noneMatch(m -> m.contains("secret/path/structure"));
	}

	// ─── F-4: PING / getters ─────────────────────────────────────────────────

	@Test
	void ping_clamdRespondsPong_returnsTrue() throws IOException {
		try (FakeClamd clamd = FakeClamd.startReplying("PONG\0")) {
			ClamAvVirusScanService service = new ClamAvVirusScanService("127.0.0.1", clamd.port(), FAST_TIMEOUT,
					26214400L);

			assertThat(service.ping()).isTrue();
			assertThat(clamd.receivedCommand()).startsWith("zPING");
		}
	}

	@Test
	void ping_clamdReturnsUnexpectedResponse_returnsFalseAndWarns() throws IOException {
		try (FakeClamd clamd = FakeClamd.startReplying("WHAT?\0")) {
			ClamAvVirusScanService service = new ClamAvVirusScanService("127.0.0.1", clamd.port(), FAST_TIMEOUT,
					26214400L);

			assertThat(service.ping()).isFalse();
			assertWarnLogContains("unexpected response");
		}
	}

	@Test
	void ping_connectionFails_returnsFalseAndWarns() {
		ClamAvVirusScanService service = new ClamAvVirusScanService(UNREACHABLE_HOST, UNREACHABLE_PORT, FAST_TIMEOUT,
				26214400L);

		assertThat(service.ping()).isFalse();
		assertWarnLogContains("PING failed");
	}

	@Test
	void getters_returnConfiguredValues() {
		ClamAvVirusScanService service = new ClamAvVirusScanService("clamd.internal", 3311, 7000, 12345678L);

		assertThat(service.getHost()).isEqualTo("clamd.internal");
		assertThat(service.getPort()).isEqualTo(3311);
		assertThat(service.getStreamMaxLength()).isEqualTo(12345678L);
	}

	/**
	 * 模擬 clamd 的本地測試 server：accept 一個 client、讀整段 input、回固定 response。
	 */
	private static final class FakeClamd implements AutoCloseable {

		private final ServerSocket server;

		private final Thread acceptor;

		private volatile String received = "";

		private FakeClamd(String reply) throws IOException {
			this.server = new ServerSocket(0);
			this.server.setSoTimeout(2000);
			this.acceptor = new Thread(() -> {
				try (Socket client = server.accept();
						InputStream in = client.getInputStream();
						OutputStream out = client.getOutputStream()) {
					// 讀 client 第一段（簡單讀完現有 buffer，避免阻塞）
					byte[] buf = new byte[64];
					int n = in.read(buf);
					if (n > 0) {
						received = new String(buf, 0, n, StandardCharsets.US_ASCII);
					}
					out.write(reply.getBytes(StandardCharsets.US_ASCII));
					out.flush();
				}
				catch (IOException ignore) {
					// server closed / client timeout
				}
			}, "fake-clamd-acceptor");
			this.acceptor.setDaemon(true);
			this.acceptor.start();
		}

		static FakeClamd startReplying(String reply) throws IOException {
			return new FakeClamd(reply);
		}

		int port() {
			return server.getLocalPort();
		}

		String receivedCommand() {
			try {
				acceptor.join(1000);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return received;
		}

		@Override
		public void close() throws IOException {
			server.close();
		}

	}

	// --- helpers --------------------------------------------------------------

	private void assertWarnLogContains(String fragment) {
		assertThat(allWarnMessages()).as("expected WARN message containing: " + fragment)
			.anyMatch(m -> m.contains(fragment));
	}

	private List<String> allWarnMessages() {
		return appender.list.stream()
			.filter(e -> e.getLevel() == Level.WARN)
			.map(ILoggingEvent::getFormattedMessage)
			.toList();
	}

}
