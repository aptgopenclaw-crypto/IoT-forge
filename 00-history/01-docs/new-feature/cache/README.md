# Cache 設計指南

> 範圍：本目錄收錄 taipei-iot 後端在「**多實例部署**」情境下，**所有本機 cache** 的設計原則、決策樹、實作 pattern 與遷移路線圖。
> 起源：來自 [Tenant 模組 Code Review v2](../../code-review/tenant/tenant-module-code-review-v2.md) T-3 議題（`TenantEnabledCache` 在水平擴展下 fail-open）的延伸討論。

---

## 文件導覽

| 文件 | 用途 |
|---|---|
| **[README.md](README.md)**（本檔） | 總覽 + 決策樹 + 現況盤點 |
| [01-cache-strategy.md](01-cache-strategy.md) | 完整設計策略：四種同步方案對照、業界 idiom |
| [02-implementation-patterns.md](02-implementation-patterns.md) | 實作 pattern：Pub/Sub 廣播、Redis Cache-Aside、Spring Cloud Bus 範例程式碼 |
| [03-migration-roadmap.md](03-migration-roadmap.md) | 從現有 local cache 遷移到分散式 cache 的階段性路線圖 |
| [04-current-inventory.md](04-current-inventory.md) | **本機 cache 現況盤點（即時快照）** ——代碼中實際存在的本機 cache、同步狀態與行動清單 |

---

## 一、為何需要這份指南

### 1.1 問題本質

> 「**任何存在於 process memory 的可寫 cache，在多實例部署（Pod ≥ 2）下都會有資料漂移問題。**」

只要符合以下三條件就必然成立：
1. ✅ Cache 在 JVM heap（不是 Redis / DB）
2. ✅ Cache 可被「寫入」（不只是 read-only 配置）
3. ✅ 部署 ≥ 2 個實例

### 1.2 本專案現況

目前 backend 採「單實例假設」設計，至少有以下 cache 點：

| Cache | 位置 | 寫入入口 | 多實例風險 |
|---|---|---|---|
| `TenantEnabledCache` | [tenant/TenantEnabledCache.java](../../../backend/src/main/java/com/taipei/iot/tenant/TenantEnabledCache.java) | `markDisabled` / `markEnabled` | 🚨 **高** — 停用 tenant 失效，安全議題 |
| JWT revocation list | （Redis 已實作）| `SessionRevocationService` | ✅ 已用 Redis |
| Spring `@Cacheable` 預設 | 各 `@Cacheable` 標註點 | 自動 | ⚠️ 視資料而定 |
| Rate Limit 計數 | `RateLimitInterceptor` | 自動 | ⚠️ 多實例下限制被 N 倍放大 |

→ 本指南目的：**建立一致的決策框架**，讓新增 cache 時不會誤踩同樣的雷。

---

## 二、決策樹：什麼時候需要同步

```
┌─────────────────────────────────────────────────┐
│ 我要新增一個 cache，需要同步機制嗎？             │
└─────────────────────────────────────────────────┘
                    │
                    ▼
        ┌───────────────────────┐
        │ Q1：資料會變動嗎？     │
        └───────────────────────┘
            ├─ ❌ 不會（immutable）
            │    → 本機 cache 即可，永遠不需同步
            │    （例：parsed JWT public key、enum 對應表）
            │
            └─ ✅ 會
                    │
                    ▼
        ┌──────────────────────────────────┐
        │ Q2：變動後多久要讓所有實例看到？  │
        └──────────────────────────────────┘
            ├─ 「幾分鐘 OK」
            │    → 本機 cache + 短 TTL（30s ~ 5min）
            │    （例：部門列表、菜單、i18n）
            │
            ├─ 「幾秒內」
            │    → 進入 Q3
            │
            └─ 「毫秒級即時」
                    → 不要 cache，每次走 Redis / DB
                    （例：rate limit 計數、Lock）
                    │
                    ▼
        ┌──────────────────────────────────┐
        │ Q3：沒看到變更的後果是什麼？     │
        └──────────────────────────────────┘
            ├─ 「體驗變差」
            │    → 本機 cache + TTL 短一點
            │
            ├─ 「功能異常」
            │    → 必須同步（Pub/Sub 廣播）
            │
            └─ 「安全漏洞」
                    → 必須同步 + Audit Log + 監控告警
                    （例：TenantEnabledCache、權限撤銷）
```

---

## 三、四種主流策略（速查表）

| 方案 | 即時性 | 程式複雜度 | 額外基礎設施 | 適用情境 |
|---|---|---|---|---|
| **A. 不 cache，每次查 DB** | ✅ 強一致 | 最低 | 無 | QPS 低、資料極小 |
| **B. 本機 cache + 短 TTL** | ⚠️ TTL 內 stale | 低 | 無 | 容忍延遲、讀多寫少 |
| **C. 本機 cache + Pub/Sub 廣播** | ✅ 秒級 | 中 | Redis | 大部分情境（推薦預設） |
| **D. Redis 集中式 cache** | ✅ 強一致 | 低 | Redis | 多實例強一致需求、共享計數 |

詳細對照見 [01-cache-strategy.md](01-cache-strategy.md)。

---

## 四、本專案現有 cache 的建議策略

> 即時快照請見 [04-current-inventory.md](04-current-inventory.md)。以下為 2026-05-27 重新盤點後的總結：

| Cache | 當前實作 | 建議策略 | 狀態 | 優先級 |
|---|---|---|---|---|
| `TenantEnabledCache` | 本機 `Set<String>` + Redis Pub/Sub + `@Scheduled` 校正 | **C**（Pattern 2） | ✅ 已完成（T-3 / T-7 / T-12） | — |
| `PasswordPolicyResolver` | **無 cache**（直接走 DB） | **A**（不 cache，每次查 DB） | ✅ 已完成（2026-05-27 移除 cache）| — |
| `RateLimitInterceptor` 主路徑 | Redis `INCR` + TTL | **D**（已為 Redis 集中式） | ✅ 已完成 | — |
| `RateLimitInterceptor.localFallback` | Redis 不可用時的本機降級 | 保留現狀；複 metrics + 部署文件 | ⚠️ 設計上接受 | 🟢 P3 |
| JWT revocation | Redis | ✅ 維持 | — | — |

---

## 五、設計原則（Cheat Sheet）

開發新模組、新功能、或 PR review 時請對照：

### 5.1 命名

- 本機 cache 類別名稱結尾用 `LocalCache` 或在 JavaDoc 明確標註 `[Local cache — single instance only]`
- 分散式 cache 用 `DistributedCache` 或 `SharedCache`

### 5.2 啟動 log

任何「**單實例假設**」的 cache，必須在啟動時 log 一行 `WARN`：

```java
@PostConstruct
void warnSingleInstanceAssumption() {
    log.warn("[TenantEnabledCache] Local in-memory cache — DO NOT deploy with replicas > 1 " +
             "without migrating to Pub/Sub or Redis. See 01-docs/new-feature/cache/README.md");
}
```

### 5.3 部署文件

`deploy/README.md`（如有）必須列出「**禁止水平擴展的服務 / 限制**」清單，並與本指南交叉引用。

### 5.4 PR review checklist

新增 `@Cacheable` / `ConcurrentHashMap` / `Set<>` / `Map<>` 作為 cache 時：

- [ ] 已經對照 [§二 決策樹](#二決策樹什麼時候需要同步)
- [ ] 已選擇 §三 中的策略（A/B/C/D）並在 PR 描述中說明
- [ ] 若選 A/B 以外，已建立對應的 Redis channel / key prefix 規範
- [ ] 已新增啟動 log 與單元/整合測試
- [ ] 已更新本指南的 §四 表格

---

## 六、相關 review 議題追蹤

| Review 編號 | 議題 | 狀態 | 對應文件 |
|---|---|---|---|
| Tenant T-3 | `TenantEnabledCache` 多實例 fail-open | ✅ 已完成（Pattern 2） | [04-current-inventory.md](04-current-inventory.md) §3.1 |
| Tenant T-7 | `TenantEnabledCache` lazy init | ✅ 已完成（`@PostConstruct` warm-up） | [04-current-inventory.md](04-current-inventory.md) §3.1 |
| Tenant T-12 | `TenantEnabledCache` 與 DB 直改不同步 | ✅ 已完成（`@Scheduled` 校正） | [04-current-inventory.md](04-current-inventory.md) §3.1 |
| （已修復）| `PasswordPolicyResolver` 多實例不一致 | ✅ 已完成（2026-05-27 移除 cache）| [04-current-inventory.md](04-current-inventory.md) §3.2 |
| （已評估）| Rate Limit 主路徑 | ✅ 已為 Redis | — |
| （已評估）| Rate Limit `localFallback` 多實例放大 | ⚠️ 設計上接受 | [04-current-inventory.md](04-current-inventory.md) §3.3 |

---

## 七、延伸閱讀

- 《Building Microservices》— Sam Newman，Chapter 11: Microservices at Scale
- 《Designing Data-Intensive Applications》— Martin Kleppmann，Part II: Distributed Data
- [The Twelve-Factor App — Processes](https://12factor.net/processes)
- [Spring Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Redis Pub/Sub](https://redis.io/docs/latest/develop/interact/pubsub/)
- [Spring Cloud Bus](https://spring.io/projects/spring-cloud-bus)
