Compacted conversation

Updated todo list

先讀關鍵檔案，了解實際耦合的呼叫形態，才能設計正確的解法。  

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/tenant/service/TenantAdminService.java#1-1), lines 1 to 80

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java#1-1), lines 1 to 60

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/tenant/service/TenantAdminService.java#80-80), lines 80 to 198

Ran terminal command:  echo "### AuthServiceImpl: TenantEntity / TenantRepository 用法"; grep -n "TenantEntity\|tenantRepository\|TenantRepository" auth/service/impl/AuthServiceImpl.java; echo; echo "### JwtAuthenticationFilter: TenantEnabledCache 用法"; grep -n "TenantEnabledCache\|tenantEnabledCache" auth/security/JwtAuthenticationFilter.java; echo; echo "### UserAdminService: TenantRepository 用法"; grep -n "TenantRepository\|tenantRepository\|TenantEntity" user/service/UserAdminService.java; echo; echo "### UserTenantMappingEntity: TenantEntity 用法"; grep -n "TenantEntity\|Tenant " user/entity/UserTenantMappingEntity.java

兩個環的根因完全收斂到**同一個檔案**：TenantAdminService.java。先確認現有 port 的封裝慣例，再定案。 

Ran terminal command:  echo "### 現有 common port 結構"; find common -type d -name port; echo; echo "### common/auth/port 內容"; ls -1 common/auth/port/ 2>/dev/null; echo; echo "### TenantAuthConfigEntity 欄位 (確認 seed 所需)"; sed -n '1,60p' auth/provider/config/entity/TenantAuthConfigEntity.java; echo; echo "### 誰呼叫 TenantAdminService.createTenant"; grep -rln "createTenant\|TenantAdminService" --include=*.java . | grep -v "TenantAdminService.java"

完整定案。兩個環的根因**完全收斂到單一方法**：`TenantAdminService.createTenant()`。它在 L1（tenant）層做了應屬上層的 user/auth 佈建。下面是解法規劃。

## 核心洞察

| 方向 | 層級 | 性質 | 處置 |
|---|---|---|---|
| `user → tenant`、`auth → tenant` | L2→L1（向下）| ✅ 合法業務依賴 | **保留不動** |
| `tenant → user`、`tenant → auth` | L1→L2（向上）| ❌ 反向依賴 = 製造環 | **反轉掉** |

**兩個向上依賴只存在於 `TenantAdminService` 一個檔案**（`updateTenant`/`toggleEnabled` 都只碰 tenant 自身，乾淨）。所以只要把 `createTenant` 裡的 user/auth 佈建邏輯「依賴反轉」到 port，**兩個環一次斷掉**。

## 解法：Port/Adapter 依賴反轉（沿用既有 `common.*.port` 慣例）

### 新增 2 個 port（放 common）

**① `common/user/port/TenantAdminProvisioner.java`**
```java
package com.taipei.iot.common.user.port;
public interface TenantAdminProvisioner {
    /** 為新建租戶佈建初始管理員（含密碼政策驗證、建立 UserEntity 與
     *  UserTenantMapping，mapping 以 system context 儲存）。無管理員資料則略過。 */
    void provisionTenantAdmin(TenantAdminSpec spec);

    record TenantAdminSpec(String tenantId, String email,
                           String rawPassword, String displayName) {}
}
```

**② `common/auth/port/TenantAuthConfigProvisioner.java`**
```java
package com.taipei.iot.common.auth.port;
import com.taipei.iot.common.enums.AuthType;   // AuthType 已在 common
public interface TenantAuthConfigProvisioner {
    /** 為新建租戶寫入預設 tenant_auth_config（system context）。 */
    void seedDefaultAuthConfig(String tenantId, AuthType authType);
}
```

### 新增 2 個 adapter（放各自的擁有模組）

**③ `user/service/UserProvisioningAdapter.java`** `@Component implements TenantAdminProvisioner`
搬走 `createTenant` 中：`existsByEmail` 檢查、`passwordValidator.validate`、建 `UserEntity`、`runInSystemContext` 存 `UserTenantMappingEntity`。持有 `UserRepository`、`UserTenantMappingRepository`、`PasswordEncoder`、`PasswordValidator`。

**④ `auth/provider/config/TenantAuthConfigProvisionerAdapter.java`** `@Component implements TenantAuthConfigProvisioner`
搬走 auth config seeding（建 `TenantAuthConfigEntity` + `runInSystemContext` 存）。持有 `TenantAuthConfigRepository`。

### 改 1 個檔（tenant 側收斂）

**⑤ TenantAdminService.java**
- 移除 5 個 `import ...user.*` + 2 個 `import ...auth.*`
- 移除 `userRepository`、`userTenantMappingRepository`、`passwordEncoder`、`passwordValidator`、`tenantAuthConfigRepository` 5 個欄位
- 改注入 `TenantAdminProvisioner`、`TenantAuthConfigProvisioner`（皆來自 common）
- `createTenant` body 改為兩行委派：
  ```java
  authConfigProvisioner.seedDefaultAuthConfig(tenantId, initialAuthMethod);
  adminProvisioner.provisionTenantAdmin(
      new TenantAdminSpec(tenantId, req.getAdminEmail(),
                          req.getAdminPassword(), req.getAdminDisplayName()));
  ```

> `ADMIN_ROLE_ID = "ROLE_ADMIN"` 常數隨 user 佈建邏輯移到 adapter。

## 效果

- `tenant → user`：5 → **0**，`tenant → auth`：2 → **0** ⟹ **環1、環2 同時斷開**
- 問題二 **7/7 全斷**，平台底層叢集完全無環
- ArchUnit `no_cyclic_dependencies` 屆時可從「凍結」改為「嚴格」（移除這兩個 frozen 違規），把成果鎖死

## 影響面

- 唯一呼叫者 `TenantAdminController` 不需改（公開簽章不變）
- 需更新測試 `TenantAdminServiceTest`（mock 改為兩個 port）；新增 adapter 的單元測試（可選）
- 收尾：`mvn spring-javaformat:apply -q` → `mvn test-compile` → 跑 `LayeredArchitectureTest`

---

這是低風險、單向收斂的重構。要我**直接執行**這 5 步（建 port、建 adapter、改 service、修測試、格式化＋驗證）嗎？

Created 4 todos