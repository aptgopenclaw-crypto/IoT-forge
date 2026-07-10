# 公告模組單元測試執行計畫

> **對應規格**：`announcement-module-spec.md`
> **目標版本**：JUnit 5 + Mockito 5（`@ExtendWith(MockitoExtension.class)`）
> **最後更新**：2026-07-10

---

## 1. 測試檔案清單

| 測試類別 | 對應 Source 類別 | 狀態 | 覆蓋功能群 |
|---|---|---|---|
| `AnnouncementServiceTest` | `AnnouncementService` | ✅ 已存在，需補充 | F1, F2, F5 |
| `AnnouncementReadServiceTest` | `AnnouncementReadService` | ✅ 已存在，需補充 | F3, F4 |
| `AnnouncementControllerTest` | `AnnouncementController` | ✅ 已存在，需補充 | F1~F6（Controller 層） |
| `AnnouncementAttachmentServiceTest` | `AnnouncementAttachmentService` | ❌ 尚未建立 | F6 |

---

## 2. AC 對照表與執行狀態

### 圖例

| 符號 | 含義 |
|---|---|
| ✅ | 已有對應測試（但可能尚未採用 AC 命名規範，見第 3 節） |
| 🔴 | 需新增 `@Test` 方法 |
| ⚪ | 由 Controller 整合測試覆蓋，不需 Service 單元測試 |

---

### F1 — 前台公告查詢與詳情（`AnnouncementServiceTest`）

| AC | 描述摘要 | 狀態 | 目標測試方法名稱 |
|---|---|---|---|
| AC-F1-1 | 3 筆可見公告（ALL+DEPT）回傳，含正確 `isRead` | 🔴 | `AC_F1_1_listVisible_mixedScope_returnsThreeWithIsRead` |
| AC-F1-2 | 已讀 1 筆 `isRead=true`，其餘 `false` | 🔴 | `AC_F1_2_listVisible_oneRead_isReadCorrect` |
| AC-F1-3 | `lang=en` 優先翻譯子表，無則 fallback zh-TW | 🔴 | `AC_F1_3_listVisible_langEn_fallsBackToZhTW` |
| AC-F1-4 | `getById` 回傳含 `isRead`；管理員額外有 `editable=true` | ✅ | `AC_F1_4_getById_withManagePermission_editableTrue` |
| AC-F1-5 | `deptId=null` → 傳入 Repository 的 deptId 為 `-1L` | 🔴 | `AC_F1_5_listVisible_nullDeptId_usesMinusOne` |
| AC-F1-6 | `publishAt > now` 的公告不出現在列表 | 🔴 | `AC_F1_6_listVisible_scheduledAnnouncement_notVisible` |
| AC-F1-7 | `expireAt < now` 的公告不出現在列表 | ✅ | `AC_F1_7_getById_expiredAnnouncement_throwsNotFound` |
| AC-F1-8 | `getById` 對不可見公告拋 `ANNOUNCEMENT_NOT_FOUND` | ✅ | `AC_F1_8_getById_unpublishedOrScopeMismatch_throwsNotFound` |
| AC-F1-9 | 非法 category 由 `normalizeCategoryFilter` 轉 null | 🔴 | `AC_F1_9_listVisible_invalidCategory_normalizedToNull` |
| AC-F1-10 | 租戶過濾（`@Filter` 已啟用，A 租不見 B 租） | ⚪ | *(整合測試覆蓋)* |
| AC-F1-11 | 未登入請求被 Spring Security 攔截 | ✅ | `AC_F1_11_list_unauthenticated_returns401`（Controller 層） |

---

### F2 — 管理端公告 CRUD（`AnnouncementServiceTest`）

| AC | 描述摘要 | 狀態 | 目標測試方法名稱 |
|---|---|---|---|
| AC-F2-1 | ADMIN 新增 scope=ALL，`createdBy` = 當前 userId | 🔴 | `AC_F2_1_create_adminScopeAll_createdByEqualsUserId` |
| AC-F2-2 | scope=DEPT，targetDeptIds=[10,20]，saveAll 寫入 2 筆 | 🔴 | `AC_F2_2_create_deptScope_savesJunctionTwoRows` |
| AC-F2-3 | 帶翻譯新增，`saveAll` 被呼叫，zh-TW 不重複寫入子表 | 🔴 | `AC_F2_3_create_withTranslations_zhTWNotDuplicated` |
| AC-F2-4 | 正確 version 編輯成功，`saveAndFlush` 呼叫一次 | ✅ | `AC_F2_4_update_correctVersion_saveAndFlushOnce` |
| AC-F2-5 | 刪除成功，`delete(entity)` 被呼叫 | ✅ | `AC_F2_5_delete_admin_deleteCalled` |
| AC-F2-6 | DEPT_ADMIN 強制 scope=DEPT，targetDeptIds=[自身部門] | ✅ | `AC_F2_6_create_deptAdmin_forcesDeptscopeAndOwnDept` |
| AC-F2-7 | scope=DEPT，targetDeptIds 空 → `VALIDATION_ERROR` | ✅ | `AC_F2_7_create_deptScopeNoTargets_throwsValidationError` |
| AC-F2-8 | 編輯無 version → `VALIDATION_ERROR` | ✅ | `AC_F2_8_update_missingVersion_throwsValidationError` |
| AC-F2-9 | version 不符 → `ANNOUNCEMENT_VERSION_CONFLICT` | ✅ | `AC_F2_9_update_staleVersion_throwsVersionConflict` |
| AC-F2-10 | `<script>` 被 sanitize 移除；`contentText` 無 HTML 標籤 | 🔴 | `AC_F2_10_create_xssContent_scriptTagRemoved` |
| AC-F2-11 | `publishAt=null` → Service 設為 `now()` | 🔴 | `AC_F2_11_create_nullPublishAt_setsToNow` |
| AC-F2-12 | 置頂未提供 `pinOrder` → 自動 `max+1` | 🔴 | `AC_F2_12_create_pinnedNoOrder_autoMaxPlusOne` |
| AC-F2-13 | `pinned=false` → `pinOrder` 設為 null | 🔴 | `AC_F2_13_create_notPinned_pinOrderIsNull` |
| AC-F2-14 | `expireAt < publishAt` → Bean Validation 失敗 | ✅ | `AC_F2_14_create_expireBeforePublish_returnsBadRequest`（Controller 層） |
| AC-F2-15 | DEPT_ADMIN 編輯他人公告 → `PERMISSION_DENIED` | ✅ | `AC_F2_15_update_deptAdminNotOwner_throwsPermissionDenied` |
| AC-F2-16 | DEPT_ADMIN 刪除他人公告 → `PERMISSION_DENIED` | ✅ | `AC_F2_16_delete_deptAdminNotOwner_throwsPermissionDenied` |
| AC-F2-17 | 無 `ANNOUNCEMENT_MANAGE` → 403 | ✅ | `AC_F2_17_create_withoutPermission_returns403`（Controller 層） |
| AC-F2-18 | 寫入操作觸發 `@AuditEvent` | ⚪ | *(Controller 整合測試或 AOP 測試覆蓋)* |

---

### F3 — 未讀計數與已讀標記（`AnnouncementReadServiceTest`）

| AC | 描述摘要 | 狀態 | 目標測試方法名稱 |
|---|---|---|---|
| AC-F3-1 | 3 筆未讀，`getUnreadCount` 回傳 `count=3` | ✅ | `AC_F3_1_getUnreadCount_returnsCountFromRepository` |
| AC-F3-2 | `existsById=true`，`markAsRead` 呼叫一次 | ✅ | `AC_F3_2_markAsRead_announcementExists_repositoryCalled` |
| AC-F3-3 | `markAllAsRead` 被呼叫，tenantId 來自 TenantContext | ✅ | `AC_F3_3_markAllAsRead_callsRepositoryWithCorrectParams` |
| AC-F3-4 | `deptId=null` → countUnread 收到 `-1L` | ✅ | `AC_F3_4_getUnreadCount_nullDeptId_usesMinusOne` |
| AC-F3-5 | 無未讀，count=0，不拋例外 | 🔴 | `AC_F3_5_getUnreadCount_noUnread_returnsZero` |
| AC-F3-6 | 重複呼叫 `markAsRead` 兩次不拋錯 | ✅ | `AC_F3_6_markAsRead_idempotent_noException` |
| AC-F3-7 | 跨租戶 announcementId，`existsById=false` → `NOT_FOUND` | ✅ | `AC_F3_7_markAsRead_crossTenant_throwsNotFound` |
| AC-F3-8 | `markAllAsRead` 的 tenantId 以 ArgumentCaptor 驗證 | ✅ | `AC_F3_8_markAllAsRead_tenantIdFromContext_captorVerified` |

---

### F4 — 已讀統計與未讀名單（`AnnouncementReadServiceTest`）

| AC | 描述摘要 | 狀態 | 目標測試方法名稱 |
|---|---|---|---|
| AC-F4-1 | scope=ALL，100 人 37 已讀，readRatio=0.3700 | 🔴 | `AC_F4_1_getReadStats_scopeAll_correctRatio` |
| AC-F4-2 | scope=DEPT，50/50 已讀，readRatio=1.0000 | 🔴 | `AC_F4_2_getReadStats_scopeDept_fullReadRatio` |
| AC-F4-3 | `getUnreadUsers` scope=ALL，回傳正確欄位 | 🔴 | `AC_F4_3_getUnreadUsers_scopeAll_returnsPagedResult` |
| AC-F4-4 | `totalAudience=0` → `readRatio=BigDecimal.ZERO` | 🔴 | `AC_F4_4_getReadStats_emptyAudience_ratioIsZero` |
| AC-F4-5 | scope=DEPT deptIds 空 → 直接回傳空 PageResponse | 🔴 | `AC_F4_5_getUnreadUsers_emptyDeptIds_returnsEmptyPage` |
| AC-F4-6 | keyword 空白 → safeKeyword=null（不過濾） | 🔴 | `AC_F4_6_getUnreadUsers_blankKeyword_passesNullToRepo` |
| AC-F4-7 | DEPT_ADMIN 查非自建公告 → `PERMISSION_DENIED` | 🔴 | `AC_F4_7_getReadStats_deptAdminNotOwner_throwsPermissionDenied` |
| AC-F4-8 | 需 `ANNOUNCEMENT_MANAGE` 權限 | ✅ | `AC_F4_8_getReadStats_withoutPermission_returns403`（Controller 層） |

---

### F5 — 置頂排序（`AnnouncementServiceTest`）

| AC | 描述摘要 | 狀態 | 目標測試方法名稱 |
|---|---|---|---|
| AC-F5-1 | `reorderPins([3,1,2])` → id=3 pinOrder=1，id=1 → 2，id=2 → 3 | 🔴 | `AC_F5_1_reorderPins_assignsPinOrderByIndex` |
| AC-F5-2 | ADMIN 回傳全部；DEPT_ADMIN 僅回傳可見 | 🔴 | `AC_F5_2_listPinned_admin_returnsAll` / `AC_F5_2_listPinned_deptAdmin_returnsFiltered` |
| AC-F5-3 | 需 `ANNOUNCEMENT_MANAGE` | ✅ | `AC_F5_3_listPinned_withoutPermission_returns403`（Controller 層） |

---

### F6 — 附件管理（`AnnouncementAttachmentServiceTest`，待建立）

| AC | 描述摘要 | 狀態 | 目標測試方法名稱 |
|---|---|---|---|
| AC-F6-1 | 5 個附件，上傳 PDF，`save()` 被呼叫，回傳正確欄位 | 🔴 | `AC_F6_1_upload_underLimit_saveCalledAndResponseReturned` |
| AC-F6-2 | `storagePath` 由 `FileStorageService.store()` 回傳值填入 | 🔴 | `AC_F6_2_upload_storagePathFromStorageService` |
| AC-F6-3 | 已達 10 個，拋 `ATTACHMENT_LIMIT_EXCEEDED`，`store` 未呼叫 | 🔴 | `AC_F6_3_upload_atLimit_throwsLimitExceeded` |
| AC-F6-4 | `.exe` 副檔名，在 FileValidationService 之前拋例外 | 🔴 | `AC_F6_4_upload_disallowedExtension_throwsBeforeValidation` |
| AC-F6-5 | `allowedExtensions` 預設 `pdf`；可透過 config 擴充 | 🔴 | `AC_F6_5_upload_configuredExtensions_allowsDocx` |
| AC-F6-6 | DEPT_ADMIN 操作他人公告 → `PERMISSION_DENIED` | 🔴 | `AC_F6_6_upload_deptAdminNotOwner_throwsPermissionDenied` |
| AC-F6-7 | virus scan 失敗情境（mock `FileValidationService` 拋例外） | 🔴 | `AC_F6_7_upload_virusScanFails_throwsException` |

---

## 3. 命名遷移：既有測試補充 AC 前綴

下列方法已有功能覆蓋但**尚未使用 AC 命名規範**，Code Review 時應以 `@DisplayName` 或 rename 補充對應關係（不要求刪除舊名，但需加上 DisplayName 標記）：

| 現有方法名稱 | 對應 AC | 建議 `@DisplayName` |
|---|---|---|
| `getUnreadCount_returnsCountFromRepository` | AC-F3-1 | `[AC-F3-1] getUnreadCount - 有未讀時回傳正確數量` |
| `getUnreadCount_nullDeptId_usesMinusOne` | AC-F3-4 | `[AC-F3-4] getUnreadCount - deptId=null 時傳入 -1L` |
| `markAsRead_callsRepositoryUpsert` | AC-F3-2 | `[AC-F3-2] markAsRead - 公告存在時呼叫 Repository upsert` |
| `markAsRead_idempotent_noException` | AC-F3-6 | `[AC-F3-6] markAsRead - 重複呼叫不拋例外` |
| `markAsRead_crossTenantAnnouncementId_throwsNotFound` | AC-F3-7 | `[AC-F3-7] markAsRead - 跨租戶防護拋 NOT_FOUND` |
| `markAllAsRead_callsRepositoryWithCorrectParams` | AC-F3-3 | `[AC-F3-3] markAllAsRead - 正確傳入 userId/tenantId/deptId` |
| `markAllAsRead_usesTenantContextStrictly_noCrossTenantLeak` | AC-F3-8 | `[AC-F3-8] markAllAsRead - tenantId 以 ArgumentCaptor 驗證` |
| `create_asAdmin_scopeAll_succeeds` | AC-F2-1 | `[AC-F2-1] create - ADMIN 新增 scope=ALL，createdBy 正確` |
| `create_asDeptAdmin_forceScopeToDept` | AC-F2-6 | `[AC-F2-6] create - DEPT_ADMIN 強制 scope=DEPT 及自身部門` |
| `create_deptScope_withoutDeptIds_throwsValidationError` | AC-F2-7 | `[AC-F2-7] create - scope=DEPT 無 targetDeptIds 拋 VALIDATION_ERROR` |
| `update_asAdmin_succeeds` | AC-F2-4 | `[AC-F2-4] update - 正確 version saveAndFlush 呼叫一次` |
| `update_asDeptAdmin_notOwner_throwsPermissionDenied` | AC-F2-15 | `[AC-F2-15] update - DEPT_ADMIN 編輯他人公告拋 PERMISSION_DENIED` |
| `update_staleVersion_throwsVersionConflict` | AC-F2-9 | `[AC-F2-9] update - version 不符拋 VERSION_CONFLICT` |
| `update_missingVersion_throwsValidationError` | AC-F2-8 | `[AC-F2-8] update - 無 version 拋 VALIDATION_ERROR` |
| `delete_asAdmin_succeeds` | AC-F2-5 | `[AC-F2-5] delete - ADMIN 刪除呼叫 delete(entity)` |
| `delete_asDeptAdmin_notOwner_throwsPermissionDenied` | AC-F2-16 | `[AC-F2-16] delete - DEPT_ADMIN 刪除他人公告拋 PERMISSION_DENIED` |
| `getById_withoutPermission_draftNotVisible_throwsNotFound` | AC-F1-8 | `[AC-F1-8] getById - 草稿對一般使用者不可見` |
| `getById_withoutPermission_expired_throwsNotFound` | AC-F1-7/AC-F1-8 | `[AC-F1-7] getById - 已過期公告不可見` |

---

## 4. 執行優先順序

依安全性影響與核心商業邏輯排定：

### Wave 1 — 安全性（最優先，本次 Sprint 必須完成）

| 優先 | AC | 測試方法 | 檔案 |
|---|---|---|---|
| P1 | AC-F3-7 | `AC_F3_7_markAsRead_crossTenant_throwsNotFound` | `AnnouncementReadServiceTest` ✅ |
| P1 | AC-F2-15 | `AC_F2_15_update_deptAdminNotOwner_throwsPermissionDenied` | `AnnouncementServiceTest` ✅ |
| P1 | AC-F2-16 | `AC_F2_16_delete_deptAdminNotOwner_throwsPermissionDenied` | `AnnouncementServiceTest` ✅ |
| P1 | AC-F4-7 | `AC_F4_7_getReadStats_deptAdminNotOwner_throwsPermissionDenied` | `AnnouncementReadServiceTest` 🔴 |
| P1 | AC-F6-6 | `AC_F6_6_upload_deptAdminNotOwner_throwsPermissionDenied` | `AnnouncementAttachmentServiceTest` 🔴 |
| P1 | AC-F2-10 | `AC_F2_10_create_xssContent_scriptTagRemoved` | `AnnouncementServiceTest` 🔴 |
| P1 | AC-F3-8 | `AC_F3_8_markAllAsRead_tenantIdFromContext_captorVerified` | `AnnouncementReadServiceTest` ✅ |

### Wave 2 — 核心商業規則

| 優先 | AC | 測試方法 | 檔案 |
|---|---|---|---|
| P2 | AC-F2-1 | `AC_F2_1_create_adminScopeAll_createdByEqualsUserId` | `AnnouncementServiceTest` 🔴 |
| P2 | AC-F2-2 | `AC_F2_2_create_deptScope_savesJunctionTwoRows` | `AnnouncementServiceTest` 🔴 |
| P2 | AC-F2-3 | `AC_F2_3_create_withTranslations_zhTWNotDuplicated` | `AnnouncementServiceTest` 🔴 |
| P2 | AC-F2-11 | `AC_F2_11_create_nullPublishAt_setsToNow` | `AnnouncementServiceTest` 🔴 |
| P2 | AC-F2-12 | `AC_F2_12_create_pinnedNoOrder_autoMaxPlusOne` | `AnnouncementServiceTest` 🔴 |
| P2 | AC-F2-13 | `AC_F2_13_create_notPinned_pinOrderIsNull` | `AnnouncementServiceTest` 🔴 |
| P2 | AC-F4-1 | `AC_F4_1_getReadStats_scopeAll_correctRatio` | `AnnouncementReadServiceTest` 🔴 |
| P2 | AC-F4-2 | `AC_F4_2_getReadStats_scopeDept_fullReadRatio` | `AnnouncementReadServiceTest` 🔴 |
| P2 | AC-F4-4 | `AC_F4_4_getReadStats_emptyAudience_ratioIsZero` | `AnnouncementReadServiceTest` 🔴 |
| P2 | AC-F5-1 | `AC_F5_1_reorderPins_assignsPinOrderByIndex` | `AnnouncementServiceTest` 🔴 |
| P2 | AC-F5-2 | `AC_F5_2_listPinned_admin_returnsAll` | `AnnouncementServiceTest` 🔴 |

### Wave 3 — 邊界值與查詢行為

| 優先 | AC | 測試方法 | 檔案 |
|---|---|---|---|
| P3 | AC-F1-1 | `AC_F1_1_listVisible_mixedScope_returnsThreeWithIsRead` | `AnnouncementServiceTest` 🔴 |
| P3 | AC-F1-2 | `AC_F1_2_listVisible_oneRead_isReadCorrect` | `AnnouncementServiceTest` 🔴 |
| P3 | AC-F1-3 | `AC_F1_3_listVisible_langEn_fallsBackToZhTW` | `AnnouncementServiceTest` 🔴 |
| P3 | AC-F1-5 | `AC_F1_5_listVisible_nullDeptId_usesMinusOne` | `AnnouncementServiceTest` 🔴 |
| P3 | AC-F1-6 | `AC_F1_6_listVisible_scheduledAnnouncement_notVisible` | `AnnouncementServiceTest` 🔴 |
| P3 | AC-F1-9 | `AC_F1_9_listVisible_invalidCategory_normalizedToNull` | `AnnouncementServiceTest` 🔴 |
| P3 | AC-F3-5 | `AC_F3_5_getUnreadCount_noUnread_returnsZero` | `AnnouncementReadServiceTest` 🔴 |
| P3 | AC-F4-3 | `AC_F4_3_getUnreadUsers_scopeAll_returnsPagedResult` | `AnnouncementReadServiceTest` 🔴 |
| P3 | AC-F4-5 | `AC_F4_5_getUnreadUsers_emptyDeptIds_returnsEmptyPage` | `AnnouncementReadServiceTest` 🔴 |
| P3 | AC-F4-6 | `AC_F4_6_getUnreadUsers_blankKeyword_passesNullToRepo` | `AnnouncementReadServiceTest` 🔴 |

### Wave 4 — F6 附件管理（新建測試類別）

| 優先 | AC | 測試方法 | 檔案 |
|---|---|---|---|
| P4 | AC-F6-1 | `AC_F6_1_upload_underLimit_saveCalledAndResponseReturned` | `AnnouncementAttachmentServiceTest` 🔴 |
| P4 | AC-F6-2 | `AC_F6_2_upload_storagePathFromStorageService` | `AnnouncementAttachmentServiceTest` 🔴 |
| P4 | AC-F6-3 | `AC_F6_3_upload_atLimit_throwsLimitExceeded` | `AnnouncementAttachmentServiceTest` 🔴 |
| P4 | AC-F6-4 | `AC_F6_4_upload_disallowedExtension_throwsBeforeValidation` | `AnnouncementAttachmentServiceTest` 🔴 |
| P4 | AC-F6-5 | `AC_F6_5_upload_configuredExtensions_allowsDocx` | `AnnouncementAttachmentServiceTest` 🔴 |
| P4 | AC-F6-7 | `AC_F6_7_upload_virusScanFails_throwsException` | `AnnouncementAttachmentServiceTest` 🔴 |

---

## 5. 測試骨架：`AnnouncementAttachmentServiceTest`（待建立）

```java
package com.taipei.iot.announcement.service;

import com.taipei.iot.announcement.entity.Announcement;
import com.taipei.iot.announcement.repository.AnnouncementAttachmentRepository;
import com.taipei.iot.announcement.repository.AnnouncementDeptRepository;
import com.taipei.iot.announcement.repository.AnnouncementRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.service.FileStorageService;
import com.taipei.iot.common.service.FileValidationService;
import com.taipei.iot.common.util.JwtClaimKeys;
import com.taipei.iot.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnnouncementAttachmentServiceTest {

    @InjectMocks
    private AnnouncementAttachmentService attachmentService;  // 需提供 allowedExtensionsConfig

    @Mock private AnnouncementRepository announcementRepository;
    @Mock private AnnouncementAttachmentRepository attachmentRepository;
    @Mock private AnnouncementDeptRepository announcementDeptRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private FileValidationService fileValidationService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId("TENANT_A");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void setSecurityContext(String userId, Long deptId, String dataScope) {
        Map<String, Object> details = new HashMap<>();
        details.put(JwtClaimKeys.TENANT_ID, "TENANT_A");
        details.put(JwtClaimKeys.DEPT_ID, deptId);
        details.put(JwtClaimKeys.DATA_SCOPE, dataScope);
        var auth = new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ANNOUNCEMENT_MANAGE")));
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("[AC-F6-1] upload - 5 個附件時上傳 PDF，save() 被呼叫回傳正確欄位")
    void AC_F6_1_upload_underLimit_saveCalledAndResponseReturned() { /* TODO */ }

    @Test
    @DisplayName("[AC-F6-2] upload - storagePath 來自 FileStorageService.store()")
    void AC_F6_2_upload_storagePathFromStorageService() { /* TODO */ }

    @Test
    @DisplayName("[AC-F6-3] upload - 已達 10 個上限拋 ATTACHMENT_LIMIT_EXCEEDED，store() 未呼叫")
    void AC_F6_3_upload_atLimit_throwsLimitExceeded() { /* TODO */ }

    @Test
    @DisplayName("[AC-F6-4] upload - 非白名單副檔名在 FileValidationService 前即拋例外")
    void AC_F6_4_upload_disallowedExtension_throwsBeforeValidation() { /* TODO */ }

    @Test
    @DisplayName("[AC-F6-5] upload - config 擴充允許 docx")
    void AC_F6_5_upload_configuredExtensions_allowsDocx() { /* TODO */ }

    @Test
    @DisplayName("[AC-F6-6] upload - DEPT_ADMIN 操作他人公告拋 PERMISSION_DENIED")
    void AC_F6_6_upload_deptAdminNotOwner_throwsPermissionDenied() { /* TODO */ }

    @Test
    @DisplayName("[AC-F6-7] upload - virus scan 失敗拋例外")
    void AC_F6_7_upload_virusScanFails_throwsException() { /* TODO */ }
}
```

---

## 6. 執行指令

```bash
# 執行 announcement 模組所有單元測試
cd backend && mvn test -Dtest="com.taipei.iot.announcement.**" -DfailIfNoTests=false

# 執行單一 Wave（例如 Wave 1 安全性測試）
mvn test -Dtest="AnnouncementReadServiceTest#AC_F3_7*+AnnouncementServiceTest#AC_F2_15*+AnnouncementServiceTest#AC_F2_16*"

# 執行新建的附件測試
mvn test -Dtest="AnnouncementAttachmentServiceTest"

# 產生覆蓋率報告（jacoco）
mvn verify -DskipITs jacoco:report
```

---

## 7. 完成驗收標準（Definition of Done）

- [ ] 所有 🔴 狀態的 AC 均有對應 `@Test` 方法，且方法名稱以 AC 編號開頭。
- [ ] 所有 ✅ 已有測試的方法均已補上 `@DisplayName("[AC-Fxx-yy] ...")`。
- [ ] `mvn test -Dtest="com.taipei.iot.announcement.**"` 執行全數通過，無 FAILED / ERROR。
- [ ] `AnnouncementAttachmentServiceTest` 已建立，Wave 4 的 7 個 AC 全部有方法覆蓋。
- [ ] 可追溯性人工核查：每條 AC 對應恰好至少一個 `@Test`；無無主冗餘測試。
