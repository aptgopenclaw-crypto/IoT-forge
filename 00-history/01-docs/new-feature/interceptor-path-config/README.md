# Interceptor 路徑配置設計（顯式優於隱式）

> 對應 code-review 議題：[T-10](../../code-review/tenant/tenant-module-code-review-v2.md#t-10-低-tenantinterceptor-在路徑被-securityconfig-排除如-v1noauth-時仍會跑)
> 修復日期：2026-05-27

---

## 一句話

`TenantInterceptor`「能不能被執行」應該由 **`WebMvcConfig` 自己決定**，
而不是**靠 `SecurityConfig` 白名單的副作用**才不會出事 ——
兩個本應獨立的配置產生了**隱性耦合**。

---

## 場景重現（修復前）

`WebMvcConfig` 原本只寫：

```java
registry.addInterceptor(tenantInterceptor)
        .addPathPatterns("/v1/**");
```

意思是「所有 `/v1/**` 都會跑 `TenantInterceptor`」，**包括 `/v1/noauth/token`**（登入端點）。

當登入請求 `POST /v1/noauth/token` 進來時：

| 階段 | 狀態 |
|---|---|
| 1. `JwtAuthenticationFilter` | 在 `SecurityConfig` 白名單裡 → **跳過 JWT 驗證** → `TenantContext` 沒被設定 |
| 2. `TenantInterceptor.preHandle()` | **仍然會跑**（路徑符合 `/v1/**`） |
| 3. `multi` 模式分支 | 從 `SecurityContext` 拿 JWT → 沒有 JWT → 進入 `else { /* 什麼都不做 */ }` |
| 4. 進入 Controller | `AuthServiceImpl.login` 內部自己 `setSystemContext()` 處理 |

**結果**：目前**剛好沒事**，但完全靠以下兩個運氣：

1. `SecurityConfig` 把 `/v1/noauth/**` 設成 `permitAll`（所以沒 JWT）
2. `TenantInterceptor` 在「沒 JWT」時剛好走進什麼都不做的 `else` 分支

---

## 為什麼這是個問題（即使沒爆炸）

### 1. 隱性耦合 / 心智負擔
要看懂「為什麼 login 不會炸」，你必須**同時讀**
`SecurityConfig` + `JwtAuthenticationFilter` + `TenantInterceptor` 三個檔案才能拼出全貌。
修任何一邊都可能踩雷。

### 2. 改動 single 模式時很危險
T-5 剛把 single 模式改成「驗證 JWT tenant 一致性、不一致就 403」。
如果未來有人在 single 模式的 `else` 分支加上「沒 JWT 就強制 pin 到 DEFAULT」之類的覆寫邏輯，
**login 請求會被誤套上 DEFAULT 租戶**，污染後續業務語意。

### 3. 違反「配置即文件」原則
`WebMvcConfig` 應該能自我描述「`TenantInterceptor` 管哪些路徑」。
靠副作用配置 ＝ 未來新增公開端點時很容易忘記 ——
例如新增 `/v1/noauth/health` 卻忘了它會被 `TenantInterceptor` 掃描。

### 4. 測試／重構時的脆弱性
換個 Filter 順序、把 `permitAll` 改成 `authenticated`、把白名單路徑改名 ——
任一動作都可能讓 `TenantInterceptor` 突然「醒過來」對 login 請求做事。

---

## 修復方式（已套用）

`WebMvcConfig.addInterceptors` 改成**自己明確宣告排除**：

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    // 速率限制攔截器 — 必須在 TenantInterceptor 之前
    registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/v1/**");

    // [Tenant v2 T-10] 明確列出 TenantInterceptor 的攔截/排除範圍
    //   - addPathPatterns("/v1/**")             業務 API 才需要租戶上下文
    //   - excludePathPatterns("/v1/noauth/**")  公開端點（login / captcha / refresh /
    //     forgot-password / force-change-password / turnstile-config）尚未經 JWT
    //     設定 tenantId，本就不需要也不應依賴 TenantInterceptor 的 single-mode 覆寫。
    // Swagger（/v3/api-docs, /swagger-ui）、actuator、/ws/** 因不在 /v1/** 範圍內，
    // 天然就不會被攔截。
    registry.addInterceptor(tenantInterceptor)
            .addPathPatterns("/v1/**")
            .excludePathPatterns("/v1/noauth/**");
}
```

這樣：
- **意圖明確**：看 `WebMvcConfig` 就知道公開端點不過 Interceptor，不用追到 `SecurityConfig`。
- **解耦**：未來 `SecurityConfig` 怎麼改，`TenantInterceptor` 的攔截範圍不變。
- **fail-safe**：新增公開端點只要放到 `/v1/noauth/` 底下，就自動雙重排除（Security ＋ WebMvc）。

風險等級是「低」，因為**目前實際上沒漏洞**，只是把「靠運氣」改成「靠設計」。

---

## 通用設計原則：Interceptor 路徑配置三條規則

未來新增任何 `HandlerInterceptor` 時請遵守：

1. **獨立於 Security 配置宣告攔截範圍** —
   不要假設「Security 已經擋掉就不會進來」。Interceptor 跑在 Security Filter Chain **之後**，但其執行條件應該由 `WebMvcConfig` 完整描述。

2. **顯式列出排除路徑** —
   即使你「知道」某些路徑不會帶 Context，也要在 `excludePathPatterns()` 寫出來作為自我文件。

3. **公開端點 = 雙重排除** —
   `/v1/noauth/**` 同時：
   - 在 `SecurityConfig` 設 `permitAll`（不驗 JWT）
   - 在 `WebMvcConfig` 設 `excludePathPatterns`（不過 Tenant/業務 Interceptor）

---

## 測試策略

針對 `MappedInterceptor.matches()` 的**純單元測試**（不需 `@SpringBootTest`）：

```java
// 取得註冊內容（getInterceptors 是 protected，需 reflection）
InterceptorRegistry registry = new InterceptorRegistry();
config.addInterceptors(registry);
Method m = InterceptorRegistry.class.getDeclaredMethod("getInterceptors");
m.setAccessible(true);
List<Object> interceptors = (List<Object>) m.invoke(registry);

// 取得目標 Interceptor 對應的 MappedInterceptor
MappedInterceptor mi = interceptors.stream()
        .filter(o -> o instanceof MappedInterceptor m2 && m2.getInterceptor() == tenantInterceptor)
        .map(o -> (MappedInterceptor) o)
        .findFirst().orElseThrow();

// 構造 MockHttpServletRequest 並預先快取 RequestPath
MockHttpServletRequest r = new MockHttpServletRequest("GET", "/v1/noauth/token");
r.setRequestURI("/v1/noauth/token");
ServletRequestPathUtils.parseAndCache(r);   // ⚠ 沒這行 MappedInterceptor.matches 會丟 IAE

assertThat(mi.matches(r)).isFalse();         // 驗證排除生效
```

**關鍵踩雷點**：
- `InterceptorRegistry.getInterceptors()` 是 `protected` → 用 reflection。
- `MappedInterceptor.matches(HttpServletRequest)` 在沒有 `DispatcherServlet` 預先解析 path 的情況下會丟 `IllegalArgumentException: Neither a pre-parsed RequestPath nor a pre-resolved String lookupPath is available.` → 必須先呼叫 `ServletRequestPathUtils.parseAndCache(request)`。

實作範例：[`WebMvcConfigTest`](../../../backend/src/test/java/com/taipei/iot/config/WebMvcConfigTest.java)（4 cases）。

---

## 相關檔案

| 檔案 | 角色 |
|---|---|
| [`WebMvcConfig.java`](../../../backend/src/main/java/com/taipei/iot/config/WebMvcConfig.java) | Interceptor 註冊（已套用顯式 `excludePathPatterns`） |
| [`TenantInterceptor.java`](../../../backend/src/main/java/com/taipei/iot/tenant/TenantInterceptor.java) | 業務 Interceptor 本體 |
| [`SecurityConfig.java`](../../../backend/src/main/java/com/taipei/iot/config/SecurityConfig.java) | `/v1/noauth/**` 設為 `permitAll` |
| [`WebMvcConfigTest.java`](../../../backend/src/test/java/com/taipei/iot/config/WebMvcConfigTest.java) | 鎖死配置行為的單元測試 |
