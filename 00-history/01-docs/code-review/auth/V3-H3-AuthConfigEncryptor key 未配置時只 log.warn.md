這是一個違反 **「失效安全 (Fail-Secure)」** 與 **「快速失敗 (Fail-Fast)」** 原則的配置風險。

簡單來說：**系統在缺少「加密金鑰」這種核心安全組件的情況下，居然還能成功啟動。這違背了「寧可系統掛掉不讓你用，也不能讓系統裸奔（明文儲存）」的資安底線。**

以下為您詳細拆解這個問題的盲點、實際風險以及修復方案：

### 1. 為什麼審計委員認為這是 High Risk？

在您的 `AuthConfigEncryptor.java` 中，當 `app.auth.config-secret-key` 沒有設定時，`@PostConstruct` 只做了 `log.warn` 就 `return` 了，系統繼續正常啟動。這會引發以下連鎖風險：

*   **維運疏失無感知 (Human Error)**：
    在部署到 Production 時，CI/CD 流程或維運人員可能**忘記設定環境變數** `AUTH_CONFIG_SECRET_KEY`。因為系統只會印出一行 `warn` 日誌就順利啟動，維運人員很容易漏看，誤以為系統已經準備好可以加密了。
*   **明文儲存風險 (Fail-Open)**：
    雖然您目前的 `encrypt()` 方法裡有 `requireKey()` 會拋出 `IllegalStateException`，但这代表 **「管理員在後台設定 LDAP 密碼時會報錯」**。
    更危險的情境是：如果未來有開發者在 `TenantAuthConfigService` 寫了類似 `if (encryptor.isKeyConfigured()) { 加密 } else { 存明文 }` 的妥協邏輯，或者新增功能時忘記檢查，**敏感的 LDAP bindPassword 或 OIDC clientSecret 就會直接以明文寫入資料庫**。
*   **違反 Fail-Fast 原則**：
    在軟體工程與資安架構中，**「配置錯誤」應該在「系統啟動時 (Boot time)」就被發現並中斷**，而不是讓系統帶病上線，等到「運行時 (Runtime)」管理員要存密碼時才爆發問題。

*(註：審計意見中提到的 property name 是 `app.auth.config.encryption-key`，但您實際程式碼與 `application.yml` 中是 `app.auth.config-secret-key`，修復時請以您的實際程式碼為準。)*

---

### 2. 如何修復？（具體實作建議）

要解決這個問題，必須讓 `AuthConfigEncryptor` 具備**環境感知能力**。在 Production 環境下，如果沒有金鑰，就必須**直接拋出例外，中斷 Spring Boot 啟動程序**。

#### 步驟 1：修改 `AuthConfigEncryptor.java`
注入 Spring 的 `Environment` 來判斷目前的 Active Profile，並在 `@PostConstruct` 中實作 Fail-Fast 邏輯：

```java
package com.taipei.iot.auth.provider.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
// ... 其他 import ...

@Component
@Slf4j
public class AuthConfigEncryptor {
    // ... 原有常數與變數 ...

    @Value("${app.auth.config-secret-key:}")
    private String secretKeyBase64;

    // 👇 新增：注入 Environment 以判斷當前啟動的 Profile
    @Autowired
    private Environment environment;

    private SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    void init() {
        // 檢查是否為 Production 環境 (可根據您實際的 profile 名稱調整，例如 "prod", "production")
        boolean isProdProfile = java.util.Arrays.asList(environment.getActiveProfiles())
                .stream()
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));

        if (secretKeyBase64 == null || secretKeyBase64.isBlank()) {
            if (isProdProfile) {
                // 🚨 [V3-H3 修復] 生產環境強制 Fail-Fast：缺少金鑰直接中斷啟動，防止明文儲存風險
                String errorMsg = "FATAL: AUTH_CONFIG_SECRET_KEY is mandatory in production profile. " +
                                  "Application startup aborted to prevent storing credentials in plaintext.";
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            } else {
                // 開發/測試環境：允許啟動，但給予警告 (維持原有行為以利本地開發)
                log.warn("AUTH_CONFIG_SECRET_KEY not configured. Tenant auth config encryption/decryption is DISABLED.");
                return;
            }
        }

        // 原有的金鑰長度驗證與初始化邏輯...
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "AUTH_CONFIG_SECRET_KEY must be 32 bytes (256 bits), got " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("AuthConfigEncryptor initialized successfully with 256-bit AES key.");
    }

    // ... 後續的 encrypt / decrypt / requireKey 方法保持不變 ...
}
```

#### 步驟 2：確認 `application.yml` 的 Profile 設定
確保您的 `application-prod.yml`（或對應的生產環境配置）中，`spring.profiles.active` 確實包含 `prod`，這樣上述的 `isProdProfile` 判斷才會生效。

---

### 3. 這個修復帶來的效益

1.  **消除人為疏失**：如果 CI/CD 部署到 Production 時漏設了 `AUTH_CONFIG_SECRET_KEY`，Spring Boot 會直接啟動失敗 (Crash)，Kubernetes 或 Docker 會重啟容器並明確顯示 `FATAL: ...` 錯誤日誌，維運人員能**立刻**發現並修正，不會讓系統帶病上線。
2.  **滿足資安合規**：向審計委員證明系統具備 **Fail-Secure (失效安全)** 機制，在安全組件缺失時，系統選擇「拒絕服務」而非「降級為不安全的明文模式」。
3.  **不影響本地開發**：透過 Profile 判斷，開發者在本地 (`dev` profile) 依然可以沒有金鑰的情況下啟動系統進行測試，保持開發彈性。

### 總結
這是一個典型的 **「配置驅動安全 (Configuration-Driven Security)」** 缺失。透過在 `@PostConstruct` 中加入 Profile 判斷並拋出 `IllegalStateException`，就能完美解決這個 High Risk 問題，並順利通過資安審計。