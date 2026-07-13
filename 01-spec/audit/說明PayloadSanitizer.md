這個 `PayloadSanitizer.java` 是審計日誌系統中負責 **「資料安全（脫敏）」** 與 **「系統穩定性（防撐爆）」** 的關鍵工具類別。

它的主要職責是：**將 API 方法的輸入參數（Payload）轉換為 JSON 格式，自動將密碼、Token 等敏感欄位替換為 `***`，並限制最大長度，最後安全地交給非同步執行緒寫入資料庫。**

以下為您詳細拆解這個 Class 的設計理念與核心機制：

### 1. 核心職責與解決的痛點
在記錄 API 審計日誌時，我們通常需要把請求的參數（Payload）記錄下來以便日後排查問題。但這會帶來兩個致命痛點：
1.  **安全合規痛點**：參數中可能包含使用者的明文密碼、JWT Token、API Secret 等。如果直接寫入日誌，一旦日誌系統被入侵或日誌被印出到 Console，就會造成嚴重的**資安外洩**。
2.  **系統穩定痛點**：某些 API 的參數可能非常大（例如：Base64 編碼的圖片、超長的文字備註、龐大的 JSON 陣列）。如果直接寫入，可能會**撐爆資料庫的 VARCHAR 欄位**，或導致日誌系統（如 ELK）效能崩潰。

`PayloadSanitizer` 就是為了完美解決這兩個痛點而誕生的。

---

### 2. 核心機制與程式碼解析

#### ① 高效的 JSON 處理引擎 (Jackson ObjectMapper)
```java
private static final ObjectMapper MAPPER;
static {
    MAPPER = new ObjectMapper();
    MAPPER.registerModule(new JavaTimeModule()); // 支援 Java 8 時間類型
    MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 日期輸出為字串而非時間戳
}
```
*   **設計亮點**：`ObjectMapper` 的創建是相對耗費資源的，且它是**執行緒安全**的。因此將其宣告為 `static final` 並在靜態初始化塊中配置，確保整個應用生命周期中只創建一次，大幅提升效能。
*   **JavaTimeModule**：確保像 `LocalDateTime` 這樣的 Java 8 時間物件能被正確序列化為 `2023-10-25T10:00:00` 格式，而不是奇怪的陣列。

#### ② 遞迴式敏感資訊脫敏 (Recursive Masking)
```java
private static void maskSensitiveFields(JsonNode node) { ... }
private static boolean isSensitive(String fieldName) { ... }
```
*   **運作原理**：它將參數轉為 JSON 樹（`JsonNode`）後，進行**深度優先的遞迴遍歷**。無論是深層巢狀的 Object 還是 Array 裡面的元素，都不放過。
*   **黑名單關鍵字匹配**：`isSensitive` 方法定義了敏感關鍵字（轉小寫後比對）：
    *   包含：`password` (密碼), `secret` (密鑰), `token` (權杖)。
    *   等於：`authorization` (HTTP 認證標頭)。
*   **脫敏動作**：一旦匹配到敏感欄位，就將其值強制替換為 `MASK`（即 `"***"`）。

#### ③ 長度截斷保護 (Truncation)
```java
if (result.length() > MAX_LENGTH) {
    return result.substring(0, MAX_LENGTH);
}
```
*   **設計亮點**：設定 `MAX_LENGTH = 2000`。如果脫敏後的 JSON 字串超過 2000 個字元，直接硬截斷。
*   **目的**：確保寫入資料庫 `user_event_log.payload` 欄位時，絕對不會因為資料過長而拋出 `Data truncation` 例外，保護主流程不受影響。

#### ④ 容錯機制 (Fail-Safe / Best-Effort)
```java
catch (Exception e) {
    return "[sanitize-error]";
}
```
*   **設計原則**：脫敏只是一個「輔助/安全」功能。如果因為某些奇葩的參數結構導致 Jackson 序列化失敗，**絕對不能拋出例外中斷了 AOP 的執行**。
*   **實作**：捕獲所有例外，並返回一個安全的佔位符 `"[sanitize-error]"`。這確保了審計日誌的「可用性」優先於「完美性」。

---

### 3. 在 Spring Boot 知識圖譜中的定位

回顧我們之前討論的 `BaseLoggerAspect`，`PayloadSanitizer` 的精確位置如下：

```text
[BaseLoggerAspect.logApiCall()]
   │
   ├─ 【Before】抓取 ThreadLocal 上下文
   │
   ├─ 【Proceed】執行 Controller/Service 業務邏輯
   │
   └─ 【Finally】 (在主執行緒中執行，因為需要讀取 pjp.getArgs())
        │
        ├─ 計算 executionTime
        │
        ├─ ⭐️ 呼叫 PayloadSanitizer.sanitize(pjp.getArgs()) 
        │   (將方法參數轉 JSON -> 遞迴脫敏 -> 截斷長度)
        │
        └─ 呼叫 auditAsyncWriter.saveAsync(..., payload, ...) 
            (將乾淨的 payload 丟給非同步執行緒寫入 DB)
```

**💡 關鍵細節**：
`PayloadSanitizer` 是在 **AOP 的 `finally` 區塊（主執行緒）** 中執行的，而不是在 `@Async` 的背景執行緒中。這是因為 `pjp.getArgs()`（方法的原始參數物件）的生命週期與當前 HTTP 請求綁定，必須在主執行緒結束前完成序列化與脫敏，然後再把處理好的「純字串 (String)」傳遞給非同步執行緒。

---

### 4. 實務上的潛在優化建議 (進階探討)

雖然這個工具類別已經寫得非常穩健，但在企業級實務中，這種「基於關鍵字黑名單」的脫敏方式有一些可以優化的空間：

1.  **誤殺問題 (False Positives)**：
    *   如果有一個欄位叫 `userPasswordCount`（使用者密碼錯誤次數），因為包含 `password`，它也會被脫敏成 `***`。雖然不影響系統運作，但會損失一點日誌的可讀性。
2.  **進階解法：搭配自定義註解 (Annotation-based)**：
    *   更精確的做法是定義一個 `@Sensitive` 註解。
    *   在 DTO 或 Request 物件的欄位上標記：`@Sensitive private String myCustomSecret;`。
    *   然後在 `PayloadSanitizer` 中，不依賴欄位名稱，而是透過 Jackson 的 `BeanProperty` 或反射去讀取 `@Sensitive` 註解來決定是否脫敏。這樣可以做到 100% 精準控制。
    *   *(註：目前的關鍵字匹配法已經能涵蓋 90% 以上的常見場景，且實作成本最低，對於初中期的審計系統來說是非常好的平衡點。)*

### 總結
`PayloadSanitizer` 是一個極具**防禦性程式設計（Defensive Programming）** 思維的工具類別。它透過 **Jackson 遞迴解析**、**關鍵字黑名單**、**長度截斷** 以及 **例外吞噬**，確保了審計日誌在記錄豐富參數的同時，既能守住資安底線，又能維持系統的絕對穩定。