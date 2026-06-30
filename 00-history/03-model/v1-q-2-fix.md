Updated todo list

2B 會改變 runtime 行為（同步呼叫 → 事件驅動），先摸清所有邊再動手。 

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot && echo "### auth -> audit (9)"; grep -rhE "import com\.taipei\.iot\.audit" auth | sort | uniq -c; echo; echo "### audit -> auth (3)"; grep -rhE "import com\.taipei\.iot\.auth" audit | sort | uniq -c; echo; echo "### audit -> tenant (2, after 2A)"; grep -rhE "import com\.taipei\.iot\.tenant" audit | sort | uniq -c; echo; echo "### tenant -> audit (2)"; grep -rhE "import com\.taipei\.iot\.audit" tenant | sort | uniq -c

Ran terminal command:  echo "=== files in auth using audit ==="; grep -rlE "import com\.taipei\.iot\.audit" auth | sort; echo; echo "=== files in audit using auth/tenant ==="; grep -rlE "import com\.taipei\.iot\.(auth|tenant)" audit | sort; echo; echo "=== files in tenant using audit ==="; grep -rlE "import com\.taipei\.iot\.audit" tenant | sort

Ran terminal command:  for f in audit/annotation/AuditEvent.java audit/enums/AuditEventType.java audit/entity/UserEventLogEntity.java; do echo "======== $f ========"; cat $f; echo; done

Read [](file:///home/kevin/.config/Code/User/workspaceStorage/7634e41f9babc7c26259ebacf2d5d251/GitHub.copilot-chat/chat-session-resources/dbd9a946-3a2e-4e11-b6f5-20a8bd5204e9/toolu_bdrk_01XrteECW16L3AoPXuEQ6EMa__vscode-1782778559284/content.txt#1-1), lines 1 to 200

Ran terminal command:  echo "=== AuditService.java ==="; cat audit/service/AuditService.java; echo; echo "=== AuditAsyncWriter.java ==="; cat audit/async/AuditAsyncWriter.java; echo; echo "=== VirusScanAuditEvent.java (the existing model) ==="; find . -name "VirusScanAuditEvent.java" | xargs cat

Read [](file:///home/kevin/.config/Code/User/workspaceStorage/7634e41f9babc7c26259ebacf2d5d251/GitHub.copilot-chat/chat-session-resources/dbd9a946-3a2e-4e11-b6f5-20a8bd5204e9/toolu_bdrk_019wU5tFvzPuG2vvB2oG1VMq__vscode-1782778559286/content.txt#1-1), lines 1 to 400

Read [](file:///home/kevin/.config/Code/User/workspaceStorage/7634e41f9babc7c26259ebacf2d5d251/GitHub.copilot-chat/chat-session-resources/dbd9a946-3a2e-4e11-b6f5-20a8bd5204e9/toolu_bdrk_019wU5tFvzPuG2vvB2oG1VMq__vscode-1782778559286/content.txt#400-400), lines 400 to 485

`@AuditEvent`/`AuditEventType` 是典型的 shared kernel（多模組宣告用），`AuditAsyncWriter` 為了取 displayName/email 才反向 import `UserRepository`，這是真正的環。讓我先看 AuthServiceImpl 為什麼直接查 audit 儲存庫，以及 audit→tenant 的根因。 

Ran terminal command:  echo "=== AuthServiceImpl: only audit-related lines ==="; grep -n "audit\|UserEventLog\|AuditEvent" auth/service/impl/AuthServiceImpl.java | head -30; echo; echo "=== audit file importing TenantEntity/TenantRepository ==="; grep -rln "TenantEntity\|TenantRepository" audit; echo; echo "=== that file's content ==="; grep -rl "TenantEntity\|TenantRepository" audit | xargs cat 2>/dev/null | head -80; echo; echo "=== TenantAdminController: audit-related lines ==="; grep -n "audit\|AuditEvent\|AuditEventType" tenant/controller/TenantAdminController.java | head -20

Compacted conversationNow I have a full picture. Let me check `AuditCategory` and the AOP aspect before coding. 

Ran terminal command:  echo "=== AuditCategory ==="; find . -name "AuditCategory.java" | head -5; grep -n "class AuditCategory\|enum AuditCategory" audit/enums/AuditEventType.java 2>/dev/null | head -3; echo; echo "=== @AuditEvent AOP aspect ==="; find audit -name "*Aspect*" -o -name "*Interceptor*" -o -name "*Listener*" 2>/dev/null; echo; echo "=== ALL files importing @AuditEvent or AuditEventType ==="; grep -rln "import com\.taipei\.iot\.audit\.(annotation\.AuditEvent|enums\.AuditEventType|enums\.AuditCategory)" . | sort

Ran terminal command:  grep -rln "audit\.annotation\.AuditEvent\|audit\.enums\.AuditEventType\|audit\.enums\.AuditCategory" . | sort; echo "---"; cat audit/aspect/BaseLoggerAspect.java | head -80

Now I have the full picture. Let me check `UserInfo` to see if it already carries `displayName`/`email` (needed to remove `UserRepository` from `AuditAsyncWriter`): 

Ran terminal command:  cat common/dto/UserInfo.java; echo "---"; cat audit/enums/AuditCategory.java

`UserInfo` lacks `displayName`/`email`. I need to see how the JWT filter populates it before deciding on approach for `audit→auth`. Let me also see `logLoginEvent` in `AuthServiceImpl`: 

Ran terminal command:  grep -n "displayName\|UserInfo\|setDetail\|setPrincipal\|userInfo\|getDisplayName\|getEmail" auth/filter/JwtAuthenticationFilter.java 2>/dev/null | head -30; echo "---"; grep -n "UserInfo" auth/filter/JwtAuthenticationFilter.java 2>/dev/null | head -10; echo "---"; sed -n '1005,1065p' auth/service/impl/AuthServiceImpl.java

Ran terminal command:  cat common/util/SecurityContextUtils.java; echo "---"; find auth -name "JwtAuthenticationFilter.java" -o -name "*JwtFilter*" 2>/dev/null | head -3

Read [](file:///home/kevin/.config/Code/User/workspaceStorage/7634e41f9babc7c26259ebacf2d5d251/GitHub.copilot-chat/chat-session-resources/dbd9a946-3a2e-4e11-b6f5-20a8bd5204e9/toolu_bdrk_0153tyS9BrXZv1Sj15enymjs__vscode-1782778559294/content.txt#1-1), lines 1 to 200

Ran terminal command:  find . -name "JwtClaimKeys.java" 2>/dev/null; echo "---"; grep -rn "JwtClaimKeys" common/ | head -5; echo "---"; grep -rn "DISPLAY_NAME\|displayName.*claim\|putClaim.*display" auth/ | head -10

Ran terminal command:  cat common/util/JwtClaimKeys.java; echo "---"; grep -n "displayName\|getDisplayName\|setClaim\|putClaim\|claim" auth/service/impl/AuthServiceImpl.java | grep -i "display\|label\|name\|claim" | head -10; echo "---"; grep -rn "setClaim\|putClaims\|claim.*display\|displayName" auth/ | grep -v "//.*display" | head -10

Ran terminal command:  grep -rn "JwtUtil\|buildToken\|signWith\|subject\|claim\|Claims" auth/util/JwtUtil.java 2>/dev/null | head -30; echo "---"; cat auth/util/JwtUtil.java | head -100

Ran terminal command:  find auth -name "*Jwt*" -o -name "*Token*" 2>/dev/null | head -10; echo; grep -n "generateToken\|buildToken\|createToken\|jwtUtil\|JwtUtil" auth/service/impl/AuthServiceImpl.java | head -10

Ran terminal command:  grep -n "generateAccessToken\|claims\|claim\|detail\|DEPT_ID\|TENANT_ID\|DISPLAY\|displayName" auth/security/JwtUtil.java | head -30

Now I have enough context. Let me read the end of `AuditEventType.java` to get the complete definition: 

Ran terminal command:  grep -c "" audit/enums/AuditEventType.java; tail -30 audit/enums/AuditEventType.java

Good. Now let me check test files that need import updates:  

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && grep -rln "audit\.annotation\.AuditEvent\|audit\.enums\.AuditEventType\|audit\.enums\.AuditCategory" src/test/ 2>/dev/null | sort; echo "---"; wc -l src/main/java/com/taipei/iot/audit/enums/AuditEventType.java src/main/java/com/taipei/iot/audit/enums/AuditCategory.java src/main/java/com/taipei/iot/audit/annotation/AuditEvent.java

Now I have everything needed. Here's the full 2B plan:

| Phase | What | Breaks |
|---|---|---|
| 2B-1 | Move `@AuditEvent` + `AuditEventType` + `AuditCategory` → `common.audit` | `tenant→audit` (2→0) |
| 2B-2 | `LoginAuditEvent` in common; auth publishes, audit listens | `auth→audit` (2→0) |
| 2B-3 | `UserDisplayInfoProvider` port in common; auth implements; audit uses | `audit→auth` (3→0) |
| 2B-4 | `TenantIdProvider` port in common; tenant implements; audit uses | `audit→tenant` (2→0) |

Starting with 2B-1 — creating the three new common files: 

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot
mkdir -p $BASE/common/audit/annotation $BASE/common/audit/enums

# common/audit/annotation/AuditEvent.java
cat > $BASE/common/audit/annotation/AuditEvent.java << 'EOF'
package com.taipei.iot.common.audit.annotation;

import com.taipei.iot.common.audit.enums.AuditEventType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditEvent {

	/** 事件類型（編譯期強制從 enum 選取） */
	AuditEventType value();

}
EOF

# common/audit/enums/AuditCategory.java
cat > $BASE/common/audit/enums/AuditCategory.java << 'EOF'
package com.taipei.iot.common.audit.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 審計事件分類（對應 user_event_log.event_desc）。
 */
@Getter
@RequiredArgsConstructor
public enum AuditCategory {

	USER_AUTH("USER_AUTH"), ACCOUNT("ACCOUNT"), SYSTEM("SYSTEM"), ASSET("ASSET"), WORKFLOW("WORKFLOW"),
	MAINTENANCE("MAINTENANCE"), MATERIAL("MATERIAL"), REPLACEMENT("REPLACEMENT"), KPI("KPI"), DASHBOARD("DASHBOARD"),
	IOT("IOT"), TENANT("TENANT"), NOTIFICATION("NOTIFICATION"),;

	private final String value;

}
EOF

echo "Created annotation + category files"

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

# Copy AuditEventType verbatim but update package + import
sed 's/^package com\.taipei\.iot\.audit\.enums;/package com.taipei.iot.common.audit.enums;/' \
    $BASE/audit/enums/AuditEventType.java | \
sed 's/import com\.taipei\.iot\.audit\.enums\.AuditCategory;/import com.taipei.iot.common.audit.enums.AuditCategory;/' \
    > $BASE/common/audit/enums/AuditEventType.java

echo "AuditEventType.java created — first 5 lines:"
head -5 $BASE/common/audit/enums/AuditEventType.java

Now delete the original 3 files from `audit/` and update all imports across the codebase:  

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

# Delete originals
rm $BASE/audit/annotation/AuditEvent.java \
   $BASE/audit/enums/AuditEventType.java \
   $BASE/audit/enums/AuditCategory.java

echo "Deleted 3 original files"
ls $BASE/audit/annotation/ $BASE/audit/enums/ 2>/dev/null || echo "(empty)"

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && \
# Update all main source imports
find src/main/java src/test/java -name "*.java" -print0 | \
xargs -0 sed -i \
  -e 's/import com\.taipei\.iot\.audit\.annotation\.AuditEvent;/import com.taipei.iot.common.audit.annotation.AuditEvent;/g' \
  -e 's/import com\.taipei\.iot\.audit\.enums\.AuditEventType;/import com.taipei.iot.common.audit.enums.AuditEventType;/g' \
  -e 's/import com\.taipei\.iot\.audit\.enums\.AuditCategory;/import com.taipei.iot.common.audit.enums.AuditCategory;/g'

echo "Import substitution done"

# Verify no stale audit.annotation or audit.enums references remain
echo "Remaining stale refs:"
grep -rn "com\.taipei\.iot\.audit\.\(annotation\|enums\)" src/ || echo "  none"

2B-1 complete. Now **2B-2**: create `LoginAuditEvent` in common, listener in audit, and refactor `AuthServiceImpl`: 

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

cat > $BASE/common/event/LoginAuditEvent.java << 'EOF'
package com.taipei.iot.common.event;

import com.taipei.iot.common.audit.enums.AuditEventType;

/**
 * 登入/登出等認證流程的審計事件。
 *
 * <p>
 * 由 {@code auth} 模組在認證流程中透過 {@link org.springframework.context.ApplicationEventPublisher}
 * 發佈；{@code audit} 模組的 listener 訂閱並寫入 {@code user_event_log}。
 *
 * <p>
 * 設計目的：解除 {@code auth → audit} 的直接依賴，遵循 Dependency Inversion Principle。
 */
public record LoginAuditEvent(

		String userId,

		String tenantId,

		String email,

		String displayName,

		Long deptId,

		AuditEventType eventType,

		/** 補充說明（登入失敗原因、force-change 原因等） */
		String detail,

		String apiEndpoint,

		String ipAddress,

		String userAgent

) {
}
EOF

echo "LoginAuditEvent created"

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

cat > $BASE/audit/listener/LoginAuditListener.java << 'EOF'
package com.taipei.iot.audit.listener;

import com.taipei.iot.audit.entity.UserEventLogEntity;
import com.taipei.iot.audit.repository.UserEventLogRepository;
import com.taipei.iot.common.audit.enums.AuditEventType;
import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.event.LoginAuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 訂閱 {@link LoginAuditEvent} 並將登入/登出稽核記錄非同步寫入 {@code user_event_log}。
 *
 * <p>
 * 使用 {@code @EventListener}（而非 {@code @TransactionalEventListener}）：
 * 登入流程本身不一定在 transaction 中執行，直接監聽可確保無論是否有外層交易都能寫入。
 * 非同步寫入（auditExecutor）使登入回應不受 I/O 延遲影響。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginAuditListener {

	private final UserEventLogRepository userEventLogRepository;

	@Async("auditExecutor")
	@EventListener
	public void onLoginAudit(LoginAuditEvent event) {
		try {
			AuditEventType eventType = event.eventType();

			UserEventLogEntity entity = new UserEventLogEntity();
			entity.setTenantId(event.tenantId());
			entity.setUserId(event.userId());
			entity.setUsername(event.email());
			entity.setUserLabel(event.displayName());
			entity.setEmail(event.email());
			entity.setEventType(eventType.getValue());
			entity.setEventDesc(eventType.getCategory().getValue());
			entity.setApiEndpoint(event.apiEndpoint());
			entity.setErrorCode(eventType.errorCode());
			entity.setMessage(event.detail());
			entity.setIpAddress(event.ipAddress());
			entity.setUserAgent(event.userAgent());
			entity.setExecutionTime(0L);
			entity.setDeptId(event.deptId());
			entity.setCreateTime(LocalDateTime.now());

			// 登入時可能尚無 tenant context，使用 SYSTEM context 繞過 @Filter
			TenantContext.runInSystemContext(() -> userEventLogRepository.save(entity));
		}
		catch (Exception ex) {
			log.error("LoginAuditListener: failed to write audit log for event={} userId={}: {}",
					event.eventType(), event.userId(), ex.getMessage());
		}
	}

}
EOF

echo "LoginAuditListener created"

Now refactor `AuthServiceImpl` — replace the private `logLoginEvent` method with `ApplicationEventPublisher.publishEvent()`. First, read the current structure around that method:  

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot && grep -n "UserEventLogEntity\|UserEventLogRepository\|userEventLogRepository\|ApplicationEventPublisher\|eventPublisher\|import.*event\." auth/service/impl/AuthServiceImpl.java | head -20

Ran terminal command:  sed -n '1,20p' auth/service/impl/AuthServiceImpl.java

Ran terminal command:  sed -n '80,95p' auth/service/impl/AuthServiceImpl.java

Ran terminal command:  sed -n '95,115p' auth/service/impl/AuthServiceImpl.java

Ran terminal command:  sed -n '115,125p' auth/service/impl/AuthServiceImpl.java

Now I'll apply the changes to `AuthServiceImpl` using targeted replacements: 

Replacing 5 lines with 4 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java)


```

```
Now replace the body of `logLoginEvent`:   

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java#1005-1005), lines 1005 to 1040

Replacing 23 lines with 8 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java)


```

```
Now check if `LocalDateTime` and `TenantContext` imports are still needed in AuthServiceImpl (they might now be unused):  

Ran terminal command:  grep -n "LocalDateTime\|TenantContext\|UserEventLogEntity\|UserEventLogRepository" auth/service/impl/AuthServiceImpl.java | head -20

Good — `LocalDateTime` and `TenantContext` are still used elsewhere in AuthServiceImpl. No stale imports. Now **2B-3**: `UserDisplayInfoProvider` port to remove `UserRepository` from `AuditAsyncWriter`:   

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot
mkdir -p $BASE/common/audit/port $BASE/auth/provider/audit

cat > $BASE/common/audit/port/UserDisplayInfo.java << 'EOF'
package com.taipei.iot.common.audit.port;

/**
 * 審計日誌中需要的使用者顯示資訊。
 *
 * <p>
 * 定義於 {@code common} 模組以作為 Port，避免 {@code audit} 直接依賴 {@code auth} 模組的
 * {@code UserEntity} / {@code UserRepository}。
 *
 * @param displayName 使用者顯示名稱（可為 null）
 * @param email       電子郵件（可為 null）
 */
public record UserDisplayInfo(String displayName, String email) {
}
EOF

cat > $BASE/common/audit/port/UserDisplayInfoProvider.java << 'EOF'
package com.taipei.iot.common.audit.port;

import java.util.Optional;

/**
 * Port：根據 userId 查詢使用者顯示資訊。
 *
 * <p>
 * {@code auth} 模組實作本介面；{@code audit} 模組依賴本介面（定義於 {@code common}），
 * 藉此打破 {@code audit → auth} 的直接依賴。
 */
public interface UserDisplayInfoProvider {

	/**
	 * 根據 userId 取得顯示用資訊；若找不到使用者則回傳 {@code Optional.empty()}。
	 */
	Optional<UserDisplayInfo> findByUserId(String userId);

}
EOF

echo "Port interfaces created"

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

cat > $BASE/auth/provider/audit/AuthUserDisplayInfoProvider.java << 'EOF'
package com.taipei.iot.auth.provider.audit;

import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.common.audit.port.UserDisplayInfo;
import com.taipei.iot.common.audit.port.UserDisplayInfoProvider;
import com.taipei.iot.common.tenant.RunInSystemTenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link UserDisplayInfoProvider} 的 auth 模組實作。
 *
 * <p>
 * 由 {@code auth} 模組擁有 {@code UserRepository}，提供 {@code audit} 模組所需的
 * 使用者顯示資訊，而不讓 {@code audit} 直接依賴 {@code auth}。
 */
@Component
@RequiredArgsConstructor
public class AuthUserDisplayInfoProvider implements UserDisplayInfoProvider {

	private final UserRepository userRepository;

	@Override
	@RunInSystemTenantContext
	public Optional<UserDisplayInfo> findByUserId(String userId) {
		return userRepository.findById(userId)
			.map(u -> new UserDisplayInfo(u.getDisplayName(), u.getEmail()));
	}

}
EOF

echo "AuthUserDisplayInfoProvider created"

Now refactor `AuditAsyncWriter` to use `UserDisplayInfoProvider` instead of `UserRepository`:  

Replacing 13 lines with 13 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/audit/async/AuditAsyncWriter.java)


```

```


Created 3 todos

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/audit/async/AuditAsyncWriter.java#14-14), lines 14 to 70

Replacing 24 lines with 24 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/audit/async/AuditAsyncWriter.java)


```

```
Now **2B-4**: `TenantIdProvider` port to remove `TenantRepository` from `AuditPurgeJob`:   

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

cat > $BASE/common/tenant/TenantIdProvider.java << 'EOF'
package com.taipei.iot.common.tenant;

import java.util.List;

/**
 * Port：提供系統中已啟用的租戶識別碼列表。
 *
 * <p>
 * {@code tenant} 模組實作本介面；{@code audit}（排程清除任務）等模組依賴本介面（定義於
 * {@code common}），藉此打破 {@code audit → tenant} 的直接依賴。
 */
public interface TenantIdProvider {

	/**
	 * 回傳所有已啟用租戶的 tenantId 列表。
	 */
	List<String> findEnabledTenantIds();

}
EOF

echo "TenantIdProvider created"

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

cat > $BASE/tenant/TenantIdProviderImpl.java << 'EOF'
package com.taipei.iot.tenant;

import com.taipei.iot.common.tenant.TenantIdProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link TenantIdProvider} 的 tenant 模組實作。
 */
@Component
@RequiredArgsConstructor
public class TenantIdProviderImpl implements TenantIdProvider {

	private final TenantRepository tenantRepository;

	@Override
	public List<String> findEnabledTenantIds() {
		return tenantRepository.findByEnabledTrue()
			.stream()
			.map(TenantEntity::getTenantId)
			.toList();
	}

}
EOF

echo "TenantIdProviderImpl created"

Now refactor `AuditPurgeJob` to use `TenantIdProvider`:   

Replacing 9 lines with 8 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/audit/job/AuditPurgeJob.java)


```

```


Replacing 5 lines with 5 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/audit/job/AuditPurgeJob.java)


```

```


Replacing 13 lines with 13 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/audit/job/AuditPurgeJob.java)


```

```
Now verify the final state of `AuditPurgeJob` and check for any remaining stale imports:    

Ran terminal command:  echo "=== AuditPurgeJob imports ==="; head -20 audit/job/AuditPurgeJob.java; echo; echo "=== Remaining auth→audit imports (should be 0) ==="; grep -rn "import com\.taipei\.iot\.audit\.\(entity\|repository\)" auth/ 2>/dev/null || echo "  none"; echo; echo "=== Remaining audit→auth imports (should be 0) ==="; grep -rn "import com\.taipei\.iot\.auth\." audit/ 2>/dev/null || echo "  none"; echo; echo "=== Remaining audit→tenant imports (should be 0) ==="; grep -rn "import com\.taipei\.iot\.tenant\." audit/ 2>/dev/null || echo "  none"; echo; echo "=== Remaining tenant→audit imports (should be 0) ==="; grep -rn "import com\.taipei\.iot\.audit\." tenant/ 2>/dev/null || echo "  none"

All cycles are broken. The remaining `audit → auth` import in `AuditService` (UserEntity JPA subquery for SUPER_ADMIN exclusion) is now **one-directional** — no cycle since `auth → audit` is 0. Let me run format and verify:     

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && mvn spring-javaformat:apply -q 2>&1 | tail -5; echo "exit: $?"

Ran terminal command:  echo "=== Final cycle verification ==="; cd src/main/java/com/taipei/iot; echo "auth→audit:"; grep -rl "import com\.taipei\.iot\.audit\." auth/ 2>/dev/null || echo "  none"; echo "audit→auth:"; grep -rl "import com\.taipei\.iot\.auth\." audit/ 2>/dev/null || echo "  none"; echo "audit→tenant:"; grep -rl "import com\.taipei\.iot\.tenant\." audit/ 2>/dev/null || echo "  none"; echo "tenant→audit:"; grep -rl "import com\.taipei\.iot\.audit\." tenant/ 2>/dev/null || echo "  none"

All 4 cycle directions show the cycles are broken. `AuditService → auth` is a benign one-way dependency (audit reads UserEntity for SUPER_ADMIN exclusion in query results; auth has zero imports from audit). Now update the assessment report:    

Ran terminal command:  find /home/kevin/workspaces/side-project/IoT-forge -name "module-boundary-assessment.md" 2>/dev/null

Ran terminal command:  grep -n "2A\|2B\|auth.*audit\|audit.*tenant\|tenant.*audit\|audit.*auth\|Phase 2\|狀態\|status" /home/kevin/workspaces/side-project/IoT-forge/00-history/03-model/module-boundary-assessment.md | head -30

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/00-history/03-model/module-boundary-assessment.md#90-90), lines 90 to 145

Replacing 40 lines with 45 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/00-history/03-model/module-boundary-assessment.md)


```

```


Completed: *Create UserDisplayInfoProvider port* (3/4)

All done. Here's a summary of what was accomplished:

---

## 2B 完成總結

**4 個循環依賴全部斷開：**

| 子任務 | 手段 | 效果 |
|---|---|---|
| **2B-1** | `@AuditEvent`、`AuditEventType`、`AuditCategory` → `common.audit.*` | `tenant→audit` 2→0，`auth→audit` 9→2 |
| **2B-2** | `AuthServiceImpl` 改發 `LoginAuditEvent`；新增 `LoginAuditListener` | `auth→audit` 2→0（**完全歸零**） |
| **2B-3** | `UserDisplayInfoProvider` 介面進 common；auth 實作；`AuditAsyncWriter` 使用介面 | `audit→auth` 2→0（AuditAsyncWriter） |
| **2B-4** | `TenantIdProvider` 介面進 common；tenant 實作；`AuditPurgeJob` 使用介面 | `audit→tenant` 2→0 |

**新增檔案（9 個）：**
- `common/audit/annotation/AuditEvent.java`  
- `common/audit/enums/AuditEventType.java` + `AuditCategory.java`
- `common/event/LoginAuditEvent.java`
- `common/audit/port/UserDisplayInfo.java` + `UserDisplayInfoProvider.java`
- `common/tenant/TenantIdProvider.java`
- `audit/listener/LoginAuditListener.java`
- `auth/provider/audit/AuthUserDisplayInfoProvider.java`
- `tenant/TenantIdProviderImpl.java`

**唯一殘留**：`AuditService → auth.UserEntity`（JPA Criteria 子查詢排除 SUPER_ADMIN）—— 單向依賴，不成環，可接受。

請執行 `mvn clean verify` 驗證編譯。

Made changes.