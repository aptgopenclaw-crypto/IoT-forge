
Completed: *更新知識庫同步* (5/6)

搬移完成。以下是完整的改動摘要：

---

### 變更內容

**新增 schema 模組** (`com.taipei.iot.schema`)

| 檔案 | 新位置 |
|------|--------|
| Entity | `backend/.../schema/entity/DeviceTemplate.java` |
| Repository | `backend/.../schema/repository/DeviceTemplateRepository.java` |
| Service | `backend/.../schema/service/DeviceTemplateService.java` |
| Controller | `backend/.../schema/controller/DeviceTemplateController.java` |
| Frontend API | index.ts |
| Frontend View | DeviceTemplateView.vue |

**更新引用**

| 檔案 | 變更 |
|------|------|
| DeviceService.java | Import `device.service` → `schema.service` |
| DeviceServiceTest.java | 加入 `schema.service.DeviceTemplateService` import |
| index.ts | 移除 `getDeviceSchema` / `updateDeviceSchema` |
| index.ts | 路徑 `/device/templates` → `/schemas`，component import 指向新位置 |

**知識庫同步** ✅ — `schema` 模組已寫入知識圖譜，2 個 contracts 已產生

**測試結果** — 29 tests, 0 failures ✅

### 模組邊界現況

```
schema (device-template)
  ├── export: DeviceTemplateService
  ├── export: DeviceTemplateRepository
  └── API: /v1/auth/device-templates/*

device ───→ schema （僅依賴 DeviceTemplateService.validateAttributes）
telemetry ─→ schema （未來）
event-rule ─→ schema （未來）
```

未來 telemetry 和 event-rule 模組要讀取 device schema 時，只需依賴 `schema` 模組的 `DeviceTemplateService`，不需要碰整個 `device` 模組。

Made changes.