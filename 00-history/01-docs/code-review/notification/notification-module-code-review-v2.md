# Notification 模組 Code Review & Security Review v2

> 本文件為 [notification-module-code-review.md](notification-module-code-review.md) 的後續複查與擴充。  
> 重點：(1) 驗證 v1 全部 7 項優先項目（P-1~P-7）的狀態 (2) 補上 v1 漏看的新問題 (3) 提出值得優化的功能建議。  
> 複查日期：2026-05-27。  
> 審查範圍：`backend/src/main/java/com/taipei/iot/notification/` 全部 controller / service / channel / dto / entity / enum / repository / websocket + 6 個測試；跨模組整合（`TenantFilterAspect`、`AuditEventType`、`SecurityConfig`、`application.yml` CORS/Mail）；前端 `stores/notificationStore.ts`、`components/NotificationBell.vue`、`api/notification/index.ts`、`types/notification.ts`；DB migration `V31__notify_create_notifications.sql`。

---

## 一、整體評價

| 維度       | v1 評分 | v2 修復前 | v2 修復後 | 變化 |
| ---------- | ------- | ---------- | ---------- | ---- |
| 安全性     | 8.0/10  | 7.0/10 | **9.0/10** | ⬆ 2.0 — N-1（STOMP SUBSCRIBE 驗證）✅ + N-9（CORS fail-fast）✅ |
| 正確性     | 8.5/10  | 8.5/10 | **9.0/10** | ⬆ 0.5 — N-7（markRead 原子更新）已修復 ✅ |
| 效能       | 9.0/10  | 7.5/10 | **9.0/10** | ⬆ 1.5 — N-2（batch load）✅ + N-3（retention 歸檔機制）✅ 均已修復 |
| 可維護性   | 9.0/10  | 9.0/10 | **9.0/10** | ↔ 策略模式 + Profile 隔離設計仍佳 |
| 可觀測性   | 8.0/10  | 7.0/10 | **9.0/10** | ⬆ 2.0 — N-4（@AuditEvent）✅ + N-8（DISCONNECT log）✅ |
| **總分**   | **8.1/10** | **7.5/10** | **9.0/10** | ⬆ 1.5 |

**結論**：v1 列為 P2/P3/P4 優先項目（5 項）皆已落實 ✅，但本輪深入審查 STOMP / WebSocket 層後發現 **N-1 訂閱目的地未驗證屬於跨使用者資料洩漏潛在風險**，必須優先處理。其餘新議題（N+1、retention、audit、frontend 401）為可逐步補強的中低風險項目。

---

## 二、v1 議題複查

| # | v1 議題 | 狀態 | 證據 |
| - | ------- | ---- | ---- |
| P-1 | `NotificationRepository extends TenantScopedRepository`（防禦縱深）| ✅ 已修補 | [NotificationRepository.java](../../../backend/src/main/java/com/taipei/iot/notification/repository/NotificationRepository.java) 同時 extends `JpaRepository` 與 `TenantScopedRepository`；`TenantFilterAspect` 將自動 enable `tenantFilter` |
| P-2 | `page`/`size` 加上限（防 DoS）| ✅ 已修補 | [NotificationController.java](../../../backend/src/main/java/com/taipei/iot/notification/controller/NotificationController.java) `list()` / `listTodos()` 皆有 `@Min(1) @Max(100) int size` |
| P-3 | `InAppChannel.send()` for 迴圈逐筆 try-catch（單一 user 失敗不中斷）| ✅ 已修補 | [InAppChannel.java](../../../backend/src/main/java/com/taipei/iot/notification/channel/InAppChannel.java) 對 save + WebSocket push 各層皆有 try-catch，單一 userId 失敗會 log error 後繼續下一個 |
| P-4 | `NotificationResponse` 加 `@JsonInclude(NON_NULL)`（縮減 payload）| ✅ 已修補 | [NotificationResponse.java](../../../backend/src/main/java/com/taipei/iot/notification/dto/NotificationResponse.java) 已加註解；`refType` / `refId` / `readAt` 為 null 時不序列化 |
| P-5 | `EmailChannel` title / content 做 CRLF sanitize（防 email header injection）| ✅ 已修補 | [EmailChannel.java](../../../backend/src/main/java/com/taipei/iot/notification/channel/EmailChannel.java) 加 `sanitize()` private method，對 subject 與 body 都過濾 `\r\n` |
| P-6 | 前端 `notificationStore` 處理 401 | ✅ 已修補 | [notificationStore.ts](../../../frontend/src/stores/notificationStore.ts) 所有 `catch` 區塊對 401/403 re-throw；其他錯誤 `console.warn`（詳見 N-6 ✅）|
| P-7 | `EmailChannel` 採 MQ 非同步批次 | 🟠 未實施（v1 列為「建議」）| 仍為同步 for-loop + 直接 `JavaMailSender.send()`；無 `@Async` 或 MQ；詳見 N-2 / F-1 |

> **小結**：v1 列為「已修正」的 5 項（P-1 ~ P-5）**5/5 ✅ 已驗證**；列為「建議」的 2 項（P-6、P-7）尚未實施，分別併入本輪 N-6、F-1 處理建議。

---

## 三、本輪新發現問題

### 🔴 高風險

#### N-1. `StompAuthInterceptor` 僅驗證 CONNECT，未驗證 SUBSCRIBE destination — 跨使用者通知洩漏 ✅ 已修復

- **檔案**：[StompAuthInterceptor.java](../../../backend/src/main/java/com/taipei/iot/notification/websocket/StompAuthInterceptor.java)、[WebSocketConfig.java](../../../backend/src/main/java/com/taipei/iot/notification/websocket/WebSocketConfig.java)
- **問題**：interceptor 僅在 `StompCommand.CONNECT` 時驗證 JWT，**未檢查 SUBSCRIBE frame 的 destination 是否屬於該認證使用者**。
- **修復方式**：在 `StompAuthInterceptor` 的 `preSend` 中新增 `SUBSCRIBE` 命令攔截，增加 `handleSubscribe()` 方法驗證 destination prefix 必須為 `/user/{currentUserId}/queue/`，否則拋出 `MessageDeliveryException`。
- **測試**：[StompAuthInterceptorTest.java](../../../backend/src/test/java/com/taipei/iot/notification/websocket/StompAuthInterceptorTest.java)（9 個測試）：
  - `subscribe_toOwnQueue_shouldPass` — 訂閱自己的 queue 正常通過
  - `subscribe_toOtherUserQueue_shouldThrow` — 訂閱他人 queue 被拒絕
  - `subscribe_withoutAuthentication_shouldThrow` — 未認證時訂閱被拒絕
  - `subscribe_withNullDestination_shouldThrow` — 無 destination 被拒絕
  - `subscribe_toArbitraryTopic_shouldThrow` — 訂閱非 user queue 被拒絕
- **優先級**：🔴 高（已修復）

---

### 🟠 中風險

#### N-2. `EmailChannel.send()` 為 N+1 查詢 — 廣播通知時 DB 壓力倍增 ✅ 已修復

- **檔案**：[EmailChannel.java](../../../backend/src/main/java/com/taipei/iot/notification/channel/EmailChannel.java)
- **問題**：原本 for-loop 內逐筆 `findById`，1,000 收件人 = 1,000 次 DB 查詢。
- **修復方式**：改用 `userRepository.findAllById(payload.getUserIds())` 一次 batch load 所有使用者，再以 `Map<userId, UserEntity>` 迭代處理，DB 查詢降為 1 次。
- **測試**：[EmailChannelTest.java](../../../backend/src/test/java/com/taipei/iot/notification/channel/EmailChannelTest.java)（6 個測試）：
  - `send_batchLoad_shouldQueryOnceForMultipleUsers` — 驗證 3 人廣播僅呼叫 1 次 `findAllById`，且 `findById` 從未被呼叫
- **優先級**：🟠 中（已修復）

---

#### N-3. `notifications` 表無 retention / 歸檔機制 — 表持續無限成長 ✅ 已修復

- **檔案**：[V56 migration](../../../backend/src/main/resources/db/migration/V56__notification__add_archived_at_and_retention_setting.sql)、[NotificationRepository.java](../../../backend/src/main/java/com/taipei/iot/notification/repository/NotificationRepository.java)、[NotificationPurgeJob.java](../../../backend/src/main/java/com/taipei/iot/notification/job/NotificationPurgeJob.java)
- **問題**：表上無 `archived_at` 欄位、無排程清理 job，通知表會無限成長。
- **修復方式**：
  1. V56 migration：加 `archived_at TIMESTAMPTZ NULL` 欄位 + `idx_notifications_active` 部分索引 + seed `notification_retention_days` 設定至所有租戶。
  2. `NotificationEntity` 新增 `archivedAt` 欄位。
  3. `NotificationRepository` 所有查詢方法加 `ArchivedAtIsNull` 條件；新增 `archiveOldReadNotifications()` bulk update。
  4. `NotificationPurgeJob`（`@Scheduled cron = "0 30 2 * * ?"`）：遍歷所有啟用租戶，從 `system_settings` 讀取 `notification_retention_days`（預設 90 天），將超期已讀通知設 `archived_at`。
  5. `SettingKey` 新增 `NOTIFICATION_RETENTION_DAYS("notification_retention_days", "90")`。
- **測試**：[NotificationPurgeJobTest.java](../../../backend/src/test/java/com/taipei/iot/notification/job/NotificationPurgeJobTest.java)（7 個測試）+ [NotificationServiceTest.java](../../../backend/src/test/java/com/taipei/iot/notification/service/NotificationServiceTest.java)（14 個測試更新為新查詢方法）
- **優先級**：🟠 中（已修復）

---

#### N-4. `markRead` / `markAllRead` 缺 `@AuditEvent` — 無稽核軌跡 ✅ 已修復

- **檔案**：[NotificationController.java](../../../backend/src/main/java/com/taipei/iot/notification/controller/NotificationController.java)、[AuditEventType.java](../../../backend/src/main/java/com/taipei/iot/audit/enums/AuditEventType.java)、[AuditCategory.java](../../../backend/src/main/java/com/taipei/iot/audit/enums/AuditCategory.java)
- **問題**：兩個寫入端點都未掛 `@AuditEvent`，`AuditEventType` 亦無對應事件型別。其他模組（dept / user / setting）皆有完整 audit。
- **修復方式**：
  1. `AuditCategory` 新增 `NOTIFICATION("NOTIFICATION")`。
  2. `AuditEventType` 新增 `MARK_NOTIFICATION_READ`、`MARK_ALL_NOTIFICATIONS_READ`（category = `NOTIFICATION`）。
  3. Controller `markRead` 掛上 `@AuditEvent(AuditEventType.MARK_NOTIFICATION_READ)`；`markAllRead` 掛上 `@AuditEvent(AuditEventType.MARK_ALL_NOTIFICATIONS_READ)`。
- **測試**：[NotificationControllerAuditEventTest.java](../../../backend/src/test/java/com/taipei/iot/notification/controller/NotificationControllerAuditEventTest.java)（4 個測試）：
  - `markRead_shouldHaveAuditEventAnnotation` — 驗證 markRead 方法有正確 @AuditEvent 註解
  - `markAllRead_shouldHaveAuditEventAnnotation` — 驗證 markAllRead 方法有正確 @AuditEvent 註解
  - `markNotificationRead_eventType_shouldHaveCorrectCategory` — 驗證 enum category 為 NOTIFICATION
  - `markAllNotificationsRead_eventType_shouldHaveCorrectCategory` — 驗證 enum category 為 NOTIFICATION
- **優先級**：🟠 中（已修復）

---

### 🟡 低風險 / 建議

#### N-5. `NotificationService.send()` 無 `@Transactional` 且設計意圖未文件化 ✅ 已修復

- **檔案**：[NotificationService.java](../../../backend/src/main/java/com/taipei/iot/notification/service/NotificationService.java)
- **問題**：channel loop 各自捕捉例外（best-effort）為現行設計，但 class / method javadoc 未說明，新進工程師易誤加 `@Transactional` 或誤期望 all-or-nothing。
- **修復方式**：在 `send()` 方法上補完整 Javadoc，明示「best-effort，各 channel 獨立執行、獨立捕捉例外；InAppChannel 負責持久化（內部自行管理交易）；其他 channel 為外部 I/O，失敗不影響其他 channel 也不回滾」。
- **測試**：[NotificationServiceTest.java](../../../backend/src/test/java/com/taipei/iot/notification/service/NotificationServiceTest.java)（新增 3 個測試）：
  - `send_shouldNotHaveTransactionalAnnotation` — 反射驗證 send() 方法無 @Transactional 註解
  - `send_shouldNotRollbackOtherChannelsOnFailure` — 中間 channel 失敗，前後 channel 仍被呼叫
  - `send_shouldContinueEvenIfAllChannelsFail` — 所有 channel 失敗也不拋出例外
- **優先級**：🟡 低（已修復）

#### N-6. 前端 `notificationStore` 靜默吞掉所有錯誤（含 401） ✅ 已修復

- **檔案**：[notificationStore.ts](../../../frontend/src/stores/notificationStore.ts)
- **問題**：所有 `catch` 區塊為空（`// silently fail`）；401/403 被吞掉，axios interceptor 完全不會被觸發，使用者 token 過期時不會被自動踢出；60 秒 polling 持續發出無效請求。
- **修復方式**：四個 async 方法（`fetchUnreadCount`、`fetchItems`、`markRead`、`markAllRead`）的 catch 區塊改為：
  - 若 `err.response.status` 為 401 或 403，重新拋出錯誤（讓 axios interceptor 接手處理 refresh/logout）
  - 其他錯誤以 `console.warn('[notification] xxx failed', err)` 記錄
- **測試**：[notificationStore.test.ts](../../../frontend/src/__tests__/stores/notificationStore.test.ts)（新增 9 個測試）：
  - `fetchUnreadCount should re-throw on 401` / `on 403` / `should not throw on 500`
  - `fetchItems should re-throw on 401` / `should not throw on 500`
  - `markRead should re-throw on 401` / `should not throw on 500`
  - `markAllRead should re-throw on 403` / `should not throw on network error without response`
- **優先級**：🟡 低（已修復）

#### N-7. `markRead` 並發 race — 已有 `if (Boolean.FALSE.equals(read))` 保護，但 `readAt` 可能被覆寫 ✅ 已修復

- **檔案**：[NotificationRepository.java](../../../backend/src/main/java/com/taipei/iot/notification/repository/NotificationRepository.java)、[NotificationService.java](../../../backend/src/main/java/com/taipei/iot/notification/service/NotificationService.java)
- **問題**：兩個並發請求都通過 read=false 判斷，最後 `readAt` 會以後到的 timestamp 為準。稽核要求嚴格「首次讀取時間」時會不準確。
- **修復方式**：
  1. `NotificationRepository` 新增 `markReadAtomic(id, userId)` — JPQL `@Modifying` 原子更新，僅當 `read=false` 時才設定 `readAt`，並發情境下只有第一個請求會生效。
  2. `NotificationService.markRead()` 改為先驗證權限（findById + userId 比對），再呼叫 `markReadAtomic` 執行原子更新，移除舊的 `entity.setRead()` + `save()` 模式。
- **測試**：[NotificationServiceTest.java](../../../backend/src/test/java/com/taipei/iot/notification/service/NotificationServiceTest.java)（更新 4 個測試）：
  - `markRead_shouldCallAtomicUpdate` — 驗證呼叫 markReadAtomic 且不呼叫 save
  - `markRead_shouldThrowWhenNotFound` — 通知不存在時拋出異常
  - `markRead_shouldThrowWhenUserMismatch` — 非擁有者時拋出異常
  - `markRead_shouldBeIdempotentWhenAlreadyRead` — 已讀時原子更新返回 0，不拋出異常
- **優先級**：🟡 低（已修復）

#### N-8. STOMP 缺 DISCONNECT / UNSUBSCRIBE 觀測 hook ✅

- **檔案**：[StompAuthInterceptor.java](../../../backend/src/main/java/com/taipei/iot/notification/websocket/StompAuthInterceptor.java)
- **問題**：無 DISCONNECT 記錄；運維難以分析「使用者連線分布 / 平均存活時間」。
- **建議**：補 `DISCONNECT` 時 `log.info("ws disconnect user={}", userId)`，必要時可寫入 audit。
- **修復方式**：在 `preSend` 中增加 `DISCONNECT` 命令處理分支，呼叫 `handleDisconnect(accessor)` 方法記錄 `log.info("WebSocket disconnect user={}", userId)`；無使用者時記錄 `"WebSocket disconnect (unauthenticated session)"`。
- **測試**：2 個新測試（`disconnect_withAuthenticatedUser_shouldLogAndPassThrough`、`disconnect_withoutUser_shouldLogAndPassThrough`），全部通過。
- **優先級**：🟡 低（已修復）

#### N-9. CORS / WebSocket allowedOrigins 配置需明確 ✅ 已修復

- **檔案**：[WebSocketConfig.java](../../../backend/src/main/java/com/taipei/iot/notification/websocket/WebSocketConfig.java)、`application.yml` `cors.allowed-origins`
- **問題**：`setAllowedOrigins(allowedOrigins)` 安全與否完全取決於外部設定。建議：
  - 啟動時驗證 `allowedOrigins` 不含 `*`，否則 fail-fast。
  - 文件化生產環境必填項。
- **修復方式**：在 `WebSocketConfig` 加入 `@PostConstruct validateAndLog()` 方法，啟動時驗證 `allowedOrigins` 不為 null/空且不含 `*`，否則拋出 `IllegalStateException` fail-fast；同時 `log.info` 記錄生效的 origins 供運維確認。
- **測試**：[WebSocketConfigCorsTest.java](../../../backend/src/test/java/com/taipei/iot/notification/websocket/WebSocketConfigCorsTest.java)（6 個測試）：
  - `validOrigins_shouldPassValidation` — 單一有效 origin 通過
  - `multipleValidOrigins_shouldPassValidation` — 多個有效 origin 通過
  - `wildcardOrigin_shouldFailFast` — `*` 時 fail-fast
  - `wildcardAmongValidOrigins_shouldFailFast` — 混入 `*` 時 fail-fast
  - `nullOrigins_shouldFailFast` — null 時 fail-fast
  - `emptyOrigins_shouldFailFast` — 空陣列時 fail-fast
- **優先級**：🟡 低（已修復）

---

## 四、安全性總結（OWASP 對照）

| 面向 | 評估 | 摘要 |
|------|------|------|
| A01 — Broken Access Control | ✅ | REST 層 `markRead` 有 IDOR 防護；**WebSocket SUBSCRIBE 已驗證（N-1 ✅）** |
| A02 — Cryptographic Failures | ✅ | JWT 簽驗完整、無敏感資料明文落 log |
| A03 — Injection | ✅ | Email header CRLF 已 sanitize（P-5）；JPQL 無動態拼接 |
| A04 — Insecure Design | ✅ | EmailChannel batch load 已修復（N-2）；通知歸檔機制已加入（N-3）|
| A05 — Security Misconfiguration | ✅ | CORS allowedOrigins 啟動時 fail-fast 驗證（N-9 ✅）|
| A07 — Identification & Authn | ✅ | JWT + STOMP CONNECT 驗證完整 |
| A09 — Security Logging & Monitoring | ✅ | `markRead` / `markAllRead` 已有 audit（N-4 ✅）；前端 401/403 已正確 re-throw（N-6 ✅）|
| A10 — SSRF | n/a | 無外部 URL fetch |

---

## 五、值得優化的功能建議

| # | 功能 | 價值 | 概要 |
| - | ---- | ---- | ---- |
| F-1 | **Email / SMS 非同步 MQ 寄送（呼應 v1 P-7、N-2）** | 效能 ★★★ | 引入 Kafka / RabbitMQ topic `notification.email`；EmailChannel 改為發 message；獨立 worker consume；天然支援 retry + dead letter |
| F-2 | **使用者通知偏好（per-channel opt-in/out）** | UX ★★ | 新表 `user_notification_preference(userId, channel, enabled, refType)`；channel 內部統一檢查 |
| F-3 | **通知保留 / 歸檔排程（呼應 N-3）** | 維運 ★★★ | `archived_at` 欄位 + 每日 `@Scheduled` 清理 > N 天的已讀；N 由 `system_settings` 控管 |
| F-4 | ~~未讀數即時 push（取代 60 秒 polling）~~ | ~~UX + 效能 ★★~~ | ❌ 不再需要 — 現行架構已滿足需求 |
| F-5 | **通知去重 / grouping** | UX ★★ | 加 `group_key`；同 group 新通知到時舊通知 archived；UI 顯示「（3 則）同類訊息」|
| F-6 | **重大通知保證送達（dead letter queue）** | 安全 ★★ | 對 ALERT 類通知導入重試 + DLQ；管理員儀表板可查看失敗清單並重送 |
| F-7 | **STOMP session 觀測 dashboard** | 運維 ★ | 從 N-8 延伸：DISCONNECT 寫表 + Prometheus metric `ws_active_sessions{tenantId}` |
| F-8 | **通知偏好對應 i18n / 模板化** | 國際化 ★ | 通知標題 / 內容支援 placeholder + locale；每個 user 收到對應其偏好語言的通知 |

---

## 六、修復路線圖建議

### Sprint 1 — 立即（資安阻斷項）
1. **N-1** — ✅ 已修復。`StompAuthInterceptor` 新增 `handleSubscribe()` 驗證 SUBSCRIBE destination 必須為 `/user/{currentUserId}/queue/`；測試 9 個全過。
2. **N-4** — ✅ 已修復。`AuditCategory` 新增 `NOTIFICATION`；`AuditEventType` 新增 `MARK_NOTIFICATION_READ` / `MARK_ALL_NOTIFICATIONS_READ`；Controller 掛上 `@AuditEvent`；測試 4 個全過。

### Sprint 2 — 效能與運維
3. **N-2** — ✅ 已修復。`EmailChannel` 改 `findAllById` batch load，消除 N+1；測試 6 個全過。
4. **N-3 + F-3** — ✅ 已修復。V56 migration 加 `archived_at` + 部分索引；`NotificationPurgeJob` 每日歸檔超過 `notification_retention_days`（預設 90 天，可由 `system_settings` 設定）的已讀通知；測試 7 個全過。

### Sprint 3 — UX 與細部
5. **N-6** — ✅ 已修復。前端 `notificationStore` 對 401/403 re-throw；其他錯誤 `console.warn`；測試 9 個全過。
6. **N-5** — ✅ 已修復。`send()` 方法補 Javadoc 明示 best-effort 設計意圖；測試 3 個全過。
7. **N-7 / N-8 / N-9** — N-7 ✅ 已修復（原子 markRead，測試 4 個全過）；N-8 ✅ 已修復（DISCONNECT log，測試 2 個全過）；N-9 ✅ 已修復（WebSocket CORS fail-fast 驗證，測試 6 個全過）。

### Sprint 4+ — 架構升級
7. **F-1** — 引入 MQ；EmailChannel 改為 publisher + worker。
8. ~~**F-4**~~ — ❌ 不再需要。
9. **F-2 / F-5 / F-6 / F-7 / F-8** — 依產品優先級排入 backlog。

---

## 七、附錄：本次複查涵蓋的檔案

### Backend
- [NotificationController.java](../../../backend/src/main/java/com/taipei/iot/notification/controller/NotificationController.java)
- `notification/service/NotificationService.java`
- [NotificationRepository.java](../../../backend/src/main/java/com/taipei/iot/notification/repository/NotificationRepository.java)
- `notification/entity/NotificationEntity.java`
- [NotificationResponse.java](../../../backend/src/main/java/com/taipei/iot/notification/dto/NotificationResponse.java)
- `notification/dto/NotificationPayload.java`、`notification/dto/UnreadCountResponse.java`
- `notification/enums/NotificationType.java`、`notification/enums/NotificationRefType.java`
- [InAppChannel.java](../../../backend/src/main/java/com/taipei/iot/notification/channel/InAppChannel.java)
- [EmailChannel.java](../../../backend/src/main/java/com/taipei/iot/notification/channel/EmailChannel.java)
- `notification/channel/NoOpEmailChannel.java`、`notification/channel/NoOpSmsChannel.java`、`notification/channel/NotificationChannel.java`
- [StompAuthInterceptor.java](../../../backend/src/main/java/com/taipei/iot/notification/websocket/StompAuthInterceptor.java)
- [WebSocketConfig.java](../../../backend/src/main/java/com/taipei/iot/notification/websocket/WebSocketConfig.java)

### 測試
- `backend/src/test/java/com/taipei/iot/notification/controller/NotificationControllerTest.java`
- `backend/src/test/java/com/taipei/iot/notification/service/NotificationServiceTest.java`
- `backend/src/test/java/com/taipei/iot/notification/channel/InAppChannelTest.java`
- `backend/src/test/java/com/taipei/iot/notification/channel/EmailChannelTest.java`
- `backend/src/test/java/com/taipei/iot/notification/channel/NoOpEmailChannelTest.java`
- `backend/src/test/java/com/taipei/iot/notification/channel/NoOpSmsChannelTest.java`

### DB Migration
- [V31__notify_create_notifications.sql](../../../backend/src/main/resources/db/migration/V31__notify_create_notifications.sql)

### 跨模組整合
- [TenantFilterAspect.java](../../../backend/src/main/java/com/taipei/iot/tenant/TenantFilterAspect.java)
- [AuditEventType.java](../../../backend/src/main/java/com/taipei/iot/audit/enums/AuditEventType.java)
- `application.yml` / `application-dev.yml` / `application-test.yml`（`cors.allowed-origins`、`spring.mail.*`）

### 前端
- [notificationStore.ts](../../../frontend/src/stores/notificationStore.ts)
- [NotificationBell.vue](../../../frontend/src/components/NotificationBell.vue)
- [api/notification/index.ts](../../../frontend/src/api/notification/index.ts)
- `frontend/src/types/notification.ts`
- [axiosIns.ts](../../../frontend/src/api/axios/axiosIns.ts)（refresh / logout interceptor，與 N-6 相關）
