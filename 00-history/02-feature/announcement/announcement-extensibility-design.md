# Announcement 功能模組「易擴展」設計指南

> 對象：`com.taipei.iot.announcement` 後端模組
> 目的：說明如何設計、以及有哪些具體作法，讓公告（Announcement）模組能「易擴展」——在最少修改、不破壞既有行為的前提下，加入新的公告分類、新的受眾／可見範圍規則、新的內容格式、新的派送通道（站內／Email／推播／Webhook）、新的狀態與生命週期。

---

## 1. 什麼叫「易擴展」？（先定義目標）

對 announcement 模組而言，「易擴展」要滿足以下可衡量目標：

| 目標 | 白話描述 | 衡量方式 |
|------|----------|----------|
| **開放封閉（OCP）** | 新增分類／受眾規則／通道時「只新增類別」，不改既有核心 | 加一種受眾規則所需修改的既有檔案數趨近 0（現況需改 5+ 處） |
| **單一職責（SRP）** | 受眾解析、內容處理、派送、讀取追蹤各自獨立 | `AnnouncementService`（現 ~900 行）能拆小、互不牽動 |
| **可設定（Configurable）** | 分類顏色、附件白名單、語言清單由設定驅動 | 改設定不需重編譯 |
| **可組合（Composable）** | 「建立公告」後的副作用（通知／稽核／快取失效）可插拔 | 新增一個副作用不改建立流程 |
| **可測試（Testable）** | 每個擴展點可獨立 mock 測試 | 受眾規則、通道可單測 |
| **多租戶安全** | 公告／附件以租戶隔離，跨租戶不外洩 | 受眾解析永遠帶 tenant 條件 |

---

## 2. 現況盤點：已經做對的事

announcement 模組在資料層與查詢效能上已有不少良好設計，這些是擴展的基礎：

### 2.1 模組結構

```
announcement/
├── entity/      Announcement, AnnouncementTranslation, AnnouncementAttachment,
│                AnnouncementRead, AnnouncementDept(+Id),
│                AnnouncementStatus / AnnouncementScope / AnnouncementCategory (enum)
├── repository/  6 個 Repository（含 StatsRepository）
├── service/     AnnouncementService(~900行), AnnouncementAttachmentService,
│                AnnouncementReadService, HtmlSanitizerService
├── controller/  AnnouncementController（單一 controller，20+ 端點）
└── dto/         Request / Response / TranslationDto / PinOrderRequest ...
```

### 2.2 已具備的良好擴展性

| 設計 | 說明 | 對擴展的意義 |
|------|------|-------------|
| **列舉驅動分類** | `AnnouncementCategory`（GENERAL/SYSTEM/POLICY/EVENT/MAINTENANCE） | 加分類只需加列舉值，前端依分類給標籤色 |
| **i18n 子表 + fallback** | 主表存預設語言（zh-TW），`AnnouncementTranslation` 存其他語言；`pickTranslation` 走「要求語言 → 預設語言」fallback | 加語言走 `LangNormalizer`，邏輯集中 |
| **HTML 白名單淨化** | `HtmlSanitizerService` 用 OWASP `PolicyFactory` 白名單 | 內容安全策略集中可調 |
| **批次載入防 N+1** | `toResponseList()` 一次載入 depts/reads/attachments/translations | 擴展欄位時沿用批次模式 |
| **樂觀鎖** | `@Version`，編輯衝突回 409 | 併發安全 |
| **多租戶自動過濾** | `@Filter(tenantFilter)` + `tenant_id` | 受眾解析天然帶租戶隔離 |
| **附件白名單可設定** | `announcement.attachments.allowed-extensions` | 不改碼即可調白名單 |

> **結論：資料模型與查詢底子不錯，但「行為的擴展點」不足——受眾、狀態、副作用都偏硬編碼。本文件聚焦補上這些擴展點。**

---

## 3. 擴展維度：會被擴展的是哪些東西？

| 維度 | 範例需求 | 現況痛點 |
|------|----------|----------|
| **A. 受眾／可見範圍** | 除 ALL/DEPT，再加 ROLE / USER_GROUP / TENANT_WIDE / CUSTOM 條件 | `AnnouncementScope` 硬編碼，查詢與決策散在 5+ 處 |
| **B. 公告分類／類型行為** | 不同類型有不同欄位、不同預設、不同必填（如維護公告要起訖時間） | 分類僅驅動顏色，無「類型行為」抽象 |
| **C. 狀態／生命週期** | 加 ARCHIVED、PENDING_APPROVAL（需審核）、RECALLED（撤回） | 狀態與「SCHEDULED/EXPIRED」計算邏輯寫死在 SQL |
| **D. 派送通道** | 發佈時同步寄 Email／推播／WebSocket／Webhook | **完全沒有事件發佈**，無法掛副作用 |
| **E. 內容格式** | 純文字、Markdown、富文本、外部連結卡片 | 內容處理（sanitize/extractText）綁死 HTML |
| **F. 副作用** | 建立／更新／刪除時：通知、稽核、快取失效、搜尋索引 | 無事件，要加就得改 service 主流程 |

---

## 4. 設計手法（Patterns & Practices）

### 手法 1：受眾以「策略 + 規格」抽象 — 維度 A（**最高優先**）

**問題**：`AnnouncementScope` 只有 `ALL | DEPT`。要加 ROLE/GROUP，得改列舉、改 `listVisible/listAdmin/getById/create/update` 五處查詢與決策、再加 junction 表 SQL。這是目前最大的擴展瓶頸。

**做法**：引入 `AudienceResolver` 策略集合，把「誰看得到」抽象成可插拔規則。

```java
public interface AudienceRule {
    AudienceType type();                          // ALL, DEPT, ROLE, GROUP, USER...
    /** 寫入：把目標（部門/角色/群組 id）存進對應 junction */
    void persistTargets(Long announcementId, AnnouncementRequest req);
    /** 讀取：產生「此公告對某使用者是否可見」的查詢條件（Specification / 述詞） */
    Specification<Announcement> visibilityPredicate(UserContext user);
}

@Component class AllAudienceRule   implements AudienceRule { type(){return ALL;} ... }
@Component class DeptAudienceRule  implements AudienceRule { type(){return DEPT;} ... }
// 未來新增——不動既有檔：
@Component class RoleAudienceRule  implements AudienceRule { type(){return ROLE;} ... }
@Component class GroupAudienceRule implements AudienceRule { type(){return GROUP;} ... }
```

`AudienceResolver` 聚合所有規則（Spring 注入 `List<AudienceRule>`），查詢可見公告時把各規則的述詞 OR 起來：

```java
Specification<Announcement> visible = rules.stream()
    .map(r -> r.visibilityPredicate(user))
    .reduce(Specification::or).orElse(Specification.where(null));
```

> **效益**：新增受眾類型 = 新增一個 `AudienceRule` 類別 + 一張（或復用）目標表，查詢主流程零修改。建議查詢層改用 **JPA `Specification`**（取代散落的 `findXxx` 客製 SQL），讓可見性條件可組合。

---

### 手法 2：公告類型的「型別策略」— 維度 B、C

**問題**：分類目前只決定前端顏色，沒有承載「類型差異化行為」（必填欄位、預設值、生命週期）。

**做法**：為需要差異化的類型引入 `AnnouncementTypeHandler`。

```java
public interface AnnouncementTypeHandler {
    AnnouncementCategory category();
    default void onValidate(AnnouncementRequest req) {}   // 類型專屬驗證（如維護公告必填起訖）
    default void onBeforeCreate(Announcement entity) {}    // 套預設值
    default Set<AnnouncementStatus> allowedStatuses() { return DEFAULT_LIFECYCLE; }
}
```

- 一般公告用預設實作（不必新增類別）。
- `MaintenanceTypeHandler` 可強制 `expireAt` 必填、預設 `requiresAck=true`。

**狀態擴展**：把「計算狀態」（SCHEDULED/EXPIRED）從 SQL 抽成 `AnnouncementStatusCalculator`，讓新增 ARCHIVED/RECALLED 時集中一處修改，而非散落查詢。

---

### 手法 3：派送通道責任鏈 / 事件驅動 — 維度 D、F（**強烈建議**）

**問題**：模組**沒有任何事件發佈**，與 common 模組既有 `ApplicationEventPublisher` 慣例不符。導致「發佈公告 → 通知使用者」「更新 → 失效快取」「刪除 → 清搜尋索引」全部無處可掛，只能塞進 `AnnouncementService` 主流程。

**做法**：發佈領域事件，副作用以監聽器擴展。

```java
// 1. 領域事件
public record AnnouncementPublishedEvent(Long id, String tenantId, AudienceSnapshot audience) {}

// 2. service 只負責發事件（交易提交後）
publisher.publishEvent(new AnnouncementPublishedEvent(id, tenantId, audience));

// 3. 副作用各自獨立、可插拔（@TransactionalEventListener(phase = AFTER_COMMIT)）
@Component class AnnouncementNotificationListener { // 呼叫 notification 模組（站內/Email/推播）
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void on(AnnouncementPublishedEvent e) { ... }
}
@Component class AnnouncementCacheEvictListener { ... }
@Component class AnnouncementSearchIndexListener { ... }
```

**派送通道再抽一層**，讓「通道」本身也可插拔：

```java
public interface DeliveryChannel {
    ChannelType type();                       // IN_APP, EMAIL, PUSH, WEBHOOK
    boolean enabled(String tenantId);         // 逐租戶開關
    void deliver(AnnouncementPublishedEvent e, Recipients r);
}
@Component @ConditionalOnProperty("announcement.delivery.email.enabled")
class EmailDeliveryChannel implements DeliveryChannel { ... }
```

> **效益**：
> - 加通道（Webhook/SMS）= 新增一個 `DeliveryChannel`，不動公告核心。
> - 副作用解耦，建立公告不再因「順便要通知」而膨脹。
> - `AFTER_COMMIT` 確保「公告真的存進去了才通知」，避免回滾仍發信。

---

### 手法 4：內容格式以「處理器」抽象 — 維度 E

**問題**：內容處理綁死 HTML（`HtmlSanitizerService.sanitize` + `extractText`）。要支援 Markdown／純文字／連結卡片需改寫。

**做法**：抽 `ContentProcessor`，依 `contentFormat` 選用。

```java
public interface ContentProcessor {
    ContentFormat format();                  // HTML, MARKDOWN, PLAIN
    ProcessedContent process(String raw);    // 回傳 {safeContent, plainText}
}
@Component class HtmlContentProcessor implements ContentProcessor { /* 包現有 sanitizer */ }
@Component class MarkdownContentProcessor implements ContentProcessor { /* MD→sanitized HTML */ }
```

> 主表加 `content_format` 欄位即可漸進導入；既有資料預設 HTML，向後相容。

---

### 手法 5：拆分巨型 Service（SRP）

`AnnouncementService` ~900 行，混合查詢／權限／受眾／翻譯／置頂。建議沿著上述擴展點拆分：

```
AnnouncementCommandService  ← create/update/delete/reorderPins（編排）
AnnouncementQueryService    ← listVisible/listAdmin/getById（用 AudienceResolver 組述詞）
AudienceResolver            ← 受眾讀寫（手法 1）
AnnouncementTranslationService ← i18n 維護
（既有）AttachmentService / ReadService / HtmlSanitizerService
```

> 拆分本身不是為拆而拆，而是讓每個擴展點落在獨立、可單測的類別，避免改一個功能波及全檔。

---

## 5. 共通的工程實踐（讓擴展不出錯）

| 實踐 | 作法 | 為何重要 |
|------|------|----------|
| **設定外部化** | 分類顏色映射、附件白名單、語言清單、通道開關走設定/系統設定 | 不改碼即可調整 |
| **特性開關** | 通道、type handler 加 `@ConditionalOnProperty` | 逐租戶/逐環境灰度 |
| **事務後事件** | 副作用用 `@TransactionalEventListener(AFTER_COMMIT)` | 避免回滾仍送通知 |
| **可組合查詢** | 受眾可見性改用 JPA `Specification` | 新增規則 OR 進去即可 |
| **多租戶帶條件** | 受眾解析永遠帶 tenant；`AnnouncementRead` 無 tenant_id 故 native SQL 顯式傳入 | 防跨租戶外洩 |
| **內容安全** | 任何新內容格式最終都過 sanitize 白名單 | 防 XSS |
| **附件安全一致** | 新通道/格式仍走 `FileValidationService`（副檔名+magic bytes+大小）與安全下載 header | 防惡意檔 |
| **稽核一致** | 維持 `@AuditEvent`；新副作用透過事件而非散落 | 軌跡完整 |
| **可測試** | 每個 `AudienceRule`/`DeliveryChannel`/`ContentProcessor` 單測 | 擴展有回歸保護 |

---

## 6. 目前缺口與優先順序

| 缺口 | 現況 | 風險 | 建議優先級 |
|------|------|------|-----------|
| 受眾範圍硬編碼 | `Scope` 僅 ALL/DEPT，邏輯散在 5+ 處 | 加 ROLE/GROUP 改動面大、易漏 | ★★★（先做） |
| 無領域事件/副作用擴展點 | 無 `ApplicationEventPublisher` | 通知/快取/索引無處可掛 | ★★★ |
| 派送通道不可插拔 | 無通道抽象 | 加 Email/推播/Webhook 要改主流程 | ★★★ |
| 巨型 Service | `AnnouncementService` ~900 行 | 改動相互牽連、難測 | ★★☆ |
| 內容格式綁死 HTML | sanitize/extractText 寫死 | 加 Markdown 需改寫 | ★★☆ |
| 計算狀態寫死 SQL | SCHEDULED/EXPIRED 在查詢內 | 加 ARCHIVED/RECALLED 改多處 | ★☆☆ |
| 分類僅驅動顏色 | 無類型行為抽象 | 類型差異化需求出現時受限 | ★☆☆ |

---

## 7. 落地檢查清單

### 7.1 新增一種「受眾規則」（例如 ROLE）
- [ ] `AudienceType` 加 `ROLE`
- [ ] 新增 `RoleAudienceRule implements AudienceRule`（`@Component`）
- [ ] 新增/復用目標表（如 `announcement_roles`）
- [ ] 確認 `visibilityPredicate` 帶 tenant 條件
- [ ] 單測：寫入目標、可見性命中/未命中
- [ ] 前端受眾選項擴充（資料驅動）

### 7.2 新增一種「派送通道」（例如 Webhook）
- [ ] `ChannelType` 加 `WEBHOOK`
- [ ] 新增 `WebhookDeliveryChannel implements DeliveryChannel`（`@ConditionalOnProperty`）
- [ ] 監聽 `AnnouncementPublishedEvent`（`AFTER_COMMIT`）
- [ ] 逐租戶開關 + 設定（URL/secret 加密儲存）
- [ ] 單測：事件→派送、停用時不派送

### 7.3 新增一種「內容格式」（例如 Markdown）
- [ ] `ContentFormat` 加 `MARKDOWN`；主表加 `content_format`
- [ ] 新增 `MarkdownContentProcessor`（輸出仍過 sanitize）
- [ ] 既有資料預設 `HTML`（向後相容）
- [ ] 單測：XSS 注入被擋

> 若上述任一新增**被迫修改 `AnnouncementService` 的核心查詢/建立分支**，代表該擴展點尚未抽出，應回頭補上手法 1~4 的抽象。

---

## 8. 一句話總結

> announcement 模組**資料模型與查詢效能底子良好**（列舉分類、i18n fallback、批次防 N+1、樂觀鎖、多租戶過濾），
> 但**行為層的擴展點不足**：受眾範圍硬編碼、無領域事件、派送通道與內容格式綁死、單一巨型 Service。
> 建議優先補上 **受眾策略（AudienceRule + Specification）**、**領域事件 + 派送通道（DeliveryChannel，AFTER_COMMIT）**、**內容處理器（ContentProcessor）**，並拆分 Service，
> 全程貫徹「設定外部化、特性開關、事務後事件、多租戶帶條件、內容安全白名單」，即可在不動核心流程的前提下持續演進。
