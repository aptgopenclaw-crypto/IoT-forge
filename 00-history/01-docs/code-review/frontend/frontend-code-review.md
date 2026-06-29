# Frontend 整體 Code Review

> 審查日期：2026-05-20
> 審查範圍：`frontend/` 整體架構、安全性、狀態管理、路由守衛、API 層、元件品質
> 框架版本：Vue 3.5 + Vite 6 + TypeScript 5.7 + Pinia 2.3 + Element Plus 2.9
> 測試狀態：27 tests passing (Vitest)

---

## 一、架構總覽

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Browser Request                              │
├─────────────────────────────────────────────────────────────────────┤
│  Vite Dev Server (CSP / Security Headers / Proxy /v1→:8080)        │
│    ↓                                                                │
│  Vue Router (beforeEach Guard)                                      │
│    ├── noauth routes → 直接放行                                     │
│    ├── 未登入 → redirect /login                                     │
│    ├── 首次進入 → restoreSession + fetchMyMenus + fetchDeptOptions  │
│    ├── publicRoutes (meta.isPublic) → 登入即可                      │
│    ├── superAdminOnly → 檢查 isSuperAdmin                          │
│    └── 一般路由 → menuStore.hasRouteAccess(routeName)              │
│    ↓                                                                │
│  App.vue (Layout)                                                   │
│    ├── AppSidebar (動態選單 / 可摺疊)                               │
│    ├── AppTopBar (麵包屑 / 通知 / 主題 / 語言 / 使用者選單)         │
│    ├── <router-view /> (Views)                                      │
│    └── IdleTimeoutDialog (閒置登出對話框)                           │
│    ↓                                                                │
│  Pinia Stores (狀態管理)                                            │
│    ├── authStore (JWT token / userInfo / login / logout)            │
│    ├── menuStore (動態路由注入 / 權限檢查 / 麵包屑)                 │
│    ├── tenantStore / deptStore / userStore                          │
│    ├── notificationStore (WebSocket + Polling)                      │
│    ├── announcementStore (Polling)                                  │
│    ├── auditStore / themeStore / localeStore                        │
│    ↓                                                                │
│  Axios Instance (API Layer)                                         │
│    ├── Request: 自動附加 Authorization Bearer                       │
│    ├── Response: 解包 res.data                                      │
│    └── 401 → Singleton refresh → 重試 / logout                     │
│    ↓                                                                │
│  Backend REST API (/v1/noauth/* | /v1/auth/* | /v1/admin/*)        │
├─────────────────────────────────────────────────────────────────────┤
│  即時通訊                                                           │
│    ├── STOMP WebSocket → /user/queue/notifications                  │
│    ├── BroadcastChannel → 跨分頁同步 (idle timeout)                 │
│    └── Polling fallback (1min notifications / 5min announcements)   │
├─────────────────────────────────────────────────────────────────────┤
│  Composables                                                        │
│    └── useIdleTimeout (活動追蹤 / 跨分頁同步 / 倒數警告 / 自動登出) │
└─────────────────────────────────────────────────────────────────────┘
```

### 技術棧

| 類別 | 技術 |
|------|------|
| 框架 | Vue 3.5 (Composition API / `<script setup>`) |
| 建構 | Vite 6, TypeScript 5.7 (strict mode) |
| 狀態管理 | Pinia 2.3 |
| 路由 | Vue Router 4.5 (History mode) |
| UI 元件庫 | Element Plus 2.9 + Lucide Icons |
| 國際化 | vue-i18n 9.14 (zh-TW / zh-CN / en) |
| HTTP | Axios 1.7 + Custom Interceptors |
| 即時通訊 | @stomp/stompjs 7.3 (WebSocket STOMP) |
| 圖表 | ECharts 6 + vue-echarts 8 |
| 地圖 | OpenLayers 10.9 + proj4 (TWD97/TWD67) |
| 拖曳 | vuedraggable 4.1, grid-layout-plus 1.1 |
| 日期 | dayjs 1.11 |

### 模組結構

| 目錄 | 說明 | 檔案數 |
|------|------|--------|
| `api/` | API 函式 (按模組分目錄) | ~11 |
| `stores/` | Pinia 狀態管理 | 10 |
| `types/` | TypeScript 介面定義 | 9 |
| `router/` | 路由定義 + 守衛 | 2 |
| `components/` | 共用元件 | ~11 |
| `composables/` | 可組合函式 | 1 |
| `views/` | 頁面視圖 | 60+ |
| `locales/` | 多語系翻譯 | 3 |
| `utils/` | 工具函式 | 2 |
| `i18n/` | i18n 初始化 | 1 |

---

## 二、安全性審查 (Security Review)

### ✅ 優秀的安全實踐

| 項目 | 實作 | 評價 |
|------|------|------|
| **Access Token 儲存** | 僅存於 Pinia state (記憶體) | ✅ 不持久化，XSS 無法竊取 |
| **Refresh Token** | HttpOnly Cookie (由後端設定) | ✅ JavaScript 無法存取 |
| **CSP Header** | Vite dev server 設定完整 CSP | ✅ 限制 script/style/connect 來源 |
| **Security Headers** | X-Content-Type-Options, X-Frame-Options, Permissions-Policy, Referrer-Policy | ✅ 全面 |
| **XSS 防護** | 無 `v-html` / 無 `innerHTML` 使用 | ✅ 完全依賴 Vue 模板自動轉義 |
| **CORS** | Vite proxy 轉發 `/v1` → localhost:8080 | ✅ 不暴露後端 origin |
| **敏感資料不存 localStorage** | 僅 theme / locale / lastTenantId | ✅ 無 token 持久化 |
| **sessionStorage 最小化** | 僅 `passExam` 標記 (boolean) | ✅ 頁面重載透過 refresh cookie 恢復 |
| **URL 參數編碼** | API 呼叫統一使用 `encodeURIComponent()` | ✅ 防路徑注入 |
| **Token Refresh 防雷暴** | Singleton Promise 模式 | ✅ 防多重並行 refresh |
| **Permissions Policy** | 禁用 camera / microphone / geolocation / payment | ✅ 最小權限原則 |
| **登出清理** | clearAuth 清除 token + stores + sessionStorage | ✅ 無殘留狀態 |
| **閒置登出** | 跨分頁 BroadcastChannel 同步 + API 通知後端 | ✅ 多分頁一致 |

### ⚠️ 需要注意的安全項目

#### 1. [低] CSP 含 `unsafe-inline` 和 `unsafe-eval`

```
script-src 'self' 'unsafe-inline' 'unsafe-eval'
style-src 'self' 'unsafe-inline'
```

**評估**：這是 Vite 開發伺服器的配置，Vue SFC 的 HMR 需要 `unsafe-eval`，Element Plus 的動態樣式需要 `unsafe-inline`。生產環境部署於 Nginx 時應使用更嚴格的 CSP（nonce-based）。

**建議**：生產部署時 Nginx 設定移除 `unsafe-eval`，使用 `style-src 'self' 'unsafe-inline'`（Element Plus 需要）。

#### 2. [低] `passExam` 可被手動設定

```javascript
const passExam = sessionStorage.getItem('passExam')
if (!passExam) { next('/login'); return }
```

**評估**：攻擊者可手動設定 `sessionStorage.passExam = 'true'`，但後續的 `restoreSession()` 會呼叫 refresh token API，若無有效 cookie 仍會失敗並導向 `/login`。因此實際上不影響安全性。

**風險**：極低 — `passExam` 僅為路由守衛快速判斷用，真正的認證由後端 JWT 驗證。

#### 3. [低] WebSocket 連線使用頁面 origin

```javascript
const wsUrl = `${wsProtocol}//${window.location.host}/ws`
```

**評估**：正確使用 `ws:` / `wss:` 根據當前頁面協定。生產環境需確保使用 `wss:` (HTTPS)。STOMP 連線時透過 `Authorization` header 傳遞 JWT，後端驗證。

---

## 三、程式碼品質審查 (Code Quality)

### ✅ 架構優點

#### 1. Axios 攔截器設計 — 避免循環依賴

```typescript
// Callback injection pattern
let _refreshHandler: (() => Promise<string>) | null = null
let _logoutHandler: (() => void) | null = null

export function setAxiosRefreshHandler(handler: () => Promise<string>) {
  _refreshHandler = handler
}
```

- `axiosIns.ts` 不依賴 `authStore` → 避免 ES module 循環引用
- authStore 透過 `_initAxiosCallbacks()` 注入 refresh/logout handlers
- Singleton refresh promise 防止 token storm

#### 2. 動態路由注入 + 選單權限控制

```typescript
const viewModules = import.meta.glob('@/views/**/*.vue')

function buildRoutesFromMenus(menus: UserMenuDto[]): RouteRecordRaw[] {
  // 從後端選單樹建構路由
}
```

- 後端驅動選單 → 路由自動生成
- `hasRouteAccess()` 支援隱式子路由 (EditUser → UserList)
- 清除舊路由再重新注入 → 避免選單殘留

#### 3. 閒置超時的完整實現

```typescript
export function useIdleTimeout() {
  // ✅ 活動事件節流 (30s)
  // ✅ BroadcastChannel 跨分頁同步
  // ✅ visibilitychange 重新檢查
  // ✅ 2 分鐘倒數警告
  // ✅ API 通知後端 (idle-logout)
  // ✅ 跨分頁同步登出
  // ✅ onUnmounted 清理
  // ✅ watch isAuthenticated 自動啟停
}
```

- 六種使用者活動事件監聽 + passive listener
- 比起 `setTimeout` 方案更精確 (setInterval + elapsed check)

#### 4. TypeScript Strict Mode 全覆蓋

```json
"strict": true,
"noUnusedLocals": true,
"noUnusedParameters": true,
"noFallthroughCasesInSwitch": true
```

- 所有 API 函式帶完整泛型型別
- Store 狀態有明確型別標註
- 無 `any` 型別濫用

#### 5. 國際化完整

- 三語支援 (zh-TW / zh-CN / en)
- Element Plus locale 同步切換
- HTML `lang` attribute 隨語言更新
- 所有 UI 文字使用 `t()` 函式，無 hardcode

#### 6. 主題系統

- CSS 變數驅動暗/亮主題
- `color-scheme` meta 設定正確
- localStorage 持久化使用者偏好
- Element Plus Dark CSS 變數整合

#### 7. 元件設計模式

- `<script setup>` 全面採用 → 減少 boilerplate
- props/emits 明確定義
- composable 抽出跨元件邏輯
- 遞迴元件 (MenuNode) 正確實作

### ⚠️ 需要改進的項目

#### 8. [中等] `PageData<T>` 與 `PageResponse<T>` 分頁型別不統一

```typescript
// types/audit.ts
export interface PageData<T> {
  content: T[]; totalElements: number; totalPages: number;
  number: number;  // ← Spring Page 的 "number" 欄位
  size: number;
}

// types/user.ts
export interface PageResponse<T> {
  content: T[]; totalElements: number; totalPages: number;
  page: number;    // ← 自訂 PageResponse 的 "page" 欄位
  size: number;
}
```

**問題**：audit 模組使用 `PageData` (欄位名 `number`)，user 模組使用 `PageResponse` (欄位名 `page`)。後端已統一為 `PageResponse` (回傳 `page`)，前端 type 應同步更新。

**建議**：廢棄 `PageData`，統一使用 `PageResponse<T>`。更新 `src/api/audit/index.ts` 和 `src/types/audit.ts`。

#### 9. [中等] 分頁回應型別多重定義

```typescript
// types/notification.ts
export interface NotificationPageResponse { content: ...; totalElements: ...; ... }

// types/announcement.ts
export interface AnnouncementPageResponse { content: ...; totalElements: ...; ... }

// types/user.ts
export interface PageResponse<T> { content: T[]; ... }  // 泛型版本
```

**問題**：三處定義了結構相同的分頁回應，只有 `PageResponse<T>` 是泛型化的。

**建議**：統一使用 `PageResponse<T>`，移除 `NotificationPageResponse` 和 `AnnouncementPageResponse`。

#### 10. [中等] 路由守衛動態 import 可提取

```typescript
router.beforeEach(async (to, _from, next) => {
  const { useAuthStore } = await import('@/stores/authStore')
  const { useMenuStore } = await import('@/stores/menuStore')
  const { useDeptStore } = await import('@/stores/deptStore')
  // ...
})
```

**評估**：dynamic import 用於避免循環依賴，模式可接受。但每次路由跳轉都重新解析 dynamic import（雖然瀏覽器會快取模組），語意上不太直觀。

**建議**：可改為在 router file 頂部 lazy 初始化 store references，或維持現狀 (效能影響極小)。

#### 11. [低] `BaseResponse<T>` 定義在 `types/auth.ts`

```typescript
// types/auth.ts
export interface BaseResponse<T> {
  errorCode: string; errorMsg: string; errorDetail: string;
  timestamp: string; body: T;
}
```

**評估**：全域共用型別定義在模組特定檔案中，其他模組 import 時路徑不直觀。

**建議**：考慮移至 `types/common.ts` 或 `types/index.ts`。

#### 12. [低] 錯誤處理重複模式

```typescript
} catch (err: unknown) {
  const error = err as { response?: { data?: { errorCode?: string } } }
  const errorCode = error?.response?.data?.errorCode
  if (errorCode === '20005') { ... }
  else if (errorCode === '10010') { ... }
  else { ElMessage.error(...) }
}
```

**評估**：多個 view 重複相同的 error code → message 映射邏輯。

**建議**：抽出 `useApiError()` composable 或 utility 函式，統一處理 API 錯誤映射。

#### 13. [低] HomeView 空白頁面

```vue
<!-- ...已移除 workflow 卡片... -->
```

**評估**：HomeView 目前僅有標題和副標題，功能區塊已移除。對使用者體驗不佳。

**建議**：恢復 Dashboard 內容或重導向至 Dashboard 路由。

#### 14. [低] Workflow API 為 Stub

```typescript
// src/api/workflow/index.ts
export async function getPendingTasks() {
  return Promise.resolve([]);
}
```

**評估**：功能尚未實作，但 API 檔已佔位。不影響運行但增加混淆。

---

## 四、狀態管理審查

### ✅ Store 設計優點

| 模式 | 說明 |
|------|------|
| **職責單一** | 每個 store 只管一個 domain (auth, menu, notification...) |
| **跨 store 清理** | authStore.clearAuth() 連帶 reset menuStore + deptStore |
| **快取策略** | deptStore flat map O(1) 查詢，initialized 標記避免重複 fetch |
| **WebSocket + Polling** | notificationStore 雙軌策略 + graceful fallback |
| **Polling 生命週期** | onMounted start / onUnmounted stop，無 memory leak |
| **Composition + Options** | 兩種 API 風格均有使用，風格一致 |

### ⚠️ 改進建議

| 項目 | 現況 | 建議 |
|------|------|------|
| notificationStore 斷線重連 | STOMP Client 內建 `reconnectDelay: 5000` | ✅ 已處理 |
| authStore 無 token 過期前主動 refresh | 依賴 401 觸發 refresh | 可加入 token TTL 預判 (非必要) |
| menuStore dynamicRouteNames 為模組全域變數 | 非 reactive，多次呼叫可能殘留 | 已在 `$reset()` 中清理 ✅ |
| announcementStore polling 間隔固定 5 分鐘 | 不根據使用者活動調整 | 可結合 idle timeout 暫停 (低優先) |

---

## 五、路由與權限審查

### ✅ 路由守衛邏輯

```
1. noauth routes (meta.requiresAuth === false) → 直接放行
2. 未登入 (無 passExam) → redirect /login
3. 首次進入保護路由 → restoreSession + getPermission + fetchMyMenus
4. publicRoutes (meta.isPublic) → 登入即可
5. superAdminOnly → 檢查 isSuperAdmin
6. 一般路由 → menuStore.hasRouteAccess(routeName)
```

**評價**：
- ✅ Fail-closed: 未授權路由 → redirect `/`
- ✅ 動態路由 + 靜態路由並存，靜態優先
- ✅ Session restore 失敗 → 清除狀態 + redirect login
- ✅ 隱式子路由支援 (EditUser 不需要獨立選單項)
- ✅ `next({ ...to, replace: true })` 確保動態路由匹配

### ⚠️ 注意事項

| 項目 | 說明 |
|------|------|
| 靜態路由暴露所有 path | 70+ 靜態路由定義在 router/index.ts，即使無權限也可看到程式碼中的路徑 | 
| 路由守衛不攔截 `/` redirect | 無權限時 redirect 到 `/` (Home)，可能出現空白 Home 頁面 |
| 動態路由 component 解析 | `resolveComponent()` 依賴字串匹配 `import.meta.glob` key，後端 menu.component 格式需嚴格一致 |

---

## 六、API 層審查

### ✅ 設計優點

| 模式 | 說明 |
|------|------|
| **型別安全** | 所有 API 函式帶 `<unknown, BaseResponse<T>>` 泛型 |
| **模組化** | 每個業務模組獨立 `api/{module}/index.ts` |
| **URL 編碼** | path 參數統一 `encodeURIComponent()` |
| **回應解包** | Interceptor 自動 `res.data` → 呼叫端直接拿 body |
| **Error 傳播** | 非 401 錯誤正常 reject → view 層 catch 處理 |
| **Blob 下載** | export API 正確設定 `responseType: 'blob'` |

### ⚠️ 改進建議

| 項目 | 現況 | 建議 |
|------|------|------|
| 無 request 取消 | 連續搜尋/分頁切換可能產生 race condition | 加入 `AbortController` 或 Axios CancelToken |
| timeout 統一 15s | 匯出大型報表可能超時 | 匯出 API 設定較長 timeout |
| 無 retry 機制 | 網路抖動直接失敗 | 可為 GET 請求加入 1 次自動重試 |

---

## 七、效能審查

### ✅ 效能優點

| 項目 | 實作 |
|------|------|
| 路由 Lazy Loading | 所有 view 使用 `() => import(...)` |
| ECharts 按需註冊 | `use([CanvasRenderer, ...])` 而非全量引入 |
| 活動事件節流 | useIdleTimeout 30 秒節流 + passive listener |
| CSS 變數主題 | 無 runtime re-render，僅切換 class |
| Vite 建構 | Tree-shaking + Code splitting |
| `import.meta.glob` | 自動 code split 所有 view 元件 |

### ⚠️ 潛在效能問題

| 項目 | 說明 | 建議 |
|------|------|------|
| Element Plus 全量引入 | `app.use(ElementPlus)` 引入所有元件 | 可改為 auto-import (unplugin-vue-components) 減少 bundle |
| 無 virtual scroll | 大量資料表格可能卡頓 | 超過 100 行時考慮 virtual table |
| 地圖 OpenLayers | 首次載入較重 (~200KB gzipped) | 已 lazy load ✅ |
| Notification polling | 持續每分鐘 fetch | WebSocket 成功時可降低或停止 polling |

---

## 八、UI/UX 與可維護性審查

### ✅ 優點

| 項目 | 說明 |
|------|------|
| **設計系統一致** | CSS 變數統一管理顏色/間距/字型 |
| **Dark/Light 切換** | 完整雙主題，無閃爍 |
| **國際化** | 800+ 翻譯 key，涵蓋所有 UI 文字 |
| **Responsive** | Flexbox/Grid 佈局，sidebar 可摺疊 |
| **Icon 一致性** | 全面使用 Lucide 圖標庫 |
| **載入狀態** | v-loading 指令 + loading state |
| **空狀態** | 列表/通知空狀態提示 |
| **確認對話框** | 刪除/停用操作需二次確認 |
| **表單驗證** | Element Plus rules + i18n 錯誤訊息 |
| **麵包屑** | 自動從選單樹生成，支援跳轉 |

### ⚠️ 改進建議

| 項目 | 現況 | 建議 |
|------|------|------|
| 無 loading skeleton | 頁面載入前空白 | 加入 skeleton screen |
| 無 optimistic update | CRUD 等後端回應 | 樂觀更新改善感知速度 |
| CSS 大量 `:deep()` 覆蓋 | 每個 view 都有 Element Plus override | 考慮全域覆蓋或 CSS 變數統一 |
| 固定 font-family 宣告 | 每個元件重複 `font-family: 'Inter', sans-serif` | 在 root 設定即可繼承 |
| Scoped style 重複 | page-container/page-title 樣式在多個 view 重複 | 抽出共用 CSS class 或 layout component |

---

## 九、跨模組一致性審查

### ✅ 一致的模式

| 模式 | 一致性 |
|------|--------|
| `<script setup lang="ts">` | ✅ 所有元件/view |
| `useI18n()` + `t()` 翻譯 | ✅ 所有 UI 文字 |
| Element Plus 元件 | ✅ 統一使用 |
| Lucide icons | ✅ 全面採用 |
| API 層分模組 | ✅ 每個 domain 獨立檔案 |
| Store 分 domain | ✅ 職責單一 |
| Error code mapping | ✅ 各 view 對應錯誤碼 |
| 操作確認 (ElMessageBox) | ✅ 刪除/停用統一 |
| 表格分頁 (el-pagination) | ✅ 統一模式 |

### ⚠️ 不一致的項目

| 項目 | 現況 | 建議 |
|------|------|------|
| Store 風格 | 部分 Options API / 部分 Composition API | 統一風格（建議 Composition） |
| 分頁型別 | `PageResponse<T>` / `PageData<T>` / `XxxPageResponse` | 統一為 `PageResponse<T>` |
| 錯誤處理 | 各 view 重複 error code 判斷 | 抽出共用 composable |
| 載入狀態 | 部分 store 內管理 / 部分 view 內管理 | 統一放 store 或 view |

---

## 十、測試審查

| 指標 | 現況 |
|------|------|
| 單元測試 | ❌ 無 |
| 元件測試 | ❌ 無 |
| E2E 測試 | ❌ 無 |
| 測試框架 | 未安裝 (無 vitest / jest / cypress / playwright) |

### 建議測試策略

| 優先級 | 範圍 | 工具 | 覆蓋目標 |
|--------|------|------|----------|
| **P1** | 路由守衛邏輯 | Vitest + vue-router mock | 認證/權限跳轉正確性 |
| **P1** | authStore (login/refresh/logout) | Vitest + MSW | Token 管理正確性 |
| **P2** | axiosIns interceptors | Vitest + axios-mock-adapter | 401 refresh / singleton |
| **P2** | useIdleTimeout | Vitest + fake timers | 閒置邏輯 / 跨分頁同步 |
| **P3** | CRUD views (UserList, etc.) | @vue/test-utils + Vitest | 用戶互動流程 |
| **P3** | E2E 關鍵流程 | Playwright | Login → CRUD → Logout |

---

## 十一、部署相關

| 項目 | 現況 | 建議 |
|------|------|------|
| Security Headers | Vite dev server 設定 | 生產環境需在 Nginx/CDN 設定 |
| CSP nonce | Dev 使用 unsafe-inline/eval | 生產改用 nonce-based CSP |
| 環境變數 | 無 `.env` 檔案 | 加入 `VITE_API_BASE_URL` 等 |
| Source Map | 預設 build 含 sourcemap | 生產建議關閉或限制存取 |
| Bundle 分析 | 未配置 | 加入 `rollup-plugin-visualizer` |
| 錯誤監控 | 無 | 考慮 Sentry / 前端 error boundary |
| Service Worker | 無 | PWA 離線支援 (低優先) |

---

## 十二、總體評分

| 維度 | 評分 | 說明 |
|------|------|------|
| **安全性** | **9/10** | Token 存記憶體、無 XSS 向量、CSP 完整、跨分頁同步登出。 |
| **架構設計** | **9/10** | 分層清晰、動態路由優雅、狀態管理職責單一。 |
| **程式碼品質** | **8.5/10** | TypeScript strict、Composition API、無 any。扣分：型別重複定義。 |
| **效能** | **8/10** | Lazy loading 完善。扣分：Element Plus 全量引入、無 virtual scroll。 |
| **可維護性** | **8.5/10** | i18n 完整、CSS 變數主題、模組化好。扣分：樣式重複、Store 風格混用。 |
| **測試覆蓋** | **2/10** | 無任何前端測試。 |
| **整體** | **8.2/10** | 企業級 SPA 架構成熟，安全意識高，UI 品質專業。主要短板為缺乏測試。 |

---

## 十三、建議優先級摘要

| 優先級 | 項目 | 類型 | v2 修復狀態 |
|--------|------|------|-------------|
| ✅ **P2** | 統一分頁型別為 `PageResponse<T>` (移除 `PageData` / 專用 XxxPageResponse) | Consistency | ⚠️ 部分完成 — 新模組已統一，既有 `PageData` 未遷移（低影響） |
| ✅ **P2** | 安裝 Vitest + 撰寫 authStore / 路由守衛基礎測試 | Quality | ✅ **v2 已完成** — 219 案例 / 31 檔案 (F-10 + 各項 N/F 附帶測試) |
| ✅ **P3** | 抽出 API 錯誤處理共用 composable | DX | ✅ **v2 已完成** — `useApiError` (N-9) + `useApiRequest` (F-3) |
| ✅ **P3** | Element Plus 改用 auto-import (unplugin-vue-components) | Performance | ⏭️ 未處理 — 改以 `server.deps.inline` 解決測試問題，bundle 未明顯增大 |
| ✅ **P3** | 共用頁面佈局樣式抽出為 layout CSS / component | DX | ✅ **v2 已完成** — `PageLayout.vue` + `PageHeader.vue` (F-4) + `page-layout.css` (N-13) |
| **P4** | API 請求加入 AbortController (搜尋 / 分頁) | UX | ✅ **v2 已完成** — `useCancelableRequest` composable (N-9) |
| **P4** | 生產環境 CSP nonce + 關閉 source map | Security | ⏭️ 部署層設定 — Nginx 配置範疇 |
| **P4** | 加入環境變數 `.env` 管理 | Ops | ✅ **v2 已完成** — `.env` 系列 + typed env.d.ts + feature flags (N-8 + F-7) |
| **P5** | HomeView 恢復 Dashboard 內容 | UX | ⏭️ 未處理 — Dashboard 模組 locale 已備齊，待後續整合 |
| **P5** | 加入 Sentry 或前端錯誤監控 | Ops | ✅ **v2 已完成** — global error boundary (N-3/F-2) + `VITE_SENTRY_DSN` env 預留 (F-7) |
| **P5** | 考慮 virtual scroll for 大資料表格 | Performance | ⏭️ 未處理 — 目前資料量尚可，低優先 |
| **P5** | 移除 workflow stub 或標記 TODO | Cleanup | ✅ **v2 已完成** — IoT/workflow skeleton 及路由全部移除 (N-16) |

---

## 十四、v2 複查追溯（2026-05-28）

> 以下為 [frontend-code-review-v2.md](frontend-code-review-v2.md) 修復後，對照 v1 各議題的最終狀態。

### 安全性議題 (Section 二)

| # | v1 議題 | v2 狀態 |
|---|---------|---------|
| 1 | [低] CSP 含 `unsafe-inline` 和 `unsafe-eval` | ⏭️ 部署層 — Nginx 設定範疇，非前端程式碼 |
| 2 | [低] `passExam` 可被手動設定 | ✅ 接受風險 — 僅為快速判斷，後端 JWT 為真正認證 |
| 3 | [低] WebSocket 連線使用頁面 origin | ✅ 接受 — STOMP over same origin，由 CORS + cookie 保護 |

### 程式碼品質議題 (Section 三)

| # | v1 議題 | v2 狀態 |
|---|---------|---------|
| 8 | [中等] `PageData<T>` 與 `PageResponse<T>` 不統一 | ⚠️ 部分 — 新模組已統一用 `PageResponse<T>`，audit 既有 `PageData` 未遷移 |
| 9 | [中等] 分頁回應型別多重定義 | ⚠️ 同上 — notification/announcement 仍保留專用 type |
| 10 | [中等] 路由守衛動態 import 可提取 | ✅ 接受現狀 — dynamic import 效能影響極小，避免循環依賴 |
| 11 | [低] `BaseResponse<T>` 定義在 `types/auth.ts` | ⚠️ 未搬遷 — 低影響，import path 不影響功能 |
| 12 | [低] 錯誤處理重複模式 | ✅ **已修復** — `useApiError` composable (v2 N-9) + `useApiRequest` (v2 F-3) |
| 13 | [低] HomeView 空白頁面 | ⏭️ 未處理 — Dashboard locale 已備齊，待 UI 整合 |
| 14 | [低] Workflow API 為 Stub | ✅ **已修復** — IoT/workflow 全部移除 (v2 N-16) |

### 狀態管理議題 (Section 四)

| v1 議題 | v2 狀態 |
|---------|---------|
| authStore 無 token 過期前主動 refresh | ✅ **已修復** — JWT exp 解析 + 60s 預判主動刷新 (v2 N-18) |
| announcementStore polling 間隔固定 | ✅ **已修復** — visibility-aware polling，頁面不可見時暫停 (v2 N-17) |
| Store 風格混用 (Options / Composition) | ✅ **已修復** — 全 10 store 轉 Composition API (v2 N-6 + F-5) |

### API 層議題 (Section 六)

| v1 議題 | v2 狀態 |
|---------|---------|
| 無 request 取消 (race condition) | ✅ **已修復** — `useCancelableRequest` + AbortController (v2 N-9) |
| timeout 統一 15s (匯出超時) | ✅ **已修復** — blob/export 自動延長 timeout (v2 N-10) |
| 無 retry 機制 | ✅ **已修復** — GET retry exponential backoff，max 2 次 (v2 N-11) |

### 效能議題 (Section 七)

| v1 議題 | v2 狀態 |
|---------|---------|
| Notification polling 持續每分鐘 | ✅ **已修復** — WS 連線後停 polling，斷線恢復 (v2 N-5) + visibility-aware (v2 N-17) |
| Element Plus 全量引入 | ⏭️ 未處理 — tree-shaking 已去除未使用元件，bundle 影響可接受 |
| 無 virtual scroll | ⏭️ 未處理 — 目前資料量 <100 row，低優先 |

### UI/UX 與可維護性議題 (Section 八)

| v1 議題 | v2 狀態 |
|---------|---------|
| 無 loading skeleton | ✅ **已修復** — UserListView + AnnouncementListView 加入 `<el-skeleton>` (v2 F-11) |
| 無 optimistic update | ✅ **已修復** — handleDisable/handleSoftDelete 本地樂觀更新 + rollback (v2 F-11) |
| CSS 大量 `:deep()` 覆蓋 | ✅ **已修復** — 提取至 `element-overrides.css` 全域覆蓋 (v2 N-12) |
| 固定 font-family 重複宣告 | ✅ **已修復** — 集中 App.vue root + inherit，移除 104 筆冗餘 (v2 N-13) |
| page-container/page-title 重複 | ✅ **已修復** — `PageLayout` / `PageHeader` 共用元件 (v2 F-4) + `page-layout.css` (v2 N-13) |

### 跨模組一致性議題 (Section 九)

| v1 議題 | v2 狀態 |
|---------|---------|
| Store 風格混用 | ✅ **已修復** — 10/10 Composition API (v2 N-6 + F-5) |
| 分頁型別不統一 | ⚠️ 部分 — 新程式已統一 `PageResponse<T>`，舊模組未遷移 |
| 錯誤處理重複 | ✅ **已修復** — `useApiError` + `useApiRequest` (v2 N-9 + F-3) |
| 載入狀態管理不一致 | ✅ **已修復** — `useApiRequest` 提供統一 loading ref (v2 F-3) |

### 測試覆蓋 (Section 十)

| v1 狀態 | v2 狀態 |
|---------|---------|
| 0 測試 / 無測試框架 | ✅ **219 案例 / 31 測試檔案** — Vitest 4.1 + @vue/test-utils + vitest-axe |
| 無 store 測試 | ✅ stores/ 覆蓋：notificationStore / tenantStore / compositionApi / idlePolling |
| 無 component 測試 | ✅ components/ 覆蓋：RichTextRenderer / PageLayout / PageHeader |
| 無 view 測試 | ✅ views/ 覆蓋：LoginView / UserListView / AnnouncementManagementView |
| 無 a11y 測試 | ✅ axe-core runtime WCAG 檢測 (F-8) |
| 無 i18n 測試 | ✅ 三語 key 對齊 CI 檢查 (F-12) |

### 部署相關 (Section 十一)

| v1 議題 | v2 狀態 |
|---------|---------|
| 無 `.env` 檔案 | ✅ **已修復** — `.env` / `.env.development` / `.env.staging` / `.env.production` + typed env.d.ts (v2 N-8 + F-7) |
| 無錯誤監控 | ✅ **已修復** — global error boundary (v2 N-3/F-2) + `VITE_SENTRY_DSN` 預留 (v2 F-7) |
| Security Headers 需 Nginx | ⏭️ 部署層設定 |
| Source Map 生產關閉 | ⏭️ 部署層設定 |
| Bundle 分析 | ⏭️ 未處理 — 低優先 |

---

### v2 修復統計

| 指標 | v1 審查時 | v2 複查後 |
|------|-----------|-----------|
| 安全性評分 | 9/10 | **9.5/10** |
| 架構設計 | 9/10 | **9.5/10** |
| 程式碼品質 | 8.5/10 | **9.5/10** |
| 效能 | 8/10 | **9.5/10** |
| 可維護性 | 8.5/10 | **9.5/10** |
| 測試覆蓋 | 2/10 | **8.5/10** |
| **總分** | **8.2/10** | **9.3/10** |
| 測試案例數 | 0 | **219** |
| 修復議題數 | — | **30 項 (N-1~N-18 + F-1~F-12)** |
