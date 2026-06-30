Updated todo list

Let me first understand the full scope before touching any code: 

Ran terminal command:  echo "=== auth/entity ==="; ls auth/entity/; echo; echo "=== auth/repository ==="; ls auth/repository/; echo; echo "=== user/entity ==="; ls user/entity/ 2>/dev/null || echo "(none)"; echo; echo "=== user/repository ==="; ls user/repository/ 2>/dev/null || echo "(none)"

Ran terminal command:  echo "=== What does user module import from auth? ==="; grep -rln "import com\.taipei\.iot\.auth\." user/ | sort; echo; echo "=== What does auth import from user? ==="; grep -rln "import com\.taipei\.iot\.user\." auth/ | sort; echo; echo "=== Who else (besides user) imports from auth entity/repo? ==="; grep -rln "auth\.entity\.\|auth\.repository\." . | grep -v "^./auth/" | sort

Let me get the exact import details: 

Ran terminal command:  echo "=== user files and what auth.* they import ==="; for f in user/controller/UserSelfController.java user/dto/request/CreateUserRequest.java user/service/PasswordValidator.java user/service/UserAdminService.java user/service/UserSelfService.java; do echo "  $f:"; grep "import com\.taipei\.iot\.auth\." $f; done; echo; echo "=== auth/service/impl/AuthServiceImpl.java imports from user ==="; grep "import com\.taipei\.iot\.user\." auth/service/impl/AuthServiceImpl.java

Ran terminal command:  echo "=== all modules importing auth.entity.UserEntity ==="; grep -rln "auth\.entity\.UserEntity" . | grep -v "^./auth/" | sort; echo; echo "=== all modules importing auth.repository.UserRepository ==="; grep -rln "auth\.repository\.UserRepository" . | grep -v "^./auth/" | sort; echo; echo "=== RoleEntity usage outside auth ==="; grep -rln "auth\.entity\.RoleEntity\|auth\.repository\.RoleRepository" . | grep -v "^./auth/" | sort; echo; echo "=== UserTenantMappingEntity/Repository usage outside auth ==="; grep -rln "auth\.entity\.UserTenantMappingEntity\|auth\.repository\.UserTenantMappingRepository" . | grep -v "^./auth/" | sort; echo; echo "=== ChangePasswordLog usage outside auth ==="; grep -rln "auth\.entity\.ChangePasswordLogEntity\|auth\.repository\.ChangePasswordLogRepository" . | grep -v "^./auth/" | sort

This is a significant refactoring. Let me deeply understand the dependencies before planning:  

Ran terminal command:  echo "=== UserSelfController JwtUtil usage ==="; grep -n "JwtUtil\|jwtUtil" user/controller/UserSelfController.java | head -10; echo; echo "=== UserSelfService UserSessionService usage ==="; grep -n "UserSessionService\|userSessionService" user/service/UserSelfService.java | head -10; echo; echo "=== CreateUserRequest AuthType usage ==="; grep -n "AuthType" user/dto/request/CreateUserRequest.java | head -10; echo; echo "=== UserEntity fields (first 40 lines) ==="; head -60 auth/entity/UserEntity.java; echo; echo "=== RoleEntity fields ==="; head -40 auth/entity/RoleEntity.java

Ran terminal command:  echo "=== auth/provider/AuthType ==="; cat auth/provider/AuthType.java; echo; echo "=== UserTenantMappingEntity ==="; head -40 auth/entity/UserTenantMappingEntity.java; echo; echo "=== ChangePasswordLogEntity ==="; head -20 auth/entity/ChangePasswordLogEntity.java

This is a large refactoring. Let me fully map the blast radius before coding:  

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && echo "=== auth internal files using UserEntity/UserRepository ==="; grep -rln "auth\.entity\.UserEntity\|auth\.repository\.UserRepository\|UserEntity\|UserRepository" src/main/java/com/taipei/iot/auth/ | grep -v "UserEntity\.java\|UserRepository\.java" | head -30; echo; echo "=== count of auth files using UserEntity ==="; grep -rl "UserEntity" src/main/java/com/taipei/iot/auth/ | grep -v "UserEntity\.java" | wc -l; echo; echo "=== UserTenantMappingEntity usages outside auth ==="; grep -rln "UserTenantMappingEntity\|UserTenantMappingRepository" src/main/java/com/taipei/iot/ | grep -v "/auth/" | sort; echo; echo "=== RoleEntity usages outside auth ==="; grep -rln "RoleEntity\|RoleRepository" src/main/java/com/taipei/iot/ | grep -v "/auth/" | sort

Ran terminal command:  echo "=== test files using auth.entity.UserEntity or auth.repository.UserRepository ==="; grep -rln "auth\.entity\.UserEntity\|auth\.repository\.UserRepository" src/test/ | sort; echo; echo "=== auth/provider/AuthType usages everywhere ==="; grep -rln "auth\.provider\.AuthType\|AuthType" src/main/java/com/taipei/iot/ | sort | head -20; echo; echo "=== UserSelfService full auth imports ==="; grep "import.*auth\." src/main/java/com/taipei/iot/user/service/UserSelfService.java; echo; echo "=== UserSelfController full auth imports ==="; grep "import.*auth\." src/main/java/com/taipei/iot/user/controller/UserSelfController.java

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot && echo "=== PasswordPolicy/PasswordPolicyResolver location & usages ==="; find auth -name "PasswordPolicy.java" -o -name "PasswordPolicyResolver.java" | head; echo; grep -rln "PasswordPolicy\|PasswordPolicyResolver" . | grep -v "PasswordPolicy\.java\|PasswordPolicyResolver\.java" | head -20; echo; echo "=== UserSessionService usage in user ==="; grep -n "UserSessionService\|userSessionService" user/service/UserSelfService.java | head -10; echo; echo "=== JwtUtil usage in UserSelfController ==="; grep -n "jwtUtil\|JwtUtil\|parseToken" user/controller/UserSelfController.java | head -10; echo; echo "=== rbac module structure ==="; ls rbac/

Ran terminal command:  ls rbac/entity/ rbac/repository/; echo; echo "=== what rbac currently imports from auth ==="; grep -rln "import com\.taipei\.iot\.auth\." rbac/ | head -10; echo; grep "import.*auth\." rbac/service/RoleService.java | head -5; echo; echo "=== workflow auth imports ==="; grep "import.*auth\." workflow/service/OrgAssigneeResolver.java | head -5; echo; echo "=== PasswordPolicy usage in user module ==="; grep -n "PasswordPolicy\|PasswordPolicyResolver" user/service/PasswordValidator.java | head -10; grep -n "PasswordPolicy\|PasswordPolicyResolver" user/service/UserAdminService.java | head -10

Ran terminal command:  cat auth/policy/PasswordPolicy.java; echo "---"; head -30 auth/policy/PasswordPolicyResolver.java

Ran terminal command:  echo "=== user→tenant existing imports ==="; grep -rln "import com\.taipei\.iot\.tenant\." user/ | head -10; echo; echo "=== full UserTenantMappingEntity ==="; cat auth/entity/UserTenantMappingEntity.java | head -80; echo; echo "=== UserTenantMappingRepository ==="; cat auth/repository/UserTenantMappingRepository.java; echo; echo "=== UserRepository ==="; cat auth/repository/UserRepository.java

Read [](file:///home/kevin/.config/Code/User/workspaceStorage/7634e41f9babc7c26259ebacf2d5d251/GitHub.copilot-chat/chat-session-resources/dbd9a946-3a2e-4e11-b6f5-20a8bd5204e9/toolu_bdrk_019BaAMYgbhxyu9m5CZ5r7Zz__vscode-1782778559346/content.txt#1-1), lines 1 to 200

I now have the complete picture. Plan A scope:

| Move | From → To | Breaks |
|---|---|---|
| `UserEntity` + `UserRepository` | `auth` → `user` | Core inversion |
| `UserTenantMappingEntity/Repo` | `auth` → `user` | user→auth |
| `ChangePasswordLogEntity/Repo` | `auth` → `user` | user→auth |
| `RoleEntity` + `RoleRepository` | `auth` → `rbac` | user→auth, rbac→auth |
| `AuthType` | `auth.provider` → `common.enums` | user→auth, tenant→auth |
| `PasswordPolicy` | `auth.policy` → `common.policy` | user→auth, tenant→auth |
| Port: `PasswordPolicyProvider` | new in `common.auth.port` | user→auth (resolver) |
| Port: `SessionRevoker` | new in `common.auth.port` | user→auth (session) |
| Port: `TokenJtiReader` | new in `common.auth.port` | user→auth (JWT) |

Executing in order — starting with the moved value types:  

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

# 1. AuthType → common.enums
mkdir -p $BASE/common/enums
sed 's/^package com\.taipei\.iot\.auth\.provider;/package com.taipei.iot.common.enums;/' \
    $BASE/auth/provider/AuthType.java > $BASE/common/enums/AuthType.java
echo "AuthType.java created in common.enums"
head -3 $BASE/common/enums/AuthType.java

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

# 2. PasswordPolicy → common.policy
mkdir -p $BASE/common/policy
sed 's/^package com\.taipei\.iot\.auth\.policy;/package com.taipei.iot.common.policy;/' \
    $BASE/auth/policy/PasswordPolicy.java > $BASE/common/policy/PasswordPolicy.java
echo "PasswordPolicy.java created in common.policy"
head -3 $BASE/common/policy/PasswordPolicy.java

Now create the 3 port interfaces in `common.auth.port`:    

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot
mkdir -p $BASE/common/auth/port

cat > $BASE/common/auth/port/PasswordPolicyProvider.java << 'EOF'
package com.taipei.iot.common.auth.port;

import com.taipei.iot.common.policy.PasswordPolicy;

/**
 * Port：根據 tenantId 解析當前有效的密碼原則。
 *
 * <p>
 * {@code auth} 模組實作本介面；{@code user} 及 {@code tenant} 模組依賴本介面
 * （定義於 {@code common}），藉此打破 {@code user/tenant → auth.policy} 的直接依賴。
 */
public interface PasswordPolicyProvider {

	/**
	 * 解析並回傳指定租戶的有效密碼原則。
	 * @param tenantId 租戶 ID；傳入 {@code null} 時使用平台預設值
	 */
	PasswordPolicy resolve(String tenantId);

}
EOF

cat > $BASE/common/auth/port/SessionRevoker.java << 'EOF'
package com.taipei.iot.common.auth.port;

/**
 * Port：撤銷使用者的登入 Session。
 *
 * <p>
 * {@code auth} 模組實作本介面；{@code user} 模組在密碼變更後呼叫以強制其他裝置登出，
 * 而不需直接依賴 {@code auth.service.UserSessionService}。
 */
public interface SessionRevoker {

	/**
	 * 撤銷指定使用者除當前 Session 之外的所有有效 Session。
	 * @param userId       使用者 ID
	 * @param currentJti   當前 Session 的 JWT ID（保留此 session）
	 */
	void revokeAllExceptCurrent(String userId, String currentJti);

}
EOF

cat > $BASE/common/auth/port/TokenJtiReader.java << 'EOF'
package com.taipei.iot.common.auth.port;

/**
 * Port：從 JWT token 字串取得 JWT ID (jti)。
 *
 * <p>
 * {@code auth} 模組實作本介面；{@code user} 模組在使用者自助變更密碼流程中
 * 需要解析當前 refresh token 的 JTI，而不需直接依賴 {@code auth.security.JwtUtil}。
 */
public interface TokenJtiReader {

	/**
	 * 解析 token 字串並回傳其 JWT ID (jti)。
	 * @param token JWT token 字串
	 * @return JTI 字串
	 * @throws RuntimeException 若 token 無效或已過期
	 */
	String extractJti(String token);

}
EOF

echo "3 port interfaces created"

Now create the auth-side adapter implementations:   

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot
mkdir -p $BASE/auth/port

cat > $BASE/auth/port/AuthPasswordPolicyProvider.java << 'EOF'
package com.taipei.iot.auth.port;

import com.taipei.iot.auth.policy.PasswordPolicyResolver;
import com.taipei.iot.common.auth.port.PasswordPolicyProvider;
import com.taipei.iot.common.policy.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link PasswordPolicyProvider} 的 auth 模組實作，委派給 {@link PasswordPolicyResolver}。
 */
@Component
@RequiredArgsConstructor
public class AuthPasswordPolicyProvider implements PasswordPolicyProvider {

	private final PasswordPolicyResolver passwordPolicyResolver;

	@Override
	public PasswordPolicy resolve(String tenantId) {
		return passwordPolicyResolver.resolve(tenantId);
	}

}
EOF

cat > $BASE/auth/port/AuthSessionRevoker.java << 'EOF'
package com.taipei.iot.auth.port;

import com.taipei.iot.auth.service.UserSessionService;
import com.taipei.iot.common.auth.port.SessionRevoker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link SessionRevoker} 的 auth 模組實作，委派給 {@link UserSessionService}。
 */
@Component
@RequiredArgsConstructor
public class AuthSessionRevoker implements SessionRevoker {

	private final UserSessionService userSessionService;

	@Override
	public void revokeAllExceptCurrent(String userId, String currentJti) {
		userSessionService.revokeAllExceptCurrent(userId, currentJti);
	}

}
EOF

cat > $BASE/auth/port/AuthTokenJtiReader.java << 'EOF'
package com.taipei.iot.auth.port;

import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.auth.port.TokenJtiReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link TokenJtiReader} 的 auth 模組實作，委派給 {@link JwtUtil}。
 */
@Component
@RequiredArgsConstructor
public class AuthTokenJtiReader implements TokenJtiReader {

	private final JwtUtil jwtUtil;

	@Override
	public String extractJti(String token) {
		return jwtUtil.parseToken(token).getId();
	}

}
EOF

echo "3 auth port adapters created"

Now create the moved entity/repository files. Starting with `UserEntity` (needs `AuthType` updated):   

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

# UserEntity: change package + AuthType import
sed 's/^package com\.taipei\.iot\.auth\.entity;/package com.taipei.iot.user.entity;/' \
    $BASE/auth/entity/UserEntity.java | \
sed 's/import com\.taipei\.iot\.auth\.provider\.AuthType;/import com.taipei.iot.common.enums.AuthType;/' \
    > $BASE/user/entity/UserEntity.java

# UserRepository
sed 's/^package com\.taipei\.iot\.auth\.repository;/package com.taipei.iot.user.repository;/' \
    $BASE/auth/repository/UserRepository.java | \
sed 's/import com\.taipei\.iot\.auth\.entity\./import com.taipei.iot.user.entity./g' \
    > $BASE/user/repository/UserRepository.java

# ChangePasswordLogEntity
sed 's/^package com\.taipei\.iot\.auth\.entity;/package com.taipei.iot.user.entity;/' \
    $BASE/auth/entity/ChangePasswordLogEntity.java > $BASE/user/entity/ChangePasswordLogEntity.java

# ChangePasswordLogRepository
sed 's/^package com\.taipei\.iot\.auth\.repository;/package com.taipei.iot.user.repository;/' \
    $BASE/auth/repository/ChangePasswordLogRepository.java | \
sed 's/import com\.taipei\.iot\.auth\.entity\./import com.taipei.iot.user.entity./g' \
    > $BASE/user/repository/ChangePasswordLogRepository.java

echo "UserEntity, UserRepository, ChangePasswordLog files created"
head -3 $BASE/user/entity/UserEntity.java
head -3 $BASE/user/repository/UserRepository.java

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

# UserTenantMappingEntity: package change + update entity refs from auth.entity → user.entity
# BUT RoleEntity will move to rbac.entity, so we update that ref too
sed 's/^package com\.taipei\.iot\.auth\.entity;/package com.taipei.iot.user.entity;/' \
    $BASE/auth/entity/UserTenantMappingEntity.java | \
sed 's/import com\.taipei\.iot\.auth\.entity\.UserEntity;/import com.taipei.iot.user.entity.UserEntity;/' | \
sed 's/import com\.taipei\.iot\.auth\.entity\.RoleEntity;/import com.taipei.iot.rbac.entity.RoleEntity;/' \
    > $BASE/user/entity/UserTenantMappingEntity.java

# UserTenantMappingRepository: package change + entity ref
sed 's/^package com\.taipei\.iot\.auth\.repository;/package com.taipei.iot.user.repository;/' \
    $BASE/auth/repository/UserTenantMappingRepository.java | \
sed 's/import com\.taipei\.iot\.auth\.entity\./import com.taipei.iot.user.entity./g' \
    > $BASE/user/repository/UserTenantMappingRepository.java

echo "UserTenantMapping files created"
head -8 $BASE/user/entity/UserTenantMappingEntity.java

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

# RoleEntity → rbac.entity
sed 's/^package com\.taipei\.iot\.auth\.entity;/package com.taipei.iot.rbac.entity;/' \
    $BASE/auth/entity/RoleEntity.java > $BASE/rbac/entity/RoleEntity.java

# RoleRepository → rbac.repository
sed 's/^package com\.taipei\.iot\.auth\.repository;/package com.taipei.iot.rbac.repository;/' \
    $BASE/auth/repository/RoleRepository.java | \
sed 's/import com\.taipei\.iot\.auth\.entity\.RoleEntity;/import com.taipei.iot.rbac.entity.RoleEntity;/' \
    > $BASE/rbac/repository/RoleRepository.java

echo "RoleEntity, RoleRepository created in rbac"
head -3 $BASE/rbac/entity/RoleEntity.java
head -4 $BASE/rbac/repository/RoleRepository.java

Now delete the 8 original auth entity/repo files and AuthType/PasswordPolicy:    

Ran terminal command:  BASE=/home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot

rm $BASE/auth/entity/UserEntity.java \
   $BASE/auth/entity/UserTenantMappingEntity.java \
   $BASE/auth/entity/ChangePasswordLogEntity.java \
   $BASE/auth/entity/RoleEntity.java \
   $BASE/auth/repository/UserRepository.java \
   $BASE/auth/repository/UserTenantMappingRepository.java \
   $BASE/auth/repository/ChangePasswordLogRepository.java \
   $BASE/auth/repository/RoleRepository.java \
   $BASE/auth/provider/AuthType.java \
   $BASE/auth/policy/PasswordPolicy.java

echo "Deleted 10 original files"
echo "Remaining auth/entity:"; ls $BASE/auth/entity/
echo "Remaining auth/repository:"; ls $BASE/auth/repository/

Now the massive global import substitution across all Java files (main + test):    

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && \
find src/main/java src/test/java -name "*.java" -print0 | xargs -0 sed -i \
  -e 's|com\.taipei\.iot\.auth\.entity\.UserEntity|com.taipei.iot.user.entity.UserEntity|g' \
  -e 's|com\.taipei\.iot\.auth\.repository\.UserRepository|com.taipei.iot.user.repository.UserRepository|g' \
  -e 's|com\.taipei\.iot\.auth\.entity\.UserTenantMappingEntity|com.taipei.iot.user.entity.UserTenantMappingEntity|g' \
  -e 's|com\.taipei\.iot\.auth\.repository\.UserTenantMappingRepository|com.taipei.iot.user.repository.UserTenantMappingRepository|g' \
  -e 's|com\.taipei\.iot\.auth\.entity\.ChangePasswordLogEntity|com.taipei.iot.user.entity.ChangePasswordLogEntity|g' \
  -e 's|com\.taipei\.iot\.auth\.repository\.ChangePasswordLogRepository|com.taipei.iot.user.repository.ChangePasswordLogRepository|g' \
  -e 's|com\.taipei\.iot\.auth\.entity\.RoleEntity|com.taipei.iot.rbac.entity.RoleEntity|g' \
  -e 's|com\.taipei\.iot\.auth\.repository\.RoleRepository|com.taipei.iot.rbac.repository.RoleRepository|g' \
  -e 's|com\.taipei\.iot\.auth\.provider\.AuthType|com.taipei.iot.common.enums.AuthType|g' \
  -e 's|com\.taipei\.iot\.auth\.policy\.PasswordPolicy|com.taipei.iot.common.policy.PasswordPolicy|g'

echo "exit: $?"
echo "Verifying no stale references:"
grep -rn "auth\.entity\.UserEntity\|auth\.repository\.UserRepository\|auth\.entity\.RoleEntity\|auth\.repository\.RoleRepository\|auth\.provider\.AuthType\|auth\.policy\.PasswordPolicy" src/ | grep -v "\.class:" | head -10 || echo "  none"

Zero stale references. Now also handle `PasswordPolicyResolver` and the UserTenantMapping/ChangePasswordLog imports that might still reference old packages:    

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && \
grep -rn "auth\.entity\.UserTenantMapping\|auth\.repository\.UserTenantMapping\|auth\.entity\.ChangePassword\|auth\.repository\.ChangePassword" src/ | head -10 || echo "none"; \
echo "---"
# Also check for any remaining stale JPQL references using old package
grep -rn "FROM com\.taipei\.iot\.auth" src/ | head -5 || echo "no JPQL fqn refs"

Now update the `user` module's behavioral dependencies to use the port interfaces:   

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot && grep -n "UserSessionService\|userSessionService\|JwtUtil\|jwtUtil\|PasswordPolicyResolver\|passwordPolicyResolver" user/service/UserSelfService.java user/controller/UserSelfController.java user/service/PasswordValidator.java user/service/UserAdminService.java | head -30

The sed pattern for `PasswordPolicy` accidentally also mangled `PasswordPolicyResolver` imports (prefix match). Need to fix those:      

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && \
echo "=== Mangled PasswordPolicyResolver imports ==="; \
grep -rn "common\.policy\.PasswordPolicyResolver" src/ | head -20

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && \
# Fix PasswordPolicyResolver back to auth.policy (it didn't move)
find src/main/java src/test/java -name "*.java" -print0 | xargs -0 sed -i \
  -e 's|com\.taipei\.iot\.common\.policy\.PasswordPolicyResolver|com.taipei.iot.auth.policy.PasswordPolicyResolver|g'

echo "Fixed PasswordPolicyResolver imports"
grep -rn "PasswordPolicyResolver" src/ | grep -v "auth\.policy\.PasswordPolicyResolver\|//\|/\*\|\*" | head -10 || echo "all correct"

Good. Now update the 5 user-module files to use port interfaces instead of auth implementations. Let me read each to make precise edits:     

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot && sed -n '1,40p' user/service/PasswordValidator.java

Replacing 3 lines with 3 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/user/service/PasswordValidator.java)


```

```


Ran terminal command:  grep -n "import.*auth\.\|PasswordPolicyResolver\|passwordPolicyResolver" user/service/UserAdminService.java | head -15

Ran terminal command:  sed -n '65,75p' user/service/UserAdminService.java; echo "---"; sed -n '178,185p' user/service/UserAdminService.java

Replacing 1 lines with 1 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java)


```

```


Ran terminal command:  grep -n "import.*auth\.\|PasswordPolicyResolver" tenant/TenantConsistencyValidator.java | head -10

Ran terminal command:  grep -n "PasswordPolicy" tenant/TenantConsistencyValidator.java | head -10

Good — `TenantConsistencyValidator` only has a comment, not an actual import. Now update `UserSelfService` and `UserSelfController` to use the ports:       

Ran terminal command:  sed -n '1,45p' user/service/UserSelfService.java

Replacing 3 lines with 3 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/user/service/UserSelfService.java)


```

```


Created 7 todos

Ran terminal command:  sed -n '1,65p' user/controller/UserSelfController.java

Replacing 3 lines with 3 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/user/controller/UserSelfController.java)


```

```
Now fix `CreateUserRequest` — `AuthType` is already updated by global sed. Let me also check the `AuthenticationProvider` interface which references `UserEntity`:   

Ran terminal command:  echo "=== Final user→auth check ==="; grep -rln "import com\.taipei\.iot\.auth\." user/ | sort; echo; echo "=== auth→user imports now ==="; grep -rln "import com\.taipei\.iot\.user\." auth/ | sort | head -20

**`user → auth` = 0.** The cycle is fully broken. Now run format and do a final check on test files:     

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && echo "=== test files with remaining auth imports in user package ==="; grep -rln "import com\.taipei\.iot\.auth\." src/test/java/com/taipei/iot/user/ | sort; echo; echo "=== check for remaining auth.policy.PasswordPolicyResolver in user tests ==="; grep -rn "auth\.policy\." src/test/java/com/taipei/iot/user/ | head -10 || echo "none"; echo; echo "=== run spring-javaformat ==="; mvn spring-javaformat:apply -q 2>&1 | tail -3; echo "exit: $?"

Ran terminal command:  for f in src/test/java/com/taipei/iot/user/controller/UserAdminControllerTest.java src/test/java/com/taipei/iot/user/controller/UserSelfControllerTest.java src/test/java/com/taipei/iot/user/service/PasswordValidatorTest.java src/test/java/com/taipei/iot/user/service/UserSelfServiceTest.java; do echo "=== $f ==="; grep "import.*auth\." $f; done

Ran terminal command:  grep -n "JwtUtil\|jwtUtil\|JwtAuthFilter\|JwtAuthenticationFilter" src/test/java/com/taipei/iot/user/controller/UserAdminControllerTest.java | head -10; echo "---"; grep -n "JwtUtil\|jwtUtil\|tokenJtiReader" src/test/java/com/taipei/iot/user/controller/UserSelfControllerTest.java | head -10; echo "---"; grep -n "PasswordPolicyResolver\|policyResolver" src/test/java/com/taipei/iot/user/service/PasswordValidatorTest.java | head -10; echo "---"; grep -n "UserSessionService\|userSessionService\|sessionRevoker" src/test/java/com/taipei/iot/user/service/UserSelfServiceTest.java | head -10

Ran terminal command:  grep -n "JwtUtil\|JwtAuthenticationFilter" src/test/java/com/taipei/iot/user/controller/UserAdminControllerTest.java | head -15; echo; grep -n "WebMvcTest\|MockBean\|@Mock" src/test/java/com/taipei/iot/user/controller/UserAdminControllerTest.java | head -10

The `UserAdminControllerTest` mocks JwtUtil for security filter setup (unavoidable `@WebMvcTest` infrastructure). Fix the 3 service/self tests:    

Ran terminal command:  sed -n '1,45p' src/test/java/com/taipei/iot/user/service/PasswordValidatorTest.java

Compacted conversation

Ran terminal command:  sed -n '1,60p' src/test/java/com/taipei/iot/user/service/UserSelfServiceTest.java

Ran terminal command:  sed -n '1,75p' src/test/java/com/taipei/iot/user/controller/UserSelfControllerTest.java

Ran terminal command:  sed -n '75,160p' src/test/java/com/taipei/iot/user/controller/UserSelfControllerTest.java

The `JwtUtil` mock in `UserSelfControllerTest` is for the security filter (it stays). Need to add `TokenJtiReader` as an additional `@MockitoBean`, and fix the two service tests. 

Replacing 9 lines with 13 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/user/service/PasswordValidatorTest.java), [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/user/service/UserSelfServiceTest.java), [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/user/controller/UserSelfControllerTest.java)


```

```

```

```

```

```
Now check the `UserSelfServiceTest` for the verify calls that reference `userSessionService`: 

Ran terminal command:  grep -n "userSessionService\|sessionRevoker" src/test/java/com/taipei/iot/user/service/UserSelfServiceTest.java

Ran terminal command:  sed -i 's/verify(userSessionService)/verify(sessionRevoker)/g' src/test/java/com/taipei/iot/user/service/UserSelfServiceTest.java

Now run final format and verify: 

Ran terminal command:  mvn spring-javaformat:apply -q 2>&1 | tail -3; echo "format exit: $?"; echo; echo "=== Remaining user→auth in main ==="; grep -rln "import com\.taipei\.iot\.auth\." src/main/java/com/taipei/iot/user/ | sort; echo "=== Remaining user→auth in test ==="; grep -rln "import com\.taipei\.iot\.auth\." src/test/java/com/taipei/iot/user/ | sort

**Production code: `user → auth` = 0.** The two remaining test files are `@WebMvcTest` controller tests that import `JwtUtil` solely to mock the `JwtAuthenticationFilter` security infrastructure — this is unavoidable in Spring MVC slice tests that load `SecurityConfig.class`.

Summary of what was done to complete **問題三 Plan A**:

| Step | File | Change |
|---|---|---|
| Port | `user/controller/UserSelfController.java` | `JwtUtil` → `TokenJtiReader` |
| Test | `user/service/PasswordValidatorTest.java` | Mock type `PasswordPolicyResolver` → `PasswordPolicyProvider` |
| Test | `user/service/UserSelfServiceTest.java` | Mock type `UserSessionService` → `SessionRevoker`; `verify(userSessionService)` → `verify(sessionRevoker)` |
| Test | `user/controller/UserSelfControllerTest.java` | Added `@MockitoBean TokenJtiReader tokenJtiReader` |

**Final result:**
- `user → auth` (production): **0 imports** ✅
- `auth → user` (production): 7 files (one-way, correct) ✅
- Formatting: applied ✅

Completed: *Move AuthType to common.enums* (1/7)

Made changes.