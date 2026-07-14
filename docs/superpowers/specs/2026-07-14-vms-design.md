# VMS (Video Management System) 設計文件

- 日期：2026-07-14
- 專案：IoT Forge
- 狀態：核定

---

## 1. 概述

在 IoT Forge 平台上整合 NX Witness VMS，提供即時影像播放與歷史影像播放功能。核心安全原則是 **前端只認識 JWT，後端集中管理 NX Token，NX Token 永不離開後端**。

### 1.1 目標

- 支援即時 HLS 串流播放
- 支援歷史 HLS 串流播放（含快進/倒播）
- 多台 NX Server 管理
- 攝影機與部門權限綁定
- 完整的串流播放稽核紀錄

### 1.2 非目標

- PTZ 控制（擱置）
- VMS 事件記錄（擱置）
- WebRTC 支援

---

## 2. 整體架構

```
┌────────────────────────────────────────────────────────┐
│                       前端 (Vue 3)                       │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │CameraList │  │ StreamPlayer │  │ PlaybackControl  │  │
│  │(選攝影機) │─▶│  (hls.js)   │  │ (進度條+速度)   │  │
│  └──────────┘  └──────┬───────┘  └──────────────────┘  │
│                        │ JWT (Bearer Token)              │
└────────────────────────┼───────────────────────────────┘
                         │
┌────────────────────────┼───────────────────────────────┐
│                   後端 (Spring Boot 3.4.1)                │
│                        │                                  │
│  ┌─────────────────────▼──────────────────────────┐     │
│  │         VMS Controller (/v1/auth/vms/...)       │     │
│  │  • 驗證 JWT + 部門權限檢查                     │     │
│  │  • 建立 sessionToken（綁定 userId + camera）   │     │
│  │  • 代理 master.m3u8 / .ts / trickplay 請求     │     │
│  └─────────────────────┬──────────────────────────┘     │
│                        │                                  │
│  ┌─────────────────────▼──────────────────────────┐     │
│  │               NxTokenManager                     │     │
│  │  • 啟動時向 NX 登入取得 token                   │     │
│  │  • @Scheduled 自動 refresh (expiresInS × 0.9)  │     │
│  │  • 提供 getToken() 給內部使用，永不外傳         │     │
│  └─────────────────────┬──────────────────────────┘     │
└────────────────────────┼───────────────────────────────┘
                         │ 內網
┌────────────────────────┼───────────────────────────────┐
│                  ▼                                       │
│            NX Witness Server                              │
│       (HLS API: live / playback / trickplay)             │
└─────────────────────────────────────────────────────────┘
```

### 2.1 安全原則

- 前端只攜帶 JWT，不知道 NX 的存在
- NX Token 在後端 NxTokenManager 集中管理，永不離開後端
- 每個 HLS session 綁定 userId + camera，可完整稽核
- 所有串流請求（m3u8 / TS / trickplay）都經後端代理
- NX 帳密加密存 DB，不寫入明文設定檔

---

## 3. 路由設計

主功能選單：**VMS**

| 子功能 | 路由 | 類型 |
|--------|------|------|
| 即時播放 | `/vms/live` | 播放 |
| 歷史播放 | `/vms/playback` | 播放 |
| VMS 伺服器 | `/vms/servers` | 管理 |
| 攝影機管理 | `/vms/cameras` | 管理 |
| 串流記錄 | `/vms/stream-logs` | 查詢 |

路由兩層（`vms` + 功能名稱），透過後端 menuStore 動態注入。

---

## 4. 後端設計

### 4.1 Package 結構

```
com.taipei.iot.vms/
├── config/
│   └── NxProperties.java                ← @ConfigurationProperties("nx")
│
├── token/
│   └── NxTokenManager.java              ← NX Token 生命週期管理
│
├── session/
│   ├── HlsSession.java                  ← Value Object
│   ├── HlsSessionManager.java           ← sessionToken CRUD
│   └── SessionNotFoundException.java
│
├── controller/
│   └── VmsStreamController.java         ← /v1/auth/vms/**
│
├── service/
│   ├── VmsStreamService.java            ← 建立 session、權限檢查
│   ├── HlsProxyService.java             ← 代理 m3u8/TS/trickplay
│   ├── VmsServerService.java            ← Server CRUD + sync cameras
│   └── VmsCameraService.java            ← Camera mapping CRUD
│
├── entity/
│   ├── VmsServerEntity.java
│   ├── VmsCameraMappingEntity.java
│   └── VmsStreamLogEntity.java
│
├── repository/
│   ├── VmsServerRepository.java
│   ├── VmsCameraMappingRepository.java
│   └── VmsStreamLogRepository.java
│
├── dto/
│   ├── StreamCreateRequest.java
│   ├── StreamCreateResponse.java
│   ├── VmsServerRequest.java
│   ├── VmsServerResponse.java
│   ├── VmsCameraRequest.java
│   ├── VmsCameraResponse.java
│   └── VmsStreamLogQuery.java
│
└── exception/
    ├── CameraOfflineException.java
    ├── NoRecordingException.java
    ├── PlaybackEndedException.java
    └── NxServerException.java
```

### 4.2 API Endpoints

#### 串流播放

| 方法 | 路徑 | 說明 |
|------|------|------|
| `POST` | `/v1/auth/vms/{cameraId}/stream` | 建立播放 session，回傳 sessionToken<br>`cameraId` = VmsCameraMapping 本地 PK |
| `GET` | `/v1/auth/vms/stream/{sessionToken}/master.m3u8` | 代理 master playlist |
| `GET` | `/v1/auth/vms/stream/{sessionToken}/trickplay` | 快進/倒播 `?speed={倍數}` |
| `DELETE` | `/v1/auth/vms/stream/{sessionToken}` | 結束播放，清除 session |

> `.ts` segment 請求由 hls.js 自動從 m3u8 解析路徑後發起，同樣經後端代理。

#### VMS Server 管理

| 方法 | 路徑 | 說明 |
|------|------|------|
| `GET` | `/v1/auth/vms/servers` | 列表 |
| `POST` | `/v1/auth/vms/servers` | 新增 |
| `PUT` | `/v1/auth/vms/servers/{id}` | 編輯 |
| `DELETE` | `/v1/auth/vms/servers/{id}` | 刪除 |
| `POST` | `/v1/auth/vms/servers/{id}/test-connection` | 測試連線 |

#### Camera Mapping 管理

| 方法 | 路徑 | 說明 |
|------|------|------|
| `GET` | `/v1/auth/vms/cameras` | 列表（含 dept 過濾） |
| `POST` | `/v1/auth/vms/cameras` | 新增 mapping |
| `PUT` | `/v1/auth/vms/cameras/{id}` | 編輯 |
| `DELETE` | `/v1/auth/vms/cameras/{id}` | 刪除 |
| `POST` | `/v1/auth/vms/servers/{id}/sync-cameras` | 從 NX Server 同步 camera 列表 |

#### 串流記錄

| 方法 | 路徑 | 說明 |
|------|------|------|
| `GET` | `/v1/auth/vms/stream-logs` | 分頁查詢（可過濾 user、camera、日期區間） |

### 4.3 串流代理安全設計

**建立 session（POST stream）：**
1. 驗證 JWT，取得 userId
2. 檢查 `cameraId`（VmsCameraMapping 本地 PK）所屬部門是否在該使用者的資料權限範圍內
3. 向 NxTokenManager 取得當前有效的 NX Token
4. 建立 `HlsSession(userId, cameraId, nxToken, type, startTime?, endTime?, createdAt)`
5. sessionToken 為 UUID
6. session TTL = **5 分鐘**（每次 TS 請求成功時自動延長）
7. 回傳 `{ sessionToken, expiresAt }`
8. 同步寫入 `VmsStreamLog`（stream_type, started_at, camera_id, user_id, session_token）

**代理請求（GET master.m3u8 / .ts）：**
1. 解析 `sessionToken` 取得 session
2. 驗證 session 未過期
3. **比對 session.userId 與 JWT 中的 userId** — 防止 sessionToken 被其他用戶使用
4. 用 session 中儲存的 NX Token 向 NX Server 發起請求
5. 回傳前 rewrite m3u8 中的 URL（將 NX 回傳的路徑改為代理路徑）
6. `.ts` segment 請求驗證路徑不含 `..` 且以合法前綴開頭

### 4.4 NxTokenManager 設計

```java
@Component
public class NxTokenManager {
    // 多台 NX Server 支援：Map<serverId, TokenInfo>
    // TokenInfo: { token, expiresAt }
    
    @PostConstruct
    public void init()           // 啟動時為所有 active server 登入
    void refreshToken(Long serverId)  // 單台 refresh
    String getToken(Long serverId)    // 取得 token（拋 NxTokenNotAvailableException）
    void invalidateToken(Long serverId)  // 伺服器停用時清除
}
```

Token refresh 策略：
- 初始 refresh interval = `expiresInS × 0.9`
- refresh 失敗時保留舊 token 繼續服務
- 連續失敗 N 次記錄錯誤
- 所有 log 記錄只顯示 token 前 8 碼

### 4.5 VmsServer Entity

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | `bigserial PK` | |
| `name` | `varchar(100)` | 顯示名稱 |
| `vms_type` | `varchar(20)` | `NX_WITNESS` / `MILESTONE` / `AXXON` |
| `base_url` | `varchar(255)` | NX Server URL |
| `auth_type` | `varchar(20)` | `BASIC` / `TOKEN` / `CERT` |
| `auth_username` | `varchar(100)` | NX 登入帳號 |
| `auth_password` | `varchar(255)` | NX 登入密碼（AES 加密） |
| `api_token` | `varchar(500)` | Token 類型用 |
| `is_active` | `boolean` | 是否啟用 |
| `created_at` | `timestamptz` | |
| `updated_at` | `timestamptz` | |
| `tenant_id` | `bigint` | 多租戶 |

### 4.6 VmsCameraMapping Entity

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | `bigserial PK` | |
| `server_id` | `bigint FK → vms_server` | 所屬 VMS Server |
| `vms_camera_id` | `varchar(100)` | NX 端的 camera UUID（如 `e3e9a385-...`） |
| `display_name` | `varchar(200)` | 顯示名稱 |
| `dept_id` | `bigint FK → dept` | 所屬部門（權限過濾用） |
| `status` | `varchar(20)` | `ONLINE` / `OFFLINE` / `ERROR` |
| `rtsp_url` | `varchar(500)` | 備用 RTSP |
| `created_at` | `timestamptz` | |
| `updated_at` | `timestamptz` | |
| `tenant_id` | `bigint` | 多租戶 |

> `vms_camera_id` 即 NX 端原生 UUID，無需另外對應本地 device。
> `status` 在「從 NX Server 同步」時更新，也可在建立串流時即時查驗 NX 端狀態。

### 4.7 VmsStreamLog Entity

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | `bigserial PK` | |
| `user_id` | `bigint FK → user` | 誰看的 |
| `camera_id` | `bigint FK → vms_camera_mapping` | 看哪支 |
| `stream_type` | `varchar(10)` | `LIVE` / `PLAYBACK` |
| `session_token` | `varchar(36)` | UUID |
| `started_at` | `timestamptz` | 開始播放時間 |
| `ended_at` | `timestamptz` | 結束時間（可 null） |
| `duration_seconds` | `int` | 持續秒數 |
| `playback_start_time` | `timestamptz` | 歷史起點（playback 才有） |
| `playback_end_time` | `timestamptz` | 歷史終點（playback 才有） |
| `tenant_id` | `bigint` | 多租戶 |

### 4.8 Flyway Migration

命名慣例：`V{數字}__{敘述}.sql`（最新為 V25）

- `V26__create_vms_server.sql`
- `V27__create_vms_camera_mapping.sql`
- `V28__create_vms_stream_log.sql`
- `V29__seed_vms_menus.sql`

---

## 5. 前端設計

### 5.1 元件樹

```
App.vue
└── VmsLayout.vue (or injected via menuStore into TenantLayout)
    ├── VmsLiveView.vue                     ← Route: /vms/live
    │   ├── VmsCameraList.vue               ← 左側扁平列表（名稱 + 狀態燈號）
    │   │   └── VmsCameraListItem.vue
    │   └── VmsStreamPanel.vue              ← 右側播放區
    │       └── VmsStreamPlayer.vue         ← 封裝 hls.js
    │
    ├── VmsPlaybackView.vue                 ← Route: /vms/playback
    │   ├── VmsCameraList.vue
    │   ├── VmsTimeRangePicker.vue          ← 選擇歷史時間區間
    │   └── VmsStreamPanel.vue
    │       └── VmsStreamPlayer.vue         ← 含 PlaybackControlBar
    │           ├── PlaybackControlBar.vue  ← 進度條 seek + 時間顯示
    │           │   └── TimelineSlider.vue
    │           └── SpeedControl.vue        ← 下拉選單（1x/2x/4x/8x/-1x/-2x/-4x/-8x）
    │
    ├── VmsServerManageView.vue             ← Route: /vms/servers
    │   ├── VmsServerTable.vue
    │   └── VmsServerDialog.vue             ← 新增/編輯 dialog
    │
    ├── VmsCameraManageView.vue             ← Route: /vms/cameras
    │   ├── VmsCameraTable.vue
    │   └── VmsCameraDialog.vue             ← 新增/編輯 dialog
    │
    └── VmsStreamLogView.vue                ← Route: /vms/stream-logs
        ├── VmsStreamLogFilter.vue          ← 過濾條件
        └── VmsStreamLogTable.vue
```

### 5.2 元件職責

| 元件 | 職責 |
|------|------|
| VmsCameraList | 取得攝影機列表、顯示扁平列表 + 狀態燈號（ONLINE 綠 / OFFLINE 灰），emit select 事件 |
| VmsStreamPlayer | 封裝 hls.js、管理 Hls instance、自動附加 JWT header、處理 NETWORK_ERROR 重試 |
| PlaybackControlBar | 顯示進度條（可拖動 seek）、當前時間 / 總區間顯示 |
| SpeedControl | 下拉選單選擇播放速度（支援正值快進 / 負值倒播） |
| VmsTimeRangePicker | 選擇歷史播放的開始/結束時間 |

### 5.3 hls.js 配置差異

```typescript
// Live
const liveConfig = {
  lowLatencyMode: true,
  backBufferLength: 30,
}

// Playback
const playbackConfig = {
  maxBufferLength: 240,
  backBufferLength: 60,
}
```

### 5.4 速度控制流程

```
SpeedControl dropdown
    │
    ├─ 選 +2x → GET .../trickplay?speed=2
    ├─ 選 -1x → GET .../trickplay?speed=-1
    │
    ▼
後端: 用 NX Token 向 NX 請求
  GET {nxServer}/hls/{cameraId}.m3u?pos={currentTime}&speed={speed}
    │
    ▼
NX 回傳 trickplay m3u8（降幀率專用）
    │
    ▼
hls.js 載入 trickplay m3u8（標準 HLS，無需修改）
    │
    ▼
切回 1x → 重新請求正常速度 m3u8?pos={currentTime}
```

### 5.5 狀態管理 (Pinia: vmsStore)

```typescript
interface VmsState {
  cameras: VmsCamera[]              // 攝影機列表
  selectedCamera: VmsCamera | null
  streamType: 'live' | 'playback'
  playbackRange: { startTime: string; endTime: string } | null
  // 播放器狀態（元件 local state，不在 store 中）
  // sessionToken, hlsInstance, currentSpeed, currentTime
}
```

---

## 6. 錯誤處理

### 6.1 情境對應

| 情境 | 前端 | 後端 |
|------|------|------|
| Camera 離線 | 顯示離線佔位圖 | 400 + `CAMERA_OFFLINE` |
| NX Token refresh 失敗 | 顯示串流服務異常 | log 前 8 碼，保留舊 token |
| sessionToken 過期 | 自動重新取得 | 401 + `SESSION_EXPIRED` |
| 無權限 | 顯示無權限提示 | 403 + `ACCESS_DENIED` |
| 該時間無錄影 | 顯示無錄影提示 | 404 + `NO_RECORDING` |
| 網路中斷 | hls.js 重試 3 次 → 顯示中斷 | — |
| NX Server 異常 | 顯示伺服器異常 | 502 + `VMS_SERVER_ERROR` |
| 快進超出 endTime | 自動停止播放 | 回傳 `PLAYBACK_ENDED` |

### 6.2 三態覆蓋

每個元件都必須處理：
- **Loading**：skeleton / spinner
- **Empty**：empty state + 引導文字
- **Error**：錯誤訊息 + 重試按鈕

---

## 7. 串流播放流程

### 7.1 即時播放

```
1. 選攝影機 → emit select(camera)
2. POST /v1/auth/vms/{cameraId}/stream { type: "live" }
3. 後端: 驗證權限 → createSession(userId, cameraId) → 回傳 sessionToken
4. hls.js: GET .../master.m3u8 (JWT + sessionToken)
5. 後端: HlsProxyService.fetchMasterPlaylist(sessionToken)
   → 用 NX Token 向 NX 請求 m3u8
   → rewrite URL → 回傳
6. hls.js 解析 m3u8 → 自動請求 sub-playlist / .ts segments
7. DELETE stream/{sessionToken} (使用者離開)
```

### 7.2 歷史播放

```
1. 選攝影機 + 時間區間
2. POST /v1/auth/vms/{cameraId}/stream { type: "playback", startTime, endTime }
3. 後端: 驗證權限 → createSession → 回傳 sessionToken + 儲存時間區間
4. hls.js: GET .../master.m3u8?pos={startTime unix ms}
5. 後端: 向 NX 請求含 pos 參數的 m3u8 → rewrite → 回傳
6. 使用者操作:
   - 拖動進度條 → 重新載入新時間點 m3u8?pos={newTime}
   - 切換速度 → trickplay?speed={倍數}
   - 時間到達 endTime → 自動停止
```

---

## 8. 前端類型定義 (TypeScript)

沿用現有 `frontend/src/types/vms.ts`，需調整：

- 移除 `VmsCameraRequest.deviceId`（不再綁定 IoT device）
- `VmsCamera.id` 改為 `number`（本地 PK），新增 `vmsCameraId: string`（NX UUID）
- 新增 `StreamCreateRequest` / `StreamCreateResponse`
- 新增 `VmsStreamLog` type

---

## 9. 依賴關係

### Flyway Migration 順序

```
V26 建立 vms_server 表
  └→ V27 建立 vms_camera_mapping 表（FK → vms_server）
       └→ V28 建立 vms_stream_log 表（FK → vms_camera_mapping）
            └→ V29 插入預設選單（需等 V28 完成才 seed）
```

### 後端套件依賴

```
vms/controller  → vms/service
                → auth/security (JWT 驗證)
                → dept/service (資料權限過濾)
vms/service     → vms/token (NxTokenManager)
                → vms/session (HlsSessionManager)
                → vms/entity (JPA)
                → common/ (BaseResponse, ErrorCode)
```

---

## 10. 未來可擴充

- PTZ 控制 → 在 `/v1/auth/vms/` 下新增 `ptz/` endpoints
- VMS 事件 → 新增 `events/` endpoints + WebSocket push
- 多 VMS 類型（Milestone / Axxon）→ `VmsAdapter` 介面隔離
