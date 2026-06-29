這是一個關於 **「資安嚴格度」與「使用者體驗 (UX)」如何平衡** 的優化建議。

簡單來說，審查委員認為：**「使用者忘記密碼去重設」和「使用者自己主動改密碼」，這兩種情境下，「不可使用過去舊密碼」的嚴格程度（N 次）應該要不一樣。**

以下為您詳細拆解這個問題的痛點與解法：

### 1. 為什麼要分離？（UX 與資安的衝突）

在您的系統中，密碼歷史檢查（防止使用者重複使用舊密碼）目前只有一個設定值 `historyCount`（例如設定為 5，代表不可使用過去 5 次用過的密碼）。但這會導致以下問題：

*   **情境 A：主動變更密碼 (Change Password)**
    *   **使用者狀態**：使用者**記得**目前的密碼，登入後主動想要換一個新密碼。
    *   **資安要求**：嚴格。要求不可使用過去 5 次密碼是合理的，防止使用者為了應付定期改密碼政策，一直用同一組密碼。
*   **情境 B：忘記密碼重設 (Reset Password)**
    *   **使用者狀態**：使用者**已經忘記**密碼了，所以透過信箱/簡訊驗證碼來重設。
    *   **UX 災難**：如果此時系統依然嚴格要求「不可使用過去 5 次用過的密碼」，使用者會非常崩潰。因為他們本來就忘記密碼了，現在系統又告訴他們「你剛剛輸入的新密碼跟你以前用過的一樣，請換一個」，使用者根本想不起來以前用過什麼，導致無法完成重設。
    *   **資安妥協**：因為「重設密碼」流程本身已經通過了強烈的身份驗證（如信箱 OTP），安全風險較低。因此，業界標準做法是**放寬重設時的密碼歷史限制**（例如 N=0 允許用舊密碼，或 N=1 僅限制不可與「當前」密碼相同）。

### 2. 您的程式碼現況（對照 `PasswordValidator.java`）

審查意見精準地指出了您程式碼的盲點：

```java
// PasswordValidator.java 第 108 行左右
public void checkNotRecentlyUsed(@Nullable String tenantId, String userId, String rawPassword) {
    PasswordPolicy policy = policyResolver.resolve(tenantId);
    
    // 🚨 問題在這裡：無論上層是 change 還是 reset 呼叫，這裡永遠只取同一個 N 值
    int historyCount = policy.getHistoryCount(); 
    
    if (historyCount <= 0) {
        return;
    }
    // ... 後續查詢 password_history 表並比對 ...
}
```

*   上層的 `resetPassword` 流程因為某些原因（可能是使用者尚未登入，缺乏完整的 tenant context）傳入 `tenantId=null`。
*   上層的 `changePassword` 流程傳入具體的 `tenantId`。
*   **結果**：雖然上層呼叫時傳入的參數不同，但 `PasswordValidator` 內部**沒有區分情境**，最終都去查同一張 `password_history` 表，且使用同一個 `N` 值來阻擋使用者。

### 3. 如何修復？（具體實作建議）

要解決這個問題，需要從「政策設定」到「驗證邏輯」進行微調：

#### 步驟 1：擴充 `PasswordPolicy` 設定
在您的 `PasswordPolicy` 類別（或對應的資料庫欄位/設定檔）中，增加一個專門給重設情境使用的欄位：
```java
// PasswordPolicy.java (示意)
private int historyCount;           // 變更密碼時，不可使用過去 N 次 (例如 5)
private int historyCountForReset;   // 重設密碼時，不可使用過去 M 次 (建議設為 0 或 1)
```

#### 步驟 2：修改 `PasswordValidator` 支援情境區分
將 `checkNotRecentlyUsed` 方法重載 (Overload) 或增加參數，讓上層可以指定要使用哪一個 N 值：

```java
// PasswordValidator.java
public void checkNotRecentlyUsed(@Nullable String tenantId, String userId, String rawPassword, boolean isResetFlow) {
    PasswordPolicy policy = policyResolver.resolve(tenantId);
    
    // 👇 根據情境決定要檢查幾次歷史
    int historyCount = isResetFlow ? policy.getHistoryCountForReset() : policy.getHistoryCount();
    
    if (historyCount <= 0) {
        return; // 如果是 reset 且 historyCountForReset=0，直接放行，改善 UX
    }
    
    // ... 原有的查詢與比對邏輯 ...
}
```

#### 步驟 3：上層 Service 正確呼叫
在您的 `AuthService` 或 `UserService` 中，根據業務流程傳遞正確的旗標：
*   在 `changePassword()` 方法中呼叫：`validator.checkNotRecentlyUsed(tenantId, userId, newPassword, false);`
*   在 `resetPassword()` 方法中呼叫：`validator.checkNotRecentlyUsed(null, userId, newPassword, true);`

### 總結
這是一個 **🟠 中風險 / 高 UX 影響** 的缺失。
它不會直接導致系統被駭，但會導致「忘記密碼的使用者無法順利重設密碼」，進而產生大量的客服客訴。審查委員建議將「變更」與「重設」的密碼歷史限制分離，是標準且成熟的 IAM（身分識別與存取管理）最佳實踐。