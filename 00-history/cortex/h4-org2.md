h4.md 的分析框架是正確的，但我對實作路徑有不同看法。

---

## 我的核心觀點：Phase 5 的優先順序應該倒過來

### 最高 ROI：維度 3（Exception/Log 萃取）先做

這是**唯一能自動化且不需要人工維護**的維度。你的 codebase 已經有 tree-sitter 在跑，只需要在 `tree-sitter-analyzer.py` 追加一個 visitor，掃描：

```java
throw new BusinessException("VIP 折扣不能與優惠券疊加");
// 或
log.warn("庫存不足，拒絕結帳: orderId={}", orderId);
```

這些字串**就是業務規則的程式碼形式**。AST 能精確定位它們在哪個 class、哪個 method — 這正是 tree-sitter 的強項。維度 3 的自動化程度最高，零人工成本。

---

### 中等 ROI：維度 1（Markdown 規則庫）**有個現實問題**

> **沒有人會維護它。**

這是業界 99% 的 Markdown 知識庫的死法。規則庫在第一個月很漂亮，三個月後就跟程式碼脫節了，變成比沒有更危險（AI 會讀到過期規則）。

**務實的替代方案**：不要從零建，而是從現有的三個地方萃取：
1. **Flyway migration 的 SQL comment** — `V29__announcement__create_tables.sql` 裡的 `COMMENT` 就是業務語意
2. **Swagger `@Schema(description = "...")` 和 `@Operation`** — 你的程式碼已經有很多了
3. **測試方法名稱** — `should_throw_when_vip_and_coupon_combined()` 就是活文件

這三個都是**有人維護的活資料**，不會跟程式碼脫節。

---

### 低 ROI（暫緩）：維度 2（ADR）

ADR 對小團隊的邊際效益低。你現在更需要的是讓 AI 讀懂**當前系統的約束**，而不是「為什麼當年選了 RabbitMQ」。

---

## 我建議的實際執行順序

```
Phase 5a（本週）：code_constraints 表 + tree-sitter 萃取 Exception/Log 訊息
Phase 5b（下週）：從 @Schema/@Operation + 測試方法名 自動填充 business_rules
Phase 5c（有需要再做）：手寫 ADR，但用自動 watcher 同步進 DB
```

要我從 Phase 5a 開始實作嗎？