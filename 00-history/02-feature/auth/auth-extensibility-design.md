# Auth 功能模組「易擴展」設計指南

> 對象：`com.taipei.iot.auth` 後端模組
> 目的：說明如何設計、以及有哪些具體作法，讓認證（Authentication）模組能「易擴展」——在最少修改、不破壞既有行為的前提下，加入新的認證方式（OIDC/SAML/SSO/Passkey/OTP…）、新的密碼策略、新的 Token 規則。

---

## 1. 什麼叫「易擴展」？（先定義目標）

對 auth 模組而言，「易擴展」具體要滿足以下幾個可衡量的目標：

| 目標 | 白話描述 | 衡量方式 |
|------|----------|----------|
| **開放封閉（OCP）** | 新增認證方式時「只新增類別」，不改既有類別 | 加一種登入方式所需修改的既有檔案數 ≈ 0 |
| **單一職責（SRP）** | 每個認證方式各自獨立，互不影響 | LDAP 改動不會編譯/測試到 LOCAL |
| **可設定（Configurable）** | 啟用/停用、連線參數由設定驅動，不用改碼 | 透過 DB/設定檔切換 provider |
| **可組合（Composable）** | 登入流程（驗證碼→鎖定→密碼策略→發 Token）可分段插拔 | 新增一個前置/後置步驟不需動主流程 |
| **可測試（Testable）** | 每個擴展點可獨立 mock 測試 | provider 可單測，dispatcher 可單測路由 |
| **多租戶安全** | 每租戶可有不同認證設定且互相隔離 | 設定以 tenant 為單位儲存且加密 |

---

## 2. 現況盤點：已經做對的事

auth 模組**已經具備一套以策略模式為核心的擴展骨架**，這是後續擴展的基礎。

### 2.1 核心抽象（擴展點）

```
auth/provider/
├── AuthenticationProvider.java   ← 策略介面（擴展點）
├── AuthenticationDispatcher.java ← 路由/工廠（@Component，啟動時自動聚合所有 provider）
├── AuthType.java                 ← LOCAL, LDAP, OIDC, SAML（列舉驅動）
├── AuthenticationRequest.java    ← provider-agnostic 輸入 DTO
├── AuthenticationResult.java     ← provider-agnostic 輸出 DTO
├── local/  LocalAuthProvider     ← 帳密 + BCrypt + 帳號鎖定
├── ldap/   LdapAuthProvider      ← LDAP bind
├── config/ TenantAuthConfig...   ← 每租戶設定（加密 JSON）
└── crypto/ AuthConfigEncryptor   ← 設定加解密（AES-GCM）
```

**關鍵設計（[AuthenticationProvider.java](../../../backend/src/main/java/com/taipei/iot/auth/provider/AuthenticationProvider.java)）**：

```java
public interface AuthenticationProvider {
    AuthType getType();                                                  // 我負責哪種認證
    AuthenticationResult authenticate(AuthenticationRequest req, String configJson); // 怎麼驗
    default boolean testConnection(String configJson) { return true; }  // 設定測試（後台用）
}
```

**自動註冊（[AuthenticationDispatcher.java](../../../backend/src/main/java/com/taipei/iot/auth/provider/AuthenticationDispatcher.java)）**：

```java
public AuthenticationDispatcher(List<AuthenticationProvider> providers, ...) {
    this.providerMap = providers.stream()
        .collect(Collectors.toMap(AuthenticationProvider::getType, Function.identity()));
}
```

> Spring 啟動時把所有 `AuthenticationProvider` Bean 注入成 `List`，再以 `AuthType` 為 key 建索引。
> **這就是 OCP 的落地：新增一個 `@Component` 實作，dispatcher 自動納管，主流程一行都不用改。**

### 2.2 三層設定/路由解析（已具備可設定性）

- **認證路由**（dispatch 決定用哪個 provider）：
  1. 使用者層級 `UserEntity.authType`（最高優先，讓 LOCAL/LDAP 使用者可在同租戶共存）
  2. 租戶層級 `TenantAuthConfigEntity`（加密 JSON 存連線參數）
  3. 硬預設 → `LOCAL`
- **密碼策略**（[PasswordPolicyResolver.java](../../../backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyResolver.java)）：
  租戶覆寫 → 平台預設 → 列舉硬預設（`PasswordPolicyKey`），同樣是「列舉 + 三層 fallback」可擴展設計。

### 2.3 容錯擴展（基礎設施失敗才 fallback）

dispatch 內已實作「外部認證**基礎設施**錯誤（逾時/連不上）才 fallback LOCAL；憑證錯誤（密碼錯/帳號鎖）絕不 fallback」的安全規則——擴展新 provider 時應沿用此原則。

> **結論：骨架已經對。本文件後半聚焦「如何沿著這個骨架繼續擴展」與「目前缺口」。**

---

## 3. 擴展維度：會被擴展的是哪些東西？

設計易擴展前，先界定「未來最可能新增什麼」。auth 模組的擴展維度有五類：

| 維度 | 範例需求 | 主要擴展點 |
|------|----------|-----------|
| **A. 認證方式** | OIDC / SAML / 企業 SSO / Passkey(WebAuthn) / OTP / 手機簡訊 | `AuthenticationProvider` |
| **B. 登入流程步驟** | 風險評估、裝置指紋、二階段驗證(MFA)、登入告警 | 流程責任鏈（Pipeline，**目前缺**） |
| **C. 帳號開通** | 外部 IdP 首登自動建檔（JIT Provisioning）、群組→角色映射 | `UserProvisioner`（**目前缺**） |
| **D. 密碼/憑證策略** | 新密碼規則、黑名單字典、定期換密 | `PasswordPolicyKey` + Validator |
| **E. Token/Session** | 不同 scope 的 claim、Token 撤銷、裝置綁定 | `JwtUtil` / `TokenScope` |

下面針對每個維度給出設計手法。

---

## 4. 設計手法（Patterns & Practices）

### 手法 1：策略模式 + SPI 自動註冊（維度 A，已具備）

**做法**：維持 `AuthenticationProvider` 介面，新增方式 = 新增一個 `@Component`。

新增 OIDC 範例（不改任何既有檔案）：

```java
@Component
public class OidcAuthProvider implements AuthenticationProvider {
    public AuthType getType() { return AuthType.OIDC; }

    public AuthenticationResult authenticate(AuthenticationRequest req, String configJson) {
        OidcConfig cfg = JsonUtil.read(configJson, OidcConfig.class);
        // 1. 用 req.getExtra().get("code") 換 token
        // 2. 驗 id_token 簽章 / nonce
        // 3. 取 sub / email / groups
        return AuthenticationResult.builder()
            .externalId(sub).email(email).displayName(name)
            .claims(Map.of("groups", groups))   // 給角色映射用
            .build();
    }

    @Override public boolean testConnection(String configJson) { /* 探測 discovery endpoint */ }
}
```

> `AuthenticationRequest.extra`（`Map<String,String>`）已預留給 OIDC 的 `code`、SAML 的 `SAMLResponse`，這代表 DTO 設計時就考慮過非帳密類流程——維持此「provider-agnostic DTO」設計是關鍵。

**強化建議**：
- 讓 provider 自帶「啟用條件」：`@ConditionalOnProperty(name="auth.provider.oidc.enabled")`，可在不部署該 jar 模組時完全不載入。
- 在 `AuthType` 列舉加值時，保留 `fromString()` 對未知值的容錯（目前已有，但會丟 `IllegalArgumentException`，建議改回傳明確的 `AUTH_PROVIDER_NOT_SUPPORTED`）。

---

### 手法 2：登入流程責任鏈（Pipeline / Chain）— 維度 B（**建議新增**）

**問題**：目前 `AuthServiceImpl.login()` 把「驗證碼 → 路由 → 驗證 → 密碼過期檢查 → 發 Token」串成一條較長流程。要插入 MFA、風險引擎、登入告警時，得改主流程 → 違反 OCP。

**做法**：把流程切成可插拔的 `AuthStep`，用責任鏈組裝。

```java
public interface AuthStep {
    int order();                              // 執行順序
    void apply(AuthContext ctx);              // 可讀寫共享 context；失敗丟 BusinessException
    default boolean supports(AuthContext c){ return true; } // 條件啟用
}

// 既有邏輯拆成獨立 step（皆 @Component，自動聚合並依 order 排序）
@Component class CaptchaStep        implements AuthStep { int order(){return 10;} ... }
@Component class ResolveTenantStep  implements AuthStep { int order(){return 20;} ... }
@Component class DispatchAuthStep   implements AuthStep { int order(){return 30;} ... } // 呼叫 dispatcher
@Component class PasswordExpiryStep implements AuthStep { int order(){return 40;} ... }
@Component class IssueTokenStep     implements AuthStep { int order(){return 90;} ... }
```

未來加 MFA 只需：

```java
@Component
@ConditionalOnProperty("auth.mfa.enabled")
class MfaStep implements AuthStep {
    public int order(){ return 35; }                       // 驗證後、發 Token 前
    public boolean supports(AuthContext c){ return c.getUser().isMfaEnabled(); }
    public void apply(AuthContext c){ /* 觸發/驗證 OTP */ }
}
```

> **效益**：登入流程變成「資料驅動的步驟清單」，新增橫切關注點（MFA、風險、告警、稽核）不再碰 `AuthServiceImpl`。

---

### 手法 3：帳號開通與角色映射策略 — 維度 C（**建議新增**）

`AuthenticationResult` 已預留 `externalId / email / displayName / claims`，**但目前沒有消費端**：外部 IdP（LDAP/OIDC）使用者必須事先在系統建檔，無法 JIT。

**做法**：抽出 `UserProvisioner` 與 `ClaimMapper` 兩個擴展點。

```java
public interface UserProvisioner {
    AuthType getType();
    UserEntity resolveOrProvision(AuthenticationResult r, String tenantId);
}

public interface ClaimMapper {            // IdP 群組 → 系統角色
    AuthType getType();
    Set<String> mapRoles(Map<String,Object> claims, String tenantId);
}
```

- LOCAL：`resolveOrProvision` 直接以 `localUserId` 查既有使用者（不建檔）。
- OIDC/SAML：以 `externalId` 找 mapping；找不到且租戶允許 JIT → 依 `claims` 建檔 + `ClaimMapper` 指派角色。

> 設計重點：把「驗證成功之後該怎麼對應/建立本地使用者」這件事，從 provider 抽離成獨立策略，避免每個 provider 重複實作建檔邏輯，也讓「同一使用者多 IdP 綁定」這種進階需求有落腳處（未來再加 `UserAuthIdentity` 表）。

---

### 手法 4：密碼/憑證策略以「列舉 + Validator」擴展 — 維度 D（已具備，續用）

維持 `PasswordPolicyKey` 列舉 + `PasswordPolicyResolver` 三層 fallback。新增規則 = 加列舉值 + 對應 `PasswordRule` 實作，避免在 `PasswordValidator` 裡塞 if-else。

```java
public interface PasswordRule {
    void validate(String rawPassword, PasswordPolicy policy, UserContext user); // 違反丟例外
}
@Component class NotContainsUsernameRule implements PasswordRule { ... }
@Component class DictionaryBlacklistRule implements PasswordRule { ... } // 未來新增只加一個類別
```

`PasswordValidator` 改成「注入 `List<PasswordRule>` 全跑一遍」即可——同樣是策略集合自動聚合。

---

### 手法 5：Token/Scope 以列舉 + Claim 建構器擴展 — 維度 E

`TokenScope`（TENANT/PLATFORM/IMPERSONATION）已是列舉驅動。建議把「組 claim」抽成 `TokenClaimContributor`，讓不同模組可貢獻自己的 claim 而不改 `JwtUtil`：

```java
public interface TokenClaimContributor {
    void contribute(Map<String,Object> claims, AuthContext ctx);
}
```

> 例如未來要加「裝置綁定 id」「MFA 已通過」claim，新增一個 contributor 即可。

---

## 5. 共通的工程實踐（讓擴展不出錯）

| 實踐 | 作法 | 為何重要 |
|------|------|----------|
| **設定外部化 + 加密** | 連線參數存 `TenantAuthConfigEntity.configJson`，經 `AuthConfigEncryptor` AES-GCM 加密 | 新 provider 的密鑰/secret 不落明碼、不寫死 |
| **特性開關** | provider/step 加 `@ConditionalOnProperty` | 能逐租戶/逐環境灰度開關，不需重編譯 |
| **provider-agnostic DTO** | 維持 `AuthenticationRequest/Result` 不含特定協定欄位，協定參數塞 `extra/claims` | DTO 不會隨每加一種協定而膨脹 |
| **錯誤碼集中** | 沿用 `ErrorCode`（如 `AUTH_PROVIDER_NOT_SUPPORTED`），新 provider 復用既有碼 | 前端錯誤處理不必為新 provider 改 |
| **安全稽核一致** | 沿用 `SecurityLogger` + `SecurityEvent` 記錄登入/fallback | 新 provider 自動有稽核軌跡 |
| **fallback 規則一致** | 僅「基礎設施錯誤」可 fallback LOCAL，憑證錯誤絕不 | 防止繞過外部 IdP 的安全漏洞 |
| **可測試性** | provider 單測（mock IdP）+ dispatcher 路由單測 | 擴展有回歸保護 |
| **後台自助** | 每 provider 實作 `testConnection()` | 管理者可自助驗證設定，降低維運成本 |

---

## 6. 目前缺口與優先順序

| 缺口 | 現況 | 風險 | 建議優先級 |
|------|------|------|-----------|
| 登入流程未責任鏈化 | 邏輯集中在 `AuthServiceImpl` | 加 MFA/風險引擎要改主流程 | ★★★（先做） |
| 無 JIT Provisioning | 外部使用者須預先建檔 | OIDC/SAML 上線會卡 | ★★★ |
| 無 ClaimMapper（群組→角色） | `claims` 已收集但無人用 | 外部使用者拿不到角色 | ★★☆ |
| `AuthType.fromString` 對未知值丟 `IllegalArgumentException` | — | 設定髒資料造成 500 | ★☆☆（小修） |
| 密碼規則仍偏集中式 | 列舉已可擴展，但 Validator 內仍有條件分支 | 加規則需改 Validator | ★☆☆ |
| 無「使用者多 IdP 綁定」 | 一使用者一 `authType` | 帳號聯邦化受限 | ☆（未來） |

---

## 7. 落地檢查清單（新增一種認證方式時照著做）

新增 `XXX` 認證方式（例如 OIDC）時，**理想情況只需「新增」以下項目，不改既有檔**：

- [ ] `AuthType` 加入 `XXX`（列舉）
- [ ] 新增 `provider/xxx/XxxAuthProvider implements AuthenticationProvider`（`@Component`，建議加 `@ConditionalOnProperty`）
- [ ] 新增 `provider/xxx/XxxConfig`（設定 schema，對應加密 JSON）
- [ ] 實作 `testConnection()` 供後台測試
- [ ] （若需 JIT）新增 `XxxUserProvisioner` 與 `XxxClaimMapper`
- [ ] 後台設定 UI 走既有 `tenant-auth-config`/`platform tenants auth-config` API（設定以租戶為單位、加密儲存）
- [ ] 單元測試：provider 驗證成功/失敗、dispatcher 路由命中、fallback 行為
- [ ] 安全稽核：確認 `SecurityLogger` 有記錄登入結果
- [ ] 文件：在本目錄補一份 `auth-provider-xxx.md` 設計與設定說明

> 若上述「新增一種方式」過程中**被迫修改既有 provider 或 `AuthServiceImpl` 的核心分支**，代表抽象出現破口，應回頭檢視是否該補上「手法 2/3」的擴展點。

---

## 8. 一句話總結

> auth 模組已用**策略 + 工廠（dispatcher 自動聚合）**滿足「新增認證方式只加類別」的核心擴展性；
> 要更全面地「易擴展」，再補上**登入流程責任鏈（AuthStep）**、**帳號開通策略（UserProvisioner / ClaimMapper）**、以及**Token Claim 貢獻者**三個擴展點，
> 並全程貫徹「設定外部化＋加密、特性開關、provider-agnostic DTO、一致的錯誤碼與安全稽核」這套工程實踐，即可在不動主流程的前提下持續演進。
