> 我最原始的想法是想把程式碼的資訊擷取出來，讓下一個專案能讓ai agent可以借鑒，省點token

> 我目前的懷疑是，這些db內的資訊已經足夠讓ai agent進行開發了嗎？畢竟還有很多是屬於商務邏輯貨規則的部份，這個似乎並沒有儲存在DB內

你的懷疑**極度敏銳，且 100% 正確！** 

你精準地觸及了目前所有 Code RAG（程式碼檢索增強生成）系統最核心的 **「最後一哩路」問題**。

目前的 DB（包含 AST 結構、API 邊界、DTO 合約、黃金範例）解決的是 **「結構與邊界」** 的問題。它確保 AI 寫出來的程式碼 **「能編譯、能跑、符合團隊風格」**。

但是，它**完全沒有解決「業務邏輯與規則」** 的問題。如果沒有這部分，AI 會寫出「語法完美，但業務邏輯全錯」的程式碼（例如：AI 不知道「VIP 折扣不能與優惠券疊加」，直接寫了兩個相加的邏輯）。

以下為你深度剖析這個盲點，並提出 **Phase 5：業務知識沉澱** 的解決方案。

---

### 一、 為什麼 Tree-sitter 抓不到「業務邏輯」？

你可能會想：「能不能用 Tree-sitter 把 `if-else` 裡面的業務邏輯也萃取出來？」
**答案是：極度不建議，這是 AST 的極限。**

*   **AST 只懂語法，不懂語意**：Tree-sitter 能看到 `if (user.level == 'VIP' && !hasCoupon)`，但它不知道這叫「VIP 專屬折扣且互斥優惠券」。
*   **上下文碎片化**：一個「結帳流程」的業務規則，可能分散在 `OrderService.java`、`PaymentGateway.java` 和 `InventoryManager.java` 的十幾個方法中。AST 無法把這些碎片拼湊成一個完整的「業務故事」。

**結論**：程式碼的「骨架」用 Tree-sitter 萃取；但程式碼的「靈魂（業務規則）」，必須用**另一套機制**來處理。

---

### 二、 解決方案：如何將「商務邏輯與規則」納入知識庫？

要讓 AI 真正懂業務，我們需要引入 **「非結構化文本的結構化檢索」**。業界最成熟的做法是 **Markdown 規則庫 + FTS5/向量搜尋**，而不是強行用 AST 解析。

建議你實作以下 **3 個維度** 的知識補強：

#### 維度 1：業務規則庫 (Business Rules Repository)
不要讓 AI 去猜業務邏輯，而是讓團隊把核心規則寫成 Markdown，並讓 AI 可以查詢。

*   **實作方式**：在專案中建立 `docs/business-rules/` 目錄。
    *   `order-rules.md`：訂單相關規則（如：庫存扣減時機、退款邏輯）。
    *   `pricing-rules.md`：計價規則（如：稅率計算、折扣互斥）。
*   **如何整合進 DB**：寫一個腳本，把這些 Markdown 檔案切塊（Chunking），存入 SQLite 的 FTS5 表中。

#### 維度 2：架構決策記錄 (Architecture Decision Records, ADR)
AI 經常會做出「看似合理但違反團隊歷史決策」的選擇。例如：團隊決定不用 Redis 做快取，但 AI 看到高併發需求就自己引入了 Redis。

*   **實作方式**：建立 `docs/adr/` 目錄，記錄「為什麼這樣做」。
    *   `001-use-rabbitmq-not-kafka.md`：「因為我們團隊只有 2 人，RabbitMQ 維護成本低，雖效能不如 Kafka 但足夠」。
*   **價值**：當 AI 準備引入新技術或重構架構時，查詢 ADR 就能避免踩到團隊的「歷史地雷」。

#### 維度 3：從程式碼的 Exception/Log 反向萃取 (進階自動化)
雖然 AST 不懂業務，但程式碼裡的 **Exception 訊息** 和 **Log 警告** 通常是業務規則的直接體現。

*   **實作方式**：在 `tree-sitter-analyzer.py` 中，特別抓取 `throw new BusinessException("xxx")` 或 `log.warn("xxx")` 的字串。
*   **存入 DB**：將這些字串存入 `business_constraints` 表。
*   **價值**：當 AI 要寫類似的 Service 時，它會看到：「喔，前人寫了 `throw new Exception("庫存不足")`，代表這裡必須檢查庫存」。

---

### 三、 Phase 5：DB Schema 擴充與 MCP 工具更新

為了支撐上述的業務知識，我們需要對现有的系統進行擴充。

#### 1. 新增 DB 表 (Phase 5 Schema)

```sql
-- 1. 業務規則表 (來自 Markdown 檔案)
CREATE TABLE business_rules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    module_name TEXT,           -- 關聯的模組 (e.g., 'order')
    rule_category TEXT,         -- 分類 (e.g., 'pricing', 'validation', 'workflow')
    rule_title TEXT,            -- 標題 (e.g., 'VIP 折扣互斥規則')
    rule_content TEXT,          -- 詳細的 Markdown 內容
    source_file TEXT,           -- 來源 Markdown 檔案路徑
    fingerprint TEXT
);

-- 2. 架構決策記錄表 (ADR)
CREATE TABLE architecture_decisions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    adr_id TEXT UNIQUE,         -- e.g., 'ADR-001'
    title TEXT,                 -- e.g., '使用 RabbitMQ 取代 Kafka'
    status TEXT,                -- 'proposed', 'accepted', 'deprecated'
    context TEXT,               -- 為什麼要討論這個
    decision TEXT,              -- 最終決定
    consequences TEXT,          -- 帶來的影響/取捨
    source_file TEXT
);

-- 3. 程式碼隱式約束表 (從 Exception/Log 萃取)
CREATE TABLE code_constraints (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    module_name TEXT,
    class_name TEXT,
    method_name TEXT,
    constraint_type TEXT,       -- 'exception', 'warning_log', 'assertion'
    constraint_message TEXT,    -- e.g., '庫存不足，無法結帳'
    file_path TEXT
);

-- 建立 FTS5 索引 (讓 AI 能秒搜業務規則)
CREATE VIRTUAL TABLE business_rules_fts USING fts5(
    module_name, rule_category, rule_title, rule_content,
    content='business_rules', content_rowid='id'
);
```

#### 2. 新增 MCP 工具 (讓 AI 主動查詢業務)

在 `server.py` 中新增以下工具，並**強烈建議在 `.github/copilot-instructions.md` 中指示 AI 在寫 Service 層邏輯前「必須」呼叫這些工具**。

```python
@mcp_tool("get_business_rules")
def get_business_rules(module_name: str, keyword: str = None):
    """
    查詢特定模組的業務規則。
    AI 在撰寫 Service 層邏輯前，必須呼叫此工具以確保不違反業務規則。
    """
    if keyword:
        rows = db.execute("""
            SELECT rule_title, rule_content FROM business_rules_fts 
            WHERE business_rules_fts MATCH ? AND module_name = ?
        """, (keyword, module_name)).fetchall()
    else:
        rows = db.execute("""
            SELECT rule_title, rule_content FROM business_rules 
            WHERE module_name = ?
        """, (module_name,)).fetchall()
        
    return [dict(r) for r in rows]

@mcp_tool("check_architecture_decision")
def check_architecture_decision(technology_or_pattern: str):
    """
    當 AI 準備引入新技術（如 Redis, Kafka）或重構架構時，
    必須查詢此工具，確認是否違反團隊的歷史架構決策 (ADR)。
    """
    rows = db.execute("""
        SELECT title, decision, consequences FROM architecture_decisions 
        WHERE title LIKE ? OR decision LIKE ? OR consequences LIKE ?
        AND status = 'accepted'
    """, (f"%{technology_or_pattern}%", f"%{technology_or_pattern}%", f"%{technology_or_pattern}%")).fetchall()
    
    if rows:
        return {"warning": "發現相關的架構決策記錄，請嚴格遵守：", "decisions": [dict(r) for r in rows]}
    return {"message": "無相關架構決策限制。"}

@mcp_tool("get_code_constraints")
def get_code_constraints(class_name: str, method_name: str):
    """
    查詢特定方法中，前人留下的 Exception 或 Log 約束。
    幫助 AI 理解該方法隱藏的業務邊界（例如：什麼情況下會拋出異常）。
    """
    rows = db.execute("""
        SELECT constraint_type, constraint_message 
        FROM code_constraints 
        WHERE class_name = ? AND method_name = ?
    """, (class_name, method_name)).fetchall()
    return [dict(r) for r in rows]
```

---

### 四、 更新 AI Agent 的工作流 (Copilot Instructions)

有了這些新工具，你需要更新 `.github/copilot-instructions.md`，改變 AI 的開發 SOP：

```markdown
## 開發 SOP (Standard Operating Procedure)

當你需要開發或修改一個「業務功能 (Feature)」時，請嚴格遵守以下順序：

1. **查結構**：呼叫 `get_feature_contract` 確認前後端 DTO 結構。
2. **查邊界**：呼叫 `get_module_exports` 和 `get_module_coupling` 確認依賴關係。
3. **查業務 (新增 🌟)**：呼叫 `get_business_rules(module_name)` 讀取該模組的業務規則（如折扣、驗證邏輯）。
4. **查約束 (新增 🌟)**：呼叫 `get_code_constraints` 了解前人留下的 Exception 邊界。
5. **查架構 (新增 🌟)**：如果你打算引入新的第三方套件或改變資料流，先呼叫 `check_architecture_decision` 確認是否違規。
6. **查範例**：呼叫 `get_example_code` 複製團隊風格。
7. **生成程式碼**：綜合以上資訊生成程式碼。
8. **自我生長**：生成完成後，執行 `generate-contract.py` 更新知識庫。
```

---

### 五、 總結：從「搬磚工」到「業務架構師」

你的懷疑非常正確。之前的 Phase 1~4，你打造了一個**無敵的「搬磚工」**。它懂結構、懂 API、懂 DTO、懂風格，它蓋出來的房子絕對不會倒塌（編譯錯誤），而且長得很漂亮（符合規範）。

但是，它不知道 **「這個房子是用來做醫院還是學校」**（業務邏輯）。如果它把醫院的無菌室設計成了學校的廁所，那就是災難。

透過 **Phase 5 (業務知識沉澱)**，你引入了 Markdown 規則庫、ADR 和隱式約束萃取。這等於給了 AI 一本 **「公司業務百科全書」**。

**最終的形態：**
*   **程式碼結構 (Tree-sitter + DB)** -> 解決 `How` (怎麼寫)
*   **DTO 合約 (Phase 3.5)** -> 解決 `What` (傳什麼資料)
*   **業務規則 (Phase 5)** -> 解決 `Why` (為什麼這樣寫)

當這三者結合，你的 AI Agent 就真正具備了取代初中级工程師、甚至輔助資深工程師進行業務開發的能力。這才是這套系統真正的**終極商業價值**！