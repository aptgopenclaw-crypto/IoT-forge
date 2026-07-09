# VMS (Video Management System) 整合架構設計

> 基於 `1.md` 的討論，經架構 Review 後產生的 refined 設計版本。
> 主要調整：WebFlux → Virtual Threads、SSE → 既有 STOMP、模組結構對齊專案慣例。
> 生效日期：2026-07-08

---

## 一、核心架構：控制面 / 資料面分離

```
┌────────────────────────────────────────────────────────────┐
│                    控制面 (Control Plane)                    │
│                   Spring Boot 3.4 / Java 21                │
│                                                            │
│  ┌──────────┐  ┌──────────────┐  ┌────────────────────┐   │
│  │ VMS API  │  │ VMS Adapter  │  │ VMS Event Service  │   │
│  │ Controller│  │  Manager      │  │ (Webhook 接收)    │   │
│  └────┬─────┘  └──────┬───────┘  └─────────┬──────────┘   │
│       │               │                     │              │
│       │     ┌─────────▼─────────┐          │              │
│       │     │  VmsAdapter (IF)  │          │              │
│       │     │  ┌─────────────┐  │          │              │
│       │     │  │NxWitness    │  │          │              │
│       │     │  │Milestone    │  │          │              │
│       │     │  │Axxon        │  │          │              │
│       │     │  └─────────────┘  │          │              │
│       │     └───────────────────┘          │              │
│       │               │                    │              │
│       │               ▼                    ▼              │
│       │     ┌──────────────────────────────────┐          │
│       │     │    Media Server REST Client      │          │
│       │     │    (ZLMediaKit / SRS)            │          │
│       │     └────────────┬───────────────────┘          │
│       │                  │                               │
│       │     ┌────────────▼───────────┐                   │
│       │     │    Redis Cache         │                   │
│       │     │    (stream URL, token) │                   │
│       │     └────────────────────────┘                   │
└───────────────────┬──────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────────────────────┐
│                    資料面 (Data Plane)                      │
│               ZLMediaKit / SRS (Docker)                    │
│                                                            │
│   RTSP → WebRTC / HLS / FLV 協議轉換                       │
│   歷史回放串流、錄影檔索引                                   │
└────────────────────────────────────────────────────────────┘
```

### 運作流程範例（即時影像）

```
前端                    Spring Boot                   ZLMediaKit              VMS (Nx/Milestone...)
 │                         │                            │                        │
 │ GET /v1/auth/vms/       │                            │                        │
 │   cameras/{id}/live     │                            │                        │
 │ ──────────────────────► │                            │                        │
 │                         │  Redis 快取 hit?           │                        │
 │                         │──┐                         │                        │
 │                         │  │ (miss)                  │                        │
 │                         │◄─┘                         │                        │
 │                         │                            │                        │
 │                         │ VmsAdapter.getStreamUrl()  │                        │
 │                         │ ──────────────────────────────────────────────────► │
 │                         │                            │                        │
 │                         │◄──── RTSP URL ─────────────────────────────────── │
 │                         │                            │                        │
 │                         │ POST /index/api/addStream  │                        │
 │                         │ ─────────────────────────► │                        │
 │                         │                            │──┐                     │
 │                         │                            │  │ 拉 RTSP → 轉 WebRTC│
 │                         │                            │◄─┘                     │
 │                         │◄── unified play URL ────── │                        │
 │                         │                            │                        │
 │                         │ Redis cache stream URL     │                        │
 │                         │──┐                         │                        │
 │◄──── unified play URL ──── │                         │                        │
 │                         │◄─┘                         │                        │
 │                         │                            │                        │
 │ WebRTC 直接連 ZLMediaKit │                            │                        │
 │ ───────────────────────────────────────────────────► │                        │
```

---

## 二、模組設計

### 2.1 套件結構

```
com.taipei.iot.vms/
├── VmsType.java                          // enum 定義支援的 VMS 類型
├── VmsAdapter.java                       // Port interface（等同 DeviceLookupPort 角色）
├── VmsAdapterManager.java                // Registry（同 TelemetryDecoderRegistry 模式）
│
├── dto/
│   ├── CameraStreamInfo.java             // 串流資訊 DTO
│   ├── CameraLiveResponse.java           // 即時影像回應
│   ├── CameraPlaybackResponse.java       // 歷史回放回應
│   ├── PtzCommand.java                   // PTZ 控制指令
│   ├── VmsEvent.java                     // VMS 推播事件標準格式
│   └── VmsCamera.java                    // 攝影機資訊 DTO
│
├── adapter/
│   ├── NxWitnessAdapter.java             // @Service — Nx Witness 實作
│   ├── MilestoneAdapter.java             // @Service — Milestone 實作
│   └── AxxonAdapter.java                 // @Service — Axxon 實作
│
├── service/
│   ├── VmsStreamService.java             // 協調 Adapter + Media Server 取得串流
│   ├── VmsStreamServiceImpl.java
│   └── VmsEventService.java              // 接收 VMS Webhook → 發布 ApplicationEvent
│
├── controller/
│   ├── VmsController.java                // /v1/auth/vms/cameras/...
│   └── VmsWebhookController.java         // /v1/vms/webhook/{vmsType}
│
├── entity/
│   ├── VmsCamera.java                    // 攝影機映射實體（含 tenantId, vmsType, vmsCameraId）
│   └── VmsServer.java                    // VMS 伺服器連線設定
│
├── repository/
│   ├── VmsCameraRepository.java
│   └── VmsServerRepository.java
│
├── event/
│   └── VmsCameraEvent.java               // 自訂 ApplicationEvent（移動偵測、離線等）
│
├── config/
│   ├── VmsProperties.java                // @ConfigurationProperties
│   └── VmsMvcConfig.java                 // 如有必要，注入 MessageConverter
│
└── enums/
    ├── CameraStatus.java                 // ONLINE / OFFLINE / ERROR
    └── VmsEventType.java                 // MOTION_DETECT / CAMERA_OFFLINE / ...
```

### 2.2 VmsAdapter 介面設計（Port）

```java
public interface VmsAdapter {
    /** 辨識此實作對應的 VMS 類型 */
    VmsType getType();

    /** 取得即時影像串流 URL（原始 RTSP） */
    CameraStreamInfo getLiveStreamUrl(String cameraId);

    /** 取得歷史回放串流 URL */
    CameraStreamInfo getPlaybackUrl(String cameraId, Instant startTime, Instant endTime);

    /** PTZ 控制 */
    void controlPtz(String cameraId, PtzCommand command);

    /** 取得攝影機資訊 */
    VmsCamera getCameraInfo(String cameraId);

    /** 列出所有攝影機（分頁） */
    List<VmsCamera> listCameras(int page, int size);

    /** 驗證 VMS 連線是否正常 */
    boolean healthCheck();
}
```

### 2.3 VmsAdapterManager（Registry — 同 TelemetryDecoderRegistry）

```java
@Service
public class VmsAdapterManager {
    private final Map<VmsType, VmsAdapter> adapterMap;

    public VmsAdapterManager(List<VmsAdapter> adapterList) {
        this.adapterMap = adapterList.stream()
            .collect(Collectors.toMap(VmsAdapter::getType, Function.identity()));
    }

    public VmsAdapter getAdapter(VmsType type) {
        VmsAdapter adapter = adapterMap.get(type);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.VMS_TYPE_UNSUPPORTED,
                "不支援的 VMS 類型: " + type);
        }
        return adapter;
    }

    public Collection<VmsAdapter> allAdapters() {
        return adapterMap.values();
    }
}
```

---

## 三、API 設計

### 3.1 REST API

所有 VMS API 掛在 `/v1/auth/vms/` 下（需 JWT 驗證 + `PERM_VMS_VIEW` / `PERM_VMS_MANAGE` 權限）。

| Method | Path | 權限 | 說明 |
|---|---|---|---|
| GET | `/v1/auth/vms/servers` | `VMS_VIEW` | 列出所有 VMS 伺服器 |
| POST | `/v1/auth/vms/servers` | `VMS_MANAGE` | 新增 VMS 伺服器連線 |
| DELETE | `/v1/auth/vms/servers/{id}` | `VMS_MANAGE` | 刪除 VMS 伺服器 |
| GET | `/v1/auth/vms/cameras` | `VMS_VIEW` | 列出攝影機（可 filter by serverId） |
| GET | `/v1/auth/vms/cameras/{id}/live` | `VMS_VIEW` | 取得即時影像播放 URL |
| GET | `/v1/auth/vms/cameras/{id}/playback` | `VMS_VIEW` | 取得歷史回放 URL（需 startTime/endTime） |
| POST | `/v1/auth/vms/cameras/{id}/ptz` | `VMS_MANAGE` | PTZ 控制（方向、縮放、預設點） |
| POST | `/v1/vms/webhook/{vmsType}` | _無驗證_ | 接收 VMS 主動推播事件（IP whitelist 驗證） |

### 3.2 即時影像 API 範例

```java
@RestController
@RequestMapping("/v1/auth/vms")
@RequiredArgsConstructor
public class VmsController {

    private final VmsStreamService vmsStreamService;

    @GetMapping("/cameras/{id}/live")
    @PreAuthorize("hasAuthority('VMS_VIEW')")
    public BaseResponse<CameraLiveResponse> getLiveStream(@PathVariable Long id) {
        CameraLiveResponse response = vmsStreamService.getLiveStream(id);
        return BaseResponse.success(response);
    }

    @GetMapping("/cameras/{id}/playback")
    @PreAuthorize("hasAuthority('VMS_VIEW')")
    public BaseResponse<CameraPlaybackResponse> getPlayback(
            @PathVariable Long id,
            @RequestParam @DateTimeParam Instant startTime,
            @RequestParam @DateTimeParam Instant endTime) {
        CameraPlaybackResponse response = vmsStreamService.getPlayback(id, startTime, endTime);
        return BaseResponse.success(response);
    }

    @PostMapping("/cameras/{id}/ptz")
    @PreAuthorize("hasAuthority('VMS_MANAGE')")
    public BaseResponse<Void> controlPtz(
            @PathVariable Long id,
            @Valid @RequestBody PtzCommand command) {
        vmsStreamService.controlPtz(id, command);
        return BaseResponse.success();
    }
}
```

---

## 四、串流代理策略（Streaming Proxy）

### 4.1 採用 Virtual Threads，非 WebFlux

本專案使用 `spring-boot-starter-web`（Spring MVC），且已使用 **Java 21**。
強烈建議開啟 Virtual Threads，不需要引入 WebFlux：

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

好處：
- 保留 imperative 程式風格，維護成本低
- Virtual Thread 自動處理 blocking I/O（RestClient 呼叫 VMS API、下載影片檔）
- 吞吐量接近 reactive 方案，但程式碼簡單很多

### 4.2 實際串流代理（若需要）

若需由後端代理影片流（而非前端直接連 ZLMediaKit），使用 `Resource` + Virtual Thread：

```java
@GetMapping("/cameras/{id}/proxy-live")
@PreAuthorize("hasAuthority('VMS_VIEW')")
public ResponseEntity<InputStreamResource> proxyLive(@PathVariable Long id) {
    // 1. 取得 VMS adapter 取得 RTSP URL
    // 2. ZLMediaKit 轉換為 HTTP-FLV 或 HLS
    // 3. 用 RestClient 串流回前端
    InputStreamResource resource = vmsStreamService.getStreamAsResource(id);
    return ResponseEntity.ok()
        .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
        .body(resource);
}
```

Virtual Thread 會自動 suspend/resume，不會佔用 platform thread。

---

## 五、事件推播 — 使用既有 STOMP

### 5.1 不建議 SSE

文件原始建議 SSE，但本專案**已有完整 STOMP over WebSocket**：
- `WebSocketConfig.java` — `/topic`, `/queue`, `/user` destinations
- 前端已有 STOMP client 實作

### 5.2 事件流程

```
VMS (Webhook POST) ──► VmsWebhookController
                              │
                              ▼
                      VmsEventService
                    (驗證、轉換為標準格式)
                              │
                              ▼
              publishEvent(new VmsCameraEvent(...))
                              │
                              ▼
              VmsCameraEventListener (@Async)
                    ┌──────────────────┐
                    │  store to DB     │
                    │  send via STOMP  │
                    │  (notification)  │
                    └──────────────────┘
                              │
                              ▼
                   前端 STOMP client 收到
                   /topic/vms/events/{cameraId}
```

### 5.3 ApplicationEvent 設計

```java
// 放在 vms/event/ 套件
public class VmsCameraEvent extends ApplicationEvent {
    private final Long tenantId;
    private final Long cameraId;
    private final VmsEventType eventType;  // MOTION_DETECT, CAMERA_OFFLINE, ...
    private final String payload;          // 事件附帶資料（JSON）
    private final Instant occurredAt;
}
```

遵從專案現有 Event 模式（同 `TelemetryReceivedEvent`、`RuleTriggeredEvent`、`LoginAuditEvent`），所有跨 module 事件放在各自的 module 內，而非全部塞在 `common/event/`。

---

## 六、資料庫設計

### 6.1 Flyway Migration

```sql
-- V8__create_vms_core.sql

-- VMS 伺服器設定
CREATE TABLE vms_servers (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(50) NOT NULL,
    name            VARCHAR(100) NOT NULL,       -- 顯示名稱
    vms_type        VARCHAR(30) NOT NULL,         -- NX_WITNESS / MILESTONE / AXXON
    base_url        VARCHAR(500) NOT NULL,        -- VMS REST API 入口
    auth_type       VARCHAR(20) NOT NULL DEFAULT 'BASIC',  -- BASIC / TOKEN / CERT
    auth_username   VARCHAR(100),
    auth_password   VARCHAR(255),                 -- 加密儲存
    api_token       VARCHAR(500),                 -- 若使用 token 驗證
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
);

-- 攝影機映射表
CREATE TABLE vms_cameras (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(50) NOT NULL,
    server_id       BIGINT NOT NULL REFERENCES vms_servers(id),
    vms_camera_id   VARCHAR(100) NOT NULL,        -- VMS 端的 camera ID
    display_name    VARCHAR(200),
    device_id       BIGINT,                       -- 選用：關聯到 IoT 裝置
    rtsp_url        VARCHAR(500),                 -- 快取 RTSP URL
    status          VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    metadata        JSONB,                        -- VMS 特定額外資訊
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    UNIQUE (server_id, vms_camera_id)
);

-- 事件紀錄（來自 VMS webhook）
CREATE TABLE vms_camera_events (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(50) NOT NULL,
    camera_id       BIGINT NOT NULL REFERENCES vms_cameras(id),
    event_type      VARCHAR(50) NOT NULL,         -- MOTION_DETECT / CAMERA_OFFLINE / ...
    payload         JSONB,
    occurred_at     TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 權限
INSERT INTO sys_permissions (code, name, description) VALUES
    ('VMS_VIEW', '即時影像檢視', '檢視即時影像、歷史回放'),
    ('VMS_MANAGE', '影像系統管理', '管理 VMS 伺服器、攝影機設定、PTZ 控制');

-- 選單（parent_id 需實際查詢 parent 後填入）
-- 目錄: 影像監控 /dashboard/vms
-- 子選單: 即時影像 /vms/live, 歷史回放 /vms/playback, VMS 管理 /vms/servers
```

### 6.2 多租戶隔離

- `vms_servers`、`vms_cameras`、`vms_camera_events` 皆有 `tenant_id`
- 使用 `@Filter(name = "tenantFilter")` 自動過濾（同其他 entity）
- `VmsEventService` 中處理 webhook 時，從 server 綁定的 tenantId 還原 `TenantContext`

---

## 七、與 ZLMediaKit 整合

### 7.1 部署方式

```yaml
# docker-compose.yml（新增 service）
services:
  zlmediakit:
    image: zlmediakit/zlmediakit:master
    container_name: iot-forge-mediaserver
    restart: unless-stopped
    ports:
      - "1935:1935"    # RTMP
      - "554:554"      # RTSP
      - "8080:80"      # HTTP API + WebRTC
      - "8443:443"     # HTTPS API
      - "10000:10000"  # RTP
    volumes:
      - ./data/media:/www/record
      - ./config/zlmediakit.ini:/config/config.ini
    environment:
      - TZ=Asia/Taipei
```

### 7.2 Spring Boot 控制 ZLMediaKit

```java
@Service
public class ZlMediaKitClient {
    private final RestClient restClient;

    public ZlMediaKitClient(VmsProperties properties) {
        this.restClient = RestClient.builder()
            .baseUrl(properties.getMediaServer().getApiUrl())
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    /** 要求 ZLMediaKit 拉取 RTSP 串流，回傳轉換後的播放 URL */
    public String addStreamProxy(String rtspUrl, VmsType vmsType) {
        // POST /index/api/addStreamProxy
        // { "vhost": "__defaultVhost__", "app": "vms", "stream": "vms_{vmsType}_{id}",
        //   "url": rtspUrl, "rtp_type": 0 }
        Map<String, Object> body = new HashMap<>();
        body.put("vhost", "__defaultVhost__");
        body.put("app", "vms");
        body.put("stream", generateStreamId(vmsType));
        body.put("url", rtspUrl);
        body.put("rtp_type", 0);

        var response = restClient.post()
            .uri("/index/api/addStreamProxy")
            .body(body)
            .retrieve()
            .body(ZlMediaKitResponse.class);

        // 回傳統一播放 URL: http://mediaserver:8080/vms/{streamId}.flv
        return buildPlayUrl(response.getData().getStreamId());
    }

    /** 關閉串流 */
    public void closeStream(String streamId) {
        restClient.post()
            .uri("/index/api/close_stream")
            .body(Map.of(
                "vhost", "__defaultVhost__",
                "app", "vms",
                "stream", streamId
            ))
            .retrieve()
            .toBodilessEntity();
    }
}
```

---

## 八、VMS Adapter 實作要點

### 8.1 Nx Witness Adapter

Nx Witness 提供完整的 REST API，整合難度最低：

```java
@Service
public class NxWitnessAdapter implements VmsAdapter {

    private final RestClient restClient;

    public NxWitnessAdapter() {
        this.restClient = RestClient.builder()
            .baseUrl("{baseUrl}/ec2")
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public VmsType getType() { return VmsType.NX_WITNESS; }

    @Override
    public CameraStreamInfo getLiveStreamUrl(String cameraId) {
        // POST /ec2/cameras/{id}/streams
        // 使用 RestClient 取得 RTSP URL
        return restClient.post()
            .uri("/cameras/{id}/streams", cameraId)
            .body(new StreamRequest("rtsp"))
            .retrieve()
            .body(CameraStreamInfo.class);
    }
}
```

### 8.2 Milestone Adapter

**⚠️ 重要限制**：Milestone 的強項是 C# MIP SDK，Java 端僅建議使用 REST API。

```java
@Service
public class MilestoneAdapter implements VmsAdapter {

    @Override
    public VmsType getType() { return VmsType.MILESTONE; }

    @Override
    public CameraStreamInfo getLiveStreamUrl(String cameraId) {
        // Milestone 提供 REST API：
        // GET /API/rest/v2/devices/{deviceId}/streams/live
        // 注意：某些功能可能需要透過 Milestone Federation API
        return restClient.get()
            .uri("/API/rest/v2/devices/{id}/streams/live", cameraId)
            .retrieve()
            .body(CameraStreamInfo.class);
    }

    // 若 REST API 不足，採用 Clean Architecture 的埠外方案：
    // 寫極輕量 C# .NET 微服務（只跑 MIP SDK），
    // 透過 gRPC 或 RabbitMQ 與 Spring Boot 通訊
}
```

### 8.3 Axxon Next Adapter

Axxon Next 提供 REST API（`/api/rest/v1/`），整合方式類似。

### 8.4 健康檢查（Health Indicator）

```java
@Component
public class VmsHealthIndicator implements HealthIndicator {
    private final VmsAdapterManager adapterManager;

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        boolean allHealthy = true;

        for (VmsAdapter adapter : adapterManager.allAdapters()) {
            boolean healthy = adapter.healthCheck();
            details.put(adapter.getType().name(), healthy ? "UP" : "DOWN");
            if (!healthy) allHealthy = false;
        }

        return allHealthy
            ? Health.up().withDetails(details).build()
            : Health.down().withDetails(details).build();
    }
}
```

---

## 九、安全性設計

### 9.1 串流 URL 保護

直接暴露 ZLMediaKit URL 有安全風險。建議：
1. **短期 Token**：Spring Boot 發放 `stream_token`（Redis, TTL = stream session length）
2. **ZLMediaKit HTTP Hook**：設定 `on_play` hook 指向 Spring Boot，驗證 token 後才允許播放

```yaml
# ZLMediaKit config.ini
[hook]
enable=1
on_play=http://iot-forge-backend:8080/v1/vms/stream-hook/on_play
```

```java
@PostMapping("/v1/vms/stream-hook/on_play")
public ResponseEntity<Map<String, Object>> onPlay(@RequestBody HookRequest request) {
    // 從 request.getParams() 取出 stream_token
    // 驗證 token 有效且 cameraId 屬於該租戶
    boolean valid = streamTokenService.validateToken(request.getParams().get("token"));
    if (!valid) {
        return ResponseEntity.ok(Map.of("code", -1, "msg", "Invalid token"));
    }
    return ResponseEntity.ok(Map.of("code", 0, "msg", "success"));
}
```

### 9.2 Webhook IP Whitelist

VMS webhook 端點不經 JWT 驗證，需以 IP whitelist 保護：

```java
@Component
public class VmsWebhookIpFilter extends OncePerRequestFilter {
    private final List<String> allowedIps;  // 從設定讀取

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain) {
        String remoteAddr = request.getRemoteAddr();
        if (!allowedIps.contains(remoteAddr)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }
        chain.doFilter(request, response);
    }
}
```

---

## 十、錯誤碼

```java
// ErrorCode 新增
VMS_SERVER_NOT_FOUND("88100", 404, "VMS 伺服器不存在"),
VMS_CAMERA_NOT_FOUND("88101", 404, "攝影機不存在"),
VMS_TYPE_UNSUPPORTED("88102", 400, "不支援的 VMS 類型"),
VMS_CONNECTION_FAILED("88103", 502, "VMS 連線失敗"),
VMS_STREAM_NOT_AVAILABLE("88104", 503, "串流無法取得"),
VMS_PLAYBACK_INVALID_RANGE("88105", 400, "歷史回放時間範圍無效"),
VMS_PTZ_FAILED("88106", 500, "PTZ 控制失敗"),
VMS_STREAM_TOKEN_INVALID("88107", 401, "串流 Token 無效或已過期"),
```

---

## 十一、與現有架構的整合對照

| 現有模式 | VMS 對應 | 說明 |
|---|---|---|
| `ImportStrategy` | `VmsAdapter` | Strategy Pattern 管理多個 VMS |
| `TelemetryDecoderRegistry` | `VmsAdapterManager` | List injection + Map 自動註冊 |
| `DeviceLookupPort` / `DeviceLookupAdapter` | `VmsAdapter` (interface) | Port/Adapter 分層 |
| `TelemetryReceivedEvent` | `VmsCameraEvent` | Spring `ApplicationEvent` 跨 module 通訊 |
| `RuleTriggeredEventListener` | `VmsCameraEventListener` | `@Async` listener + STOMP 推播 |
| `@Filter(name = "tenantFilter")` | `VmsCamera`, `VmsServer` | 多租戶隔離 |
| `@PreAuthorize` | `VMS_VIEW`, `VMS_MANAGE` | RBAC 權限控制 |
| `Ingest SecurityConfig` | (無獨立濾鏈) | VMS webhook 用 IP whitelist 即可 |
| `BaseResponse<T>` | 全部 API | 統一回應格式 |
| `@Tag("integration")` | VMS 整合測試 | Testcontainers ZLMediaKit |

---

## 十二、實作步驟建議

```
Phase 1 — Core Module
  Step 1: Flyway V8 migration（vms_servers, vms_cameras, vms_camera_events, perms, menus）
  Step 2: Entity + Repository + enums + VmsType
  Step 3: VmsAdapter interface + VmsAdapterManager + NxWitnessAdapter (first impl)
  Step 4: VmsStreamService + ZlMediaKitClient + Redis cache
  Step 5: VmsController (live + playback + ptz)
  Step 6: Unit + integration tests

Phase 2 — Events
  Step 7: VmsWebhookController + VmsEventService + VmsCameraEvent
  Step 8: VmsCameraEventListener → STOMP push
  Step 9: Stream token protection + ZLMediaKit hook

Phase 3 — Additional VMS
  Step 10: MilestoneAdapter (REST API only)
  Step 11: AxxonAdapter

Phase 4 — Frontend
  Step 12: Types + API layer (src/api/vms/)
  Step 13: Views (LiveView, PlaybackView, ServerManagement)
  Step 14: Routes + i18n
```

---

## 十三、風險與緩解

| 風險 | 影響 | 緩解方式 |
|---|---|---|
| Milestone REST API 功能不足，深度整合需 MIP SDK | 需額外 C# 微服務 | 先用 REST API 涵蓋 80% 功能；不足處以輕量 .NET 微服務 + gRPC 補強 |
| ZLMediaKit 的 WebRTC 需 HTTPS + 特定 port | 部署複雜度 | Docker Compose 管理；開發環境可用 HLS/FLV 替代 |
| 串流頻寬消耗 | 成本 / 效能 | Redis cache 避免重複拉流；串流閒置自動關閉（TTL） |
| 多租戶隔離下 webhook 不帶 tenantId | 資料錯置 | webhook 對應的 VMS server 已綁定 tenantId，從資料庫還原 |
