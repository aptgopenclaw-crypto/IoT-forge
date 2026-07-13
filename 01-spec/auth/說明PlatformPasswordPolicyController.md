這個 `PlatformPasswordPolicyController` 是專為**平台超級管理員 (SUPER_ADMIN)** 設計的 RESTful 控制器，負責管理整個多租戶系統的**全域密碼安全底線 (Platform Floor)**。

在多租戶架構中，它扮演著「最高安全標準制定者」的角色。以下為該類別的詳細結構、安全機制與架構定位說明：

### 1. 核心定位與架構角色 (Spec D-4)
根據系統規範 **Spec D-4 (平台設定下限，租戶只能在此基礎上加強)**，密碼策略分為「平台預設值」與「租戶覆寫值」。
此 Controller 提供的 API 專門用於設定那個**不可逾越的安全底線**。這意味著，任何下層租戶的密碼複雜度要求（如最小長度、特殊符號數量），都**只能等於或高於**此 Controller 所設定的值，絕不能更寬鬆。

---

### 2. 核心安全與審計機制
這個 Controller 雖然程式碼簡短，但透過 Spring 註解實作了極高強度的安全與合規控管：

* **類級別權限鎖 (`@PreAuthorize`)**：
  ```java
  @PreAuthorize("hasAuthority('PLATFORM_PASSWORD_POLICY_MANAGE')")
  ```
  直接在 Class 上進行方法級別 (Method-Level) 的權限攔截。這意味著該 Controller 下的**所有端點**，都必須具備 `PLATFORM_PASSWORD_POLICY_MANAGE` 權限才能存取。這種寫法比在 `SecurityConfig` 中配置 URL 規則更內聚，確保了不會有漏網之魚。
* **高危操作審計 (`@AuditEvent`)**：
  ```java
  @AuditEvent(AuditEventType.UPDATE_PLATFORM_PASSWORD_POLICY)
  ```
  修改全域安全底線是極高敏感的操作。此註解確保了每一次的修改都會被記錄到審計日誌中（包含操作者、時間、修改的 Key/Value），滿足企業級合規與不可抵賴性要求。

---

### 3. API 端點說明

#### A. 讀取平台底線
* **端點**：`GET /v1/platform/password-policy`
* **功能**：回傳目前平台的全域密碼策略原始鍵值對 (`Map<String, String>`)。
* **用途**：供平台後台管理介面渲染「全域安全設定」表單，讓超級管理員檢視目前的底線標準。

#### B. 更新平台底線 (觸發連鎖反應)
* **端點**：`PUT /v1/platform/password-policy`
* **功能**：更新平台密碼策略。接收 `UpdatePasswordPolicyRequest` (包含 `key` 與 `value`)。
* **底層聯動 (核心亮點)**：
  它呼叫了 `PasswordPolicyService.updatePlatformDefault()`，這會觸發兩個關鍵的安全機制：
  1. **硬底線防護**：防止管理員手誤將底線設為 0 或負數（例如 `min_length=0`）。
  2. **寫後掃描與級聯清理 (Post-write scan)**：如果平台**提高**了某個整數的下限（例如將最小長度從 8 提高到 12），系統會自動執行 `dao.deleteBelowFloor()`，**強制刪除資料庫中所有低於 12 的租戶覆寫值**。
     * **意義**：這確保了「牽一髮而動全身」。當平台收緊安全規範時，所有租戶會立即被迫適用新規範，不會有租戶因為保留了舊的寬鬆覆寫值而產生安全漏洞。

---

### 4. 在系統中的生態系連動

這個 Controller 與系統中的其他密碼策略模組形成了一個完整的閉環：

1. **`PlatformPasswordPolicyController` (本類)**：平台管理員設定**全域底線**。
2. *(隱含的) `TenantPasswordPolicyController`*：租戶管理員在自己的底線之上，設定**更嚴格的覆寫值**（若嘗試設定比平台底線寬鬆的值，會被 `PasswordPolicyService` 擋下並拋出 `PASSWORD_POLICY_BELOW_PLATFORM_MINIMUM` 異常）。
3. **`NoauthPasswordPolicyController`**：前端登入/重置密碼頁面呼叫，獲取當前租戶（或平台）的**最終生效策略**，用於前端表單的即時驗證 (Real-time validation)。

### 總結
`PlatformPasswordPolicyController` 是一個非常標準且嚴謹的**薄控制器 (Thin Controller)**。它將權限控制、審計日誌完美地透過註解聲明，而將複雜的業務邏輯（如底線校驗、級聯清理）下沉到 `PasswordPolicyService`。程式碼簡潔、職責單一，且完全符合企業級多租戶系統中「集中管控、底線防禦」的安全合規要求。