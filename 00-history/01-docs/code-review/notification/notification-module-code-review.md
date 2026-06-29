# Notification 模組 Code Review & Security Review

> 審查日期：2026-05-20
> 審查範圍：`backend/src/main/java/com/taipei/iot/notification/` 全部子套件 + 前端相關檔案

---

## 模組結構總覽

```
notification/
├── channel/
│   ├── NotificationChannel.java        # 通道策略介面
│   ├── InAppChannel.java               # 站內通知（DB + WebSocket STOMP）
│   ├── EmailChannel.java               # Email 通道（@Profile("!test")）
│   ├── NoOpEmailChannel.java           # 測試用 Email 替身（@Profile("test")）
│   └── NoOpSmsChannel.java             # SMS 佔位（尚未實裝）
├── controller/
│   └── NotificationController.java     # REST API（list / todos / unread-count / markRead / markAllRead）
├── dto/
│   ├── NotificationPayload.java        # 內部發送用 DTO（tenantId + userIds + type + title…）
│   ├── NotificationResponse.java       # API 回應 DTO
│   └── UnreadCountResponse.java        # 未讀數回應
├── entity/
│   └── NotificationEntity.java         # JPA 實體（tenant_id / user_id / type / read…）
├── enums/
│   ├── NotificationType.java           # TODO / ALERT / INFO
│   └── NotificationRefType.java        # FAULT / REPAIR / REPLACEMENT / WORKFLOW / ANNOUNCEMENT / MATERIAL / ALERT
├── repository/
│   └── NotificationRepository.java     # JPA Repository
└── websocket/
    ├── WebSocketConfig.java            # STOMP broker 配置
    └── StompAuthInterceptor.java       # STOMP CONNECT 時的 JWT 驗證
```

**前端檔案**：`types/notification.ts`、`api/notification/index.ts`、`stores/notificationStore.ts`、`components/NotificationBell.vue`

**測試覆蓋**：5 個測試檔案（`NotificationControllerTest`、`NotificationServiceTest`、`InAppChannelTest`、`EmailChannelTest`、`NoOpEmailChannelTest`、`NoOpSmsChannelTest`）

---

## 總體評價

Notification 模組設計**良好**，核心亮點：

- **多通道策略模式**：`NotificationChannel` 介面 + Spring List 注入，擴充新通道無需改動 Service
- **WebSocket 即時推送**：STOMP over SockJS + per-user queue，低延遲
- **STOMP 認證攔截**：`StompAuthInterceptor` 在 CONNECT 時驗證 JWT，拒絕無效連接
- **優雅降級**：WebSocket 推送失敗不影響 DB 儲存；Channel 異常不影響其他通道
- **使用者所有權驗證**：`markRead()` 驗證 `userId` 一致性
- **Profile 隔離**：`@Profile("!test")` 避免測試中真實發送 Email
- **前端 WebSocket + Polling 雙重保障**：優先 WebSocket，60 秒 polling 作為 fallback

---

## 安全審查（Security Review）

### ✅ 防護到位的項目

| 防護類別 | 實作方式 | 評估 |
|----------|----------|------|
| 認證 | 所有 API 在 `/v1/auth/` 下，JWT Filter 保護 | ✅ |
| WebSocket 認證 | `StompAuthInterceptor` CONNECT 階段驗證 JWT | ✅ |
| 使用者資料隔離 | `markRead()` 驗證 notification.userId == currentUserId | ✅ |
| 查詢隔離 | 所有查詢以 `userId` 作為 WHERE 條件 | ✅ |
| Channel 容錯 | 每個 channel.send() 獨立 try-catch，不互相影響 | ✅ |
| WebSocket 推送容錯 | STOMP push 失敗只 warn，不影響 DB 儲存 | ✅ |
| Email 偏好 | 僅在 `notifyEmailFlag == true` 時發送 | ✅ |
| CORS | WebSocket endpoint 使用 `cors.allowed-origins` 白名單 | ✅ |
| STOMP 拒絕未認證 | 無 Authorization header → MessageDeliveryException | ✅ |

---

### 需要注意的安全問題

#### 1. [高] `NotificationRepository` 未實作 `TenantScopedRepository` — `@Filter(tenantFilter)` 無法自動啟用

```java
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    // 未繼承 TenantScopedRepository
}
```

**風險**：
- `NotificationEntity` 上有 `@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")`
- 但 `TenantFilterAspect` 只對 `TenantScopedRepository` 的實例啟用 filter
- **目前 `@Filter` 形同虛設**，所有查詢（`findByUserIdOrderByCreatedAtDesc` 等）**不會**加上 `tenant_id` 條件

**實際影響**：
- 由於所有查詢都是 `WHERE user_id = :userId`，而 `userId` 在系統中是全域唯一的（UUID），所以**目前不存在跨租戶資料洩漏**
- 但如果未來改為其他查詢方式（如依 refId 查詢），缺少租戶隔離會成為問題
- `markAllReadByUserId()` 的批次更新也只針對特定 userId

**建議**：讓 `NotificationRepository` 繼承 `TenantScopedRepository`，保持與其他模組一致的安全縱深防禦。

#### 2. [中等] `page` 和 `size` 參數無上限限制

```java
@GetMapping
public BaseResponse<PageResponse<NotificationResponse>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
```

**風險**：攻擊者可傳入 `size=100000`，造成大量資料查詢、記憶體暴漲。

**建議**：加入 `@Max` 驗證或在 Service 層 clamp：
```java
@RequestParam(defaultValue = "20") @Max(100) int size
```

#### 3. [中等] `InAppChannel.send()` 在非事務性上下文中操作 DB

```java
@Override
public void send(NotificationPayload payload) {
    for (String userId : payload.getUserIds()) {
        NotificationEntity entity = ...;
        NotificationEntity saved = notificationRepository.save(entity);
        // ... WebSocket push
    }
}
```

**風險**：
- `send()` 沒有 `@Transactional`，如果在循環中某個 save 失敗，部分通知已存入 DB、部分未存
- 呼叫端（`NotificationService.send()`）也沒有事務

**建議**：
- 如果要求「全有或全無」→ 在 `send()` 加 `@Transactional`
- 如果接受「盡力送達」（目前設計意圖）→ 保持現狀但為每個 userId 加 try-catch 確保單一使用者失敗不影響其他人

**評估**：目前設計意圖為「盡力送達」，WebSocket 已有 try-catch。但 `save()` 拋 exception 時會中斷 for 迴圈，後續 user 不會收到通知。應在 for 迴圈內加 try-catch。

#### 4. [中等] `StompAuthInterceptor` 未驗證 Token 的 `tenantId` 一致性

```java
Claims claims = jwtUtil.parseToken(token);
String userId = claims.getSubject();
String tenantId = claims.get("tenantId", String.class);
UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(userId, tenantId, List.of());
accessor.setUser(auth);
```

**分析**：
- `tenantId` 存入 `credentials`，但後續 `convertAndSendToUser(userId, ...)` 只用 `userId` 做路由
- 如果使用者持有過期 token（tenantId 已被移除），仍可收到推送
- 但 JWT 已驗簽 + 過期檢查（`jwtUtil.parseToken` 會拋異常），所以**無法使用過期 token 連接**

**結論**：安全，但建議記錄 tenantId 至 WebSocket session attributes 供未來審計使用。

#### 5. [低] Email 主旨可能包含使用者輸入的未跳脫內容

```java
message.setSubject("[路燈平台] " + payload.getTitle());
message.setText(payload.getContent() != null ? payload.getContent() : payload.getTitle());
```

**風險**：如果 `title` 來自使用者輸入且包含 CRLF 字元，可能發生 Email Header Injection。`SimpleMailMessage` 在多數實作中會過濾，但不同 Mail Server 行為不一致。

**建議**：在 `NotificationPayload.title` 建構時確保不含換行字元，或在 `EmailChannel` 中做 sanitize。

#### 6. [低] `countUnreadByUserId` 和查詢端點無租戶條件（同議題 #1）

```java
@Query("SELECT COUNT(n) FROM NotificationEntity n WHERE n.userId = :userId AND n.read = false")
long countUnreadByUserId(@Param("userId") String userId);
```

因為 userId 全域唯一，**實務上安全**。但 JPQL 未受 Hibernate Filter 保護（因為 filter 未被啟用）。

---

## 程式碼品質審查（Code Review）

### ✅ 優點

#### 1. 多通道策略模式 — 開閉原則（OCP）

```java
private final List<NotificationChannel> channels;

public void send(NotificationPayload payload) {
    for (NotificationChannel channel : channels) {
        try {
            channel.send(payload);
        } catch (Exception e) {
            log.warn("Channel {} failed: {}", channel.channelType(), e.getMessage());
        }
    }
}
```

- 新增通道只需實作 `NotificationChannel` + `@Component`
- 各通道異常隔離，不互相影響
- `@Profile` 控制環境啟用

#### 2. `markRead()` 所有權驗證 — IDOR 防護

```java
if (!entity.getUserId().equals(userId)) {
    throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
}
```

- 使用 `NOTIFICATION_NOT_FOUND`（而非 403）避免資訊洩漏（攻擊者無法判斷 ID 是否存在）
- 已讀通知不重複 save

#### 3. 前端 WebSocket + Polling 雙重保障

```typescript
connectWebSocket(token: string) { ... }

startPolling(token?: string) {
    this.fetchUnreadCount()
    this.fetchItems()
    if (token) { this.connectWebSocket(token) }
    this.pollTimer = setInterval(() => { this.fetchUnreadCount() }, 60 * 1000)
}
```

- WebSocket 優先 → 即時
- 60 秒 polling fallback → 穩健
- reconnectDelay + heartbeat 確保連線品質

#### 4. `StompAuthInterceptor` — CONNECT 時認證

- 在 STOMP 層而非 HTTP 層驗證（正確做法）
- `Order(HIGHEST_PRECEDENCE + 99)` 確保在其他 interceptor 之前
- 失敗拋 `MessageDeliveryException` 拒絕連線

#### 5. `@Profile("test")` 隔離 — NoOp 替身

```java
@Profile("!test") public class EmailChannel implements NotificationChannel { ... }
@Profile("test")  public class NoOpEmailChannel implements NotificationChannel { ... }
```

- 測試不觸發真實 Email/SMS
- 生產環境不包含 NoOp bean

#### 6. 批次已讀 — 單一 DB 更新

```java
@Query("UPDATE NotificationEntity n SET n.read = true, n.readAt = CURRENT_TIMESTAMP ...")
int markAllReadByUserId(@Param("userId") String userId);
```

- 避免逐筆 save，效能佳
- 回傳受影響筆數供 debug

---

### 需要改進的問題

#### 7. [中等] `InAppChannel.send()` for 迴圈中 save 失敗會中斷後續使用者

```java
for (String userId : payload.getUserIds()) {
    NotificationEntity entity = ...;
    NotificationEntity saved = notificationRepository.save(entity);  // ← 若拋異常
    // ... 後續使用者不會被處理
}
```

**問題**：`save()` 若因 DB 異常（如 constraint violation）失敗，for 迴圈中斷，後面的 userId 不會收到通知。

**建議**：每個 userId 包一層 try-catch：
```java
for (String userId : payload.getUserIds()) {
    try {
        // save + push
    } catch (Exception e) {
        log.error("Failed to send in-app notification to user={}: {}", userId, e.getMessage());
    }
}
```

#### 8. [中等] `list()` 和 `listTodos()` 的 `size` 參數無上限

見安全審查 #2。使用者可傳入 `size=Integer.MAX_VALUE`。

#### 9. [低] `NotificationResponse` 缺少 `@JsonInclude(NON_NULL)`

```java
public class NotificationResponse {
    private NotificationRefType refType;  // 可能為 null
    private String refId;                 // 可能為 null
    private LocalDateTime readAt;         // 可能為 null
}
```

API 回應會包含 `"refType": null, "refId": null, "readAt": null`，增加回應體積。

#### 10. [低] `WebSocketConfig` 中 `setAllowedOrigins` 直接使用配置值

```java
registry.addEndpoint("/ws")
        .setAllowedOrigins(allowedOrigins)
        .withSockJS();
```

**分析**：只要 `cors.allowed-origins` 配置正確（非 `*`），則安全。需確認 `application.yml` 中的值。

#### 11. [低] 前端 `notificationStore` 錯誤靜默忽略

```typescript
async fetchUnreadCount() {
    try { ... } catch { /* silently fail */ }
}
```

**問題**：所有 API 失敗都靜默忽略。如果是 401（token 過期），使用者不會知道需要重新登入。

**建議**：至少在 `catch` 中檢查是否為 401/403，若是則觸發登出流程。

#### 12. [低] `EmailChannel` 沒有速率限制

如果大量通知同時發送（如公告通知所有使用者），可能短時間內發送大量 Email，觸發 SMTP 限流。

**建議**：未來可考慮使用 message queue 進行非同步批次發送。

---

## 架構總評

| 維度 | 評分 | 說明 |
|------|------|------|
| 安全性 | **8/10** | userId 所有權驗證完善、STOMP 認證正確。但 Repository 未繼承 TenantScopedRepository（雖因 userId 唯一而實務安全）、page size 無上限。 |
| 正確性 | **8.5/10** | 多通道策略正確、markRead 冪等。InAppChannel for 迴圈缺少逐筆容錯。 |
| 效能 | **9/10** | 批次已讀更新、WebSocket 即時推送、polling 作為 fallback。 |
| 可維護性 | **9/10** | 策略模式解耦通道、Profile 隔離、DTO 分離清楚。 |
| 可測試性 | **9/10** | 5 個測試檔案，含 Service 14 測試 + Controller 6 測試 + Channel 各 3-4 測試。NoOp 替身確保 CI 穩定。 |

---

## 建議優先級摘要

| 優先級 | 項目 | 類型 | 狀態 |
|--------|------|------|------|
| **P2** | `NotificationRepository` 繼承 `TenantScopedRepository` | Security / Defense-in-depth | ✅ 已修正 |
| **P2** | `page`/`size` 參數加上限（`@Max(100)`） | Security / DoS prevention | ✅ 已修正 |
| **P3** | `InAppChannel.send()` for 迴圈加逐筆 try-catch | Reliability | ✅ 已修正 |
| **P4** | `NotificationResponse` 加 `@JsonInclude(NON_NULL)` | Maintainability | ✅ 已修正 |
| **P4** | `EmailChannel.sendEmail()` 的 title 做 CRLF sanitize | Security / Email Injection | ✅ 已修正 |
| **P5** | 前端 `notificationStore` catch 中處理 401 | UX / Security | — 建議 |
| **P5** | `EmailChannel` 未來考慮 message queue 非同步批次 | Scalability | — 建議 |
