package com.taipei.iot.common.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * N-7：驗證 {@link LocalFileStorageService} 在 POSIX 檔案系統上會將 上傳檔案 / 子目錄 / 根目錄的權限收緊到 0600 /
 * 0700，避免依賴 作業系統 umask。
 */
@EnabledOnOs({ OS.LINUX, OS.MAC })
class LocalFileStorageServiceTest {

	@TempDir
	Path tempDir;

	private LocalFileStorageService service;

	@BeforeEach
	void setUp() {
		service = new LocalFileStorageService(tempDir.toString());
	}

	@Test
	void constructor_appliesRwxPermissionsToRootDir() throws Exception {
		Set<PosixFilePermission> perms = Files.getPosixFilePermissions(tempDir);

		assertThat(perms).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
				PosixFilePermission.OWNER_EXECUTE);
	}

	@Test
	void store_appliesRwPermissionsToStoredFile() throws Exception {
		byte[] content = "hello".getBytes();
		String relative = service.store("docs", "hello.txt", new ByteArrayInputStream(content));

		Path stored = tempDir.resolve(relative);
		assertThat(Files.exists(stored)).isTrue();

		Set<PosixFilePermission> perms = Files.getPosixFilePermissions(stored);
		assertThat(perms).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
		// 明確驗證 group / others 完全沒有任何權限
		assertThat(perms).doesNotContain(PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
				PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE,
				PosixFilePermission.OTHERS_EXECUTE);
	}

	@Test
	void store_appliesRwxPermissionsToCreatedSubDir() throws Exception {
		service.store("nested/sub", "x.txt", new ByteArrayInputStream(new byte[] { 1 }));

		Path subDir = tempDir.resolve("nested").resolve("sub");
		assertThat(Files.isDirectory(subDir)).isTrue();

		Set<PosixFilePermission> perms = Files.getPosixFilePermissions(subDir);
		assertThat(perms).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
				PosixFilePermission.OWNER_EXECUTE);
		assertThat(perms).doesNotContain(PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ);
	}

	@Test
	void store_overwriteRetainsRestrictivePermissions() throws Exception {
		// 第一次寫入
		String relative = service.store("docs", "same.txt", new ByteArrayInputStream("v1".getBytes()));
		Path stored = tempDir.resolve(relative);

		// 模擬被外力放寬權限後再次寫入同子目錄
		Files.setPosixFilePermissions(stored, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
				PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ));

		// 寫入第二個檔案（不同 UUID 前綴，但同 subDir）
		String relative2 = service.store("docs", "same.txt", new ByteArrayInputStream("v2".getBytes()));
		Path stored2 = tempDir.resolve(relative2);

		// 新檔案必須是 0600
		Set<PosixFilePermission> perms = Files.getPosixFilePermissions(stored2);
		assertThat(perms).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
	}

	// ─── F-6: deleteIfExists ─────────────────────────────────────────────────

	@Test
	void deleteIfExists_returnsTrueWhenFileExisted() throws Exception {
		String relative = service.store("docs", "to-delete.txt", new ByteArrayInputStream("bye".getBytes()));
		Path stored = tempDir.resolve(relative);
		assertThat(Files.exists(stored)).isTrue();

		boolean deleted = service.deleteIfExists(relative);

		assertThat(deleted).isTrue();
		assertThat(Files.exists(stored)).isFalse();
	}

	@Test
	void deleteIfExists_returnsFalseWhenFileMissing() {
		boolean deleted = service.deleteIfExists("docs/never-existed.txt");

		assertThat(deleted).isFalse();
	}

	@Test
	void deleteIfExists_isIdempotent() throws Exception {
		String relative = service.store("docs", "idem.txt", new ByteArrayInputStream("x".getBytes()));

		assertThat(service.deleteIfExists(relative)).isTrue();
		// 第二次呼叫不該丟錯，且回 false
		assertThat(service.deleteIfExists(relative)).isFalse();
		assertThat(service.deleteIfExists(relative)).isFalse();
	}

	@Test
	void deleteIfExists_rejectsPathTraversal() {
		assertThatThrownBy(() -> service.deleteIfExists("../../etc/passwd")).isInstanceOf(BusinessException.class)
			.hasMessageContaining("非法的檔案路徑");
	}

	// ─── F-6: move ───────────────────────────────────────────────────────────

	@Test
	void move_movesFileToNewSubDirAndReturnsNewRelativePath() throws Exception {
		String original = service.store("temp", "draft.txt", new ByteArrayInputStream("payload".getBytes()));
		Path originalAbs = tempDir.resolve(original);
		assertThat(Files.exists(originalAbs)).isTrue();

		String moved = service.move(original, "permanent/123", "final.txt");

		Path movedAbs = tempDir.resolve(moved);
		assertThat(moved).startsWith("permanent/123/").endsWith("_final.txt");
		assertThat(Files.exists(movedAbs)).isTrue();
		assertThat(Files.exists(originalAbs)).isFalse();
		assertThat(Files.readAllBytes(movedAbs)).isEqualTo("payload".getBytes());
	}

	@Test
	void move_appliesRestrictivePermissionsToMovedFileAndSubDir() throws Exception {
		String original = service.store("temp", "p.txt", new ByteArrayInputStream("p".getBytes()));

		String moved = service.move(original, "permanent/perm-check", "p.txt");
		Path movedAbs = tempDir.resolve(moved);
		Path movedDir = movedAbs.getParent();

		assertThat(Files.getPosixFilePermissions(movedAbs)).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ,
				PosixFilePermission.OWNER_WRITE);
		assertThat(Files.getPosixFilePermissions(movedDir)).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ,
				PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
	}

	@Test
	void move_throwsWhenSourceMissing() {
		assertThatThrownBy(() -> service.move("temp/not-there.txt", "permanent", "x.txt"))
			.isInstanceOf(BusinessException.class)
			.satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.ATTACHMENT_NOT_FOUND));
	}

	@Test
	void move_rejectsSourcePathTraversal() throws Exception {
		// 即使建立了一個真實檔案，traversal 路徑也必須被拒
		service.store("docs", "real.txt", new ByteArrayInputStream("r".getBytes()));

		assertThatThrownBy(() -> service.move("../escape.txt", "permanent", "x.txt"))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("非法的檔案路徑");
	}

	@Test
	void move_rejectsTargetSubDirTraversal() throws Exception {
		String original = service.store("temp", "p.txt", new ByteArrayInputStream("p".getBytes()));

		assertThatThrownBy(() -> service.move(original, "../escape", "x.txt")).isInstanceOf(BusinessException.class)
			.hasMessageContaining("非法的儲存路徑");
	}

	@Test
	void move_sanitizesNewFileName() throws Exception {
		String original = service.store("temp", "p.txt", new ByteArrayInputStream("p".getBytes()));

		String moved = service.move(original, "permanent", "../weird/name.txt");
		// sanitize 將 .. 與 / 全部替換為 _
		assertThat(moved).doesNotContain("..").doesNotContain("/weird/");
		Path movedAbs = tempDir.resolve(moved);
		assertThat(Files.exists(movedAbs)).isTrue();
	}

}
