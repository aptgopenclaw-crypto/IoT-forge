Updated todo list

在登入流程中，`dataScope` 是由使用者於目標租戶的 `UserTenantMappingEntity` 所關聯的角色決定：

```java
mapping.getRole() != null ? mapping.getRole().getDataScope() : "ALL"
```

也就是：

- 單一租戶登入：取得唯一啟用的 tenant mapping，將該 mapping 的 `role.dataScope` 放進 access token。
- 多租戶登入：首次只簽發 temporary token，尚未決定 `dataScope`；使用者呼叫 `selectTenant` 後，才依所選租戶 mapping 的 role 決定。
- 切換租戶：同樣依新租戶 mapping 的 `role.dataScope` 重新簽發 token。
- Refresh token：重新查詢 refresh token 綁定租戶的 mapping，再取得其 role 的 `dataScope`，因此角色設定變動後下一次 refresh 會生效。
- Super admin：固定使用 `"ALL"`。

若 mapping 沒有角色或 `mapping.getRole()` 是 `null`，會保守地 fallback 為 `"ALL"`。核心實作位於 AuthServiceImpl.java 的 `buildPostAuthLoginResult`、`issueTenantToken` 與 `refreshToken`。