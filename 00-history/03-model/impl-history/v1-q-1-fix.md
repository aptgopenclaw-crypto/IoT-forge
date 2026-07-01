

## 問題一已修復完畢

三個子項目都已完成。以下是摘要：

### 更動內容

| 違規 | 修法 | 檔案量 |
|------|------|--------|
| `GlobalExceptionHandler` → `workflow` 例外 | workflow 例外處理遷出 common，新增 `WorkflowExceptionHandler`（該模組自帶 `@RestControllerAdvice`） | −1（import 移除）+ 1（新類別） |
| `TenantAwareQuery` → `tenant.TenantContext` | `TenantContext` 下沉到 `common.context`；舊 `tenant.TenantContext` 改為 `@Deprecated` 委派類別 | +1（新檔案）+ 更新 24 個檔案 import |
| `DataScopePredicates` → `dept.DataScopeEnum` | `DataScopeEnum` 下沉到 `common.enums`；舊 `dept.enums.DataScopeEnum` 標 `@Deprecated` | +1（新檔案）+ 更新 6 個檔案 import |

### 為什麼這樣改

- **`TenantContext`** 是純 ThreadLocal 基礎設施，無業務邏輯，跨 12 個模組、25 個檔案使用——它本來就不該在 `tenant` 業務模組。
- **`DataScopeEnum`** 是跨模組的共用列舉，同理下沉。
- **Workflow 例外處理**歸 workflow 模組自有——每個模組可自帶 `@RestControllerAdvice`，不需集中在 common。

### 向後相容

舊類別保留為 `@Deprecated` 委派（delegate 給新位置），即使有遺漏的 import 仍可編譯通過。後續可視情況移除。

Made changes.