package com.taipei.iot.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

	// 00xxx: 通用（成功、驗證失敗）
	SUCCESS("00000", 200, "操作成功"), VALIDATION_ERROR("00001", 400, "請求參數驗證失敗"),

	// 10xxx: 認證與授權（10001-10019 AUTH，10020-10029 TENANT）
	ACCESS_TOKEN_INVALID("10001", 401, "access token 無效"), ACCOUNT_LOCKED("10002", 401, "帳號鎖定中"),
	ACCOUNT_DISABLED("10003", 401, "帳號已停用"), REFRESH_TOKEN_INVALID("10004", 401, "refresh token 無效"),
	ACCESS_TOKEN_GET_INFO_FAILED("10005", 401, "從 token 解析使用者失敗"),
	// [v2 N-7] Session（登入裝置）管理
	SESSION_NOT_FOUND("10006", 404, "登入裝置不存在"), CAPTCHA_INVALID("10007", 400, "圖形驗證碼錯誤"),
	// 10008-10009: 預留（第三方 OAuth 相關，尚未實作）
	PERMISSION_DENIED("10010", 403, "無操作權限"),
	// 10011-10012: 預留（細粒度資源層級存取控制，尚未實作）
	LOGIN_FAIL("10013", 401, "登入失敗"),
	// 10014-10017: 預留（MFA 多因素驗證流程，尚未實作）
	CAPTCHA_EXPIRED("10018", 400, "驗證碼已過期"), CAPTCHA_GENERATION_FAILED("10019", 500, "驗證碼生成失敗"),
	AUTH_PROVIDER_NOT_SUPPORTED("10025", 500, "不支援的認證提供者"), TENANT_NOT_FOUND("10020", 404, "場域不存在"),
	TENANT_ACCESS_DENIED("10021", 403, "無此場域存取權限"), TENANT_SELECTION_REQUIRED("10022", 403, "需先選擇場域"),
	TENANT_CODE_DUPLICATE("10023", 409, "場域代碼已存在"), TENANT_DISABLED("10024", 403, "場域已停用"),
	// [Tenant v2 T-5] single 部署模式下，JWT 帶的 tenantId 與 tenant.default-id 不一致
	TENANT_MODE_MISMATCH("10026", 403, "JWT 場域與部署模式不一致"),

	// [Platform/Tenant Separation ADR-002] Impersonation
	IMPERSONATION_DURATION_INVALID("10027", 400, "代操時長需介於 1~60 分鐘"),
	IMPERSONATION_SESSION_NOT_FOUND("10028", 404, "代操 session 不存在"),
	IMPERSONATION_NOT_OWNER("10029", 403, "非該代操 session 發起者"),

	// [Platform/Tenant Separation ADR-007 / Phase 3.1.4] ScopeEnforcementFilter enforce
	// mode
	SCOPE_FORBIDDEN("10031", 403, "Token scope 與請求路徑不符"),
	// [Multi-Auth] LDAP / AD 驗證錯誤
	LDAP_AUTH_FAILED("10032", 401, "AD 帳號或密碼錯誤"), LDAP_SERVICE_UNAVAILABLE("10033", 503, "AD 服務暫時無法連線，請稍後再試"),
	LDAP_CONFIG_INVALID("10034", 500, "AD 連線設定錯誤"),

	// 20xxx: 使用者管理
	// 20001-20002: 預留（批次建立帳號 / 匯入，尚未實作）
	RESET_PASSWORD_ERROR("20003", 400, "密碼不符合規則"),
	// 20004: 預留（信箱驗證流程，尚未實作）
	USER_NOT_FOUND("20005", 404, "查無使用者"),
	// 20006-20013: 預留（帳號申請 / 審核流程，尚未實作）
	RESET_PASSWORD_INVALID_TOKEN("20014", 400, "重設密碼 token 無效或過期"), USER_ALREADY_EXISTS("20015", 409, "帳號已存在"),
	// PASSWORD_EXPIRED 使用 403：使用者已通過身份驗證，但密碼過期不允許操作；
	// 401 會導致前端重試登入連結，應導向「修改密碼」頁面
	PASSWORD_EXPIRED("20016", 403, "密碼已過期"), PASSWORD_RECENTLY_USED("20017", 400, "密碼與近期相同"),
	MAPPING_NOT_FOUND("20018", 404, "場域存取不存在"), OLD_PASSWORD_INCORRECT("20019", 400, "舊密碼錯誤"),
	// 20020: Phase 3 — force-change-password 流程 token 驗證失敗（過期、非 password_change 用途、已使用）
	FORCE_CHANGE_PASSWORD_TOKEN_INVALID("20020", 400, "強制改密 token 無效或過期"),

	// 30xxx: RBAC
	ROLE_CODE_DUPLICATE("30001", 409, "角色代碼已存在"), ROLE_BUILTIN_READONLY("30002", 400, "內建角色不可修改"),
	ROLE_NOT_FOUND("30003", 404, "角色不存在"), ROLE_HAS_USERS("30004", 400, "角色已被指派使用者，無法刪除"),
	MENU_NOT_FOUND("30005", 404, "選單不存在"), MENU_HAS_CHILDREN("30006", 400, "選單有子節點，請先刪除子選單"),
	MENU_CYCLE_DETECTED("30007", 400, "選單 parentId 形成循環參考"),

	// 40xxx: 部門
	DEPT_NOT_FOUND("40001", 404, "部門不存在"), DEPT_ALREADY_EXISTS("40002", 400, "部門名稱已存在"),
	DEPT_HAS_CHILDREN("40003", 400, "部門下有子部門，無法刪除"), DEPT_HAS_USERS("40004", 400, "部門下有使用者，無法刪除"),

	// 50xxx: 公告
	ANNOUNCEMENT_NOT_FOUND("50001", 404, "公告不存在"), ANNOUNCEMENT_VERSION_CONFLICT("50002", 409, "公告已被其他人更新，請重新整理後再試"),

	// 55xxx: 通知
	NOTIFICATION_NOT_FOUND("55001", 404, "通知不存在"),

	// 56xxx: 系統設定
	SETTING_NOT_FOUND("56001", 404, "系統設定不存在"), SETTING_INVALID_VALUE("56002", 400, "設定值不合法"),
	SETTING_VERSION_CONFLICT("56010", 409, "設定已被他人修改，請重新載入"),
	PASSWORD_POLICY_BELOW_PLATFORM_MINIMUM("56003", 400, "密碼策略低於平台下限"),
	PASSWORD_POLICY_INVALID_KEY("56004", 400, "不支援的密碼策略 key"),
	PASSWORD_POLICY_INVALID_VALUE("56005", 400, "密碼策略設定值格式不正確"),

	// 60xxx: 資產管理
	DEVICE_NOT_FOUND("60001", 404, "設備不存在"), DEVICE_CODE_DUPLICATE("60002", 409, "設備代碼已存在"),
	DEVICE_CIRCULAR_REFERENCE("60003", 400, "設備拓撲存在循環參照"), DEVICE_HAS_CHILDREN("60004", 400, "設備下有子設備，無法刪除"),
	DEVICE_HAS_OPEN_FAULTS("60005", 400, "設備有未結障礙工單"), DEVICE_TYPE_NOT_DEFINED("60006", 400, "設備類型尚未定義模板"),
	CIRCUIT_NOT_FOUND("60010", 404, "回路不存在"), CIRCUIT_HAS_DEVICES("60011", 400, "回路下有設備，無法刪除"),
	CONTRACT_NOT_FOUND("60020", 404, "契約不存在"),

	// 70xxx: 報修維護
	REPAIR_TICKET_NOT_FOUND("70001", 404, "報修工單不存在"), REPAIR_TICKET_INVALID_STATUS("70002", 400, "報修工單狀態不正確"),
	DISPATCH_NOT_FOUND("70010", 404, "派工紀錄不存在"), ATTACHMENT_NOT_FOUND("70020", 404, "附件不存在"),
	ATTACHMENT_UPLOAD_FAILED("70021", 500, "附件上傳失敗"), FILE_EXTENSION_NOT_ALLOWED("70022", 400, "不允許的檔案類型"),
	FILE_TYPE_MISMATCH("70023", 400, "檔案內容與副檔名不符"), FILE_SIZE_EXCEEDED("70024", 400, "檔案大小超過限制"),
	FILE_VIRUS_DETECTED("70025", 400, "檔案包含惡意內容"), INSPECTION_TASK_NOT_FOUND("70030", 404, "巡查任務不存在"),
	INSPECTION_RECORD_NOT_FOUND("70031", 404, "巡查紀錄不存在"), WORK_ORDER_NOT_FOUND("70040", 404, "工單不存在"),
	WORK_ORDER_INVALID_STATUS("70041", 400, "工單狀態不正確"), WORK_ORDER_NOT_ASSIGNED_TO_USER("70042", 403, "非指定工單人員"),
	WORK_ORDER_ALREADY_ASSIGNED("70043", 400, "工單已被指派"), WORK_ORDER_CANNOT_REOPEN("70044", 400, "已結案工單不可重新開啟"),

	// 80xxx: 換裝維護
	REPLACEMENT_ORDER_NOT_FOUND("80001", 404, "換裝派工單不存在"), REPLACEMENT_INVALID_STATUS("80002", 400, "換裝派工單狀態不正確"),
	POLE_NUMBER_DUPLICATE("80020", 400, "號碼牌編號重複"), POLE_NUMBER_NOT_FOUND("80021", 404, "號碼牌不存在"),

	// 85xxx: 材料管理
	MATERIAL_SPEC_NOT_FOUND("85001", 404, "材料規格不存在"), INSUFFICIENT_INVENTORY("85002", 400, "庫存不足"),
	INVENTORY_NOT_FOUND("85003", 404, "庫存紀錄不存在"), WAREHOUSE_NOT_FOUND("85004", 404, "庫別不存在"),
	SUPPLIER_NOT_FOUND("85005", 404, "廠商不存在"), PURCHASE_ORDER_NOT_FOUND("85006", 404, "採購單不存在"),
	ISSUE_REQUEST_NOT_FOUND("85007", 404, "領料申請不存在"), APPROVED_MATERIAL_NOT_FOUND("85008", 404, "合格材料不存在"),
	INVALID_ISSUE_REQUEST_STATUS("85009", 400, "領料申請狀態不正確"), MATERIAL_NOT_APPROVED("85010", 400, "材料未通過審驗"),
	MATERIAL_NOT_AVAILABLE("85011", 400, "材料不可用"),

	// 86xxx: 績效管理 (KPI)
	KPI_INDICATOR_NOT_FOUND("86001", 404, "KPI 指標不存在"), KPI_INDICATOR_CODE_DUPLICATE("86002", 409, "KPI 指標代碼已存在"),
	KPI_INDICATOR_HAS_DATA("86003", 400, "KPI 指標已有計算數據，僅可停用"), KPI_FORMULA_INVALID("86004", 400, "KPI 公式語法錯誤"),
	KPI_FORMULA_TYPE_UNSUPPORTED("86005", 400, "不支援的公式類型"), KPI_PERIOD_LOCKED("86010", 400, "該期間已鎖定，不可修改"),
	KPI_PERIOD_NOT_LOCKED("86011", 400, "該期間未鎖定"), KPI_DATA_IMPORT_FAILED("86020", 400, "KPI 數據匯入失敗"),

	// 87xxx: 儀表板
	DASHBOARD_LAYOUT_NOT_FOUND("87001", 404, "儀表板版面不存在"), DASHBOARD_DEFAULT_NOT_FOUND("87002", 404, "儀表板預設版面不存在"),

	// A2xx: 設備匯入
	DEVICE_IMPORT_FILE_EMPTY("A200", 400, "上傳檔案為空"),
	DEVICE_IMPORT_FILE_FORMAT("A201", 400, "不支援的檔案格式，僅允許 .xlsx 與 .csv"),
	DEVICE_IMPORT_MAX_ROWS_EXCEEDED("A202", 400, "匯入筆數超過單次上限"),
	DEVICE_IMPORT_VALIDATION_FAILED("A203", 400, "部分資料驗證未通過"), DEVICE_IMPORT_HEADER_MISMATCH("A204", 400, "檔案標題列與範本不符"),
	CIRCUIT_IMPORT_VALIDATION_FAILED("A210", 400, "迴路資料驗證未通過"),
	CIRCUIT_IMPORT_HEADER_MISMATCH("A211", 400, "檔案標題列與範本不符"),
	CONTRACT_IMPORT_VALIDATION_FAILED("A220", 400, "標案資料驗證未通過"),
	CONTRACT_IMPORT_HEADER_MISMATCH("A221", 400, "檔案標題列與範本不符"),

	// 88xxx: 智慧路燈 (IoT) — Phase 7
	IOT_DEVICE_ALREADY_REGISTERED("88001", 409, "設備已註冊 IoT 功能"), IOT_DEVICE_TOKEN_INVALID("88002", 401, "設備 Token 無效"),
	IOT_TELEMETRY_FORMAT_NOT_FOUND("88010", 404, "Telemetry Format 不存在"),
	IOT_TELEMETRY_FORMAT_FIELD_IN_USE("88011", 400, "Format 欄位已被事件規則引用"),
	IOT_EVENT_RULE_NOT_FOUND("88020", 404, "事件規則不存在"), IOT_EVENT_RULE_DUPLICATE("88021", 409, "rule_code 已存在"),
	IOT_ALERT_NOT_FOUND("88030", 404, "告警不存在"), IOT_ALERT_INVALID_STATUS("88031", 400, "告警狀態不正確"),
	IOT_DIMMING_GROUP_NOT_FOUND("88040", 404, "調光群組不存在"), IOT_DIMMING_SCHEDULE_NOT_FOUND("88041", 404, "調光排程不存在"),
	IOT_METER_CIRCUIT_NOT_FOUND("88050", 404, "電表所屬回路不存在"), IOT_TELEMETRY_VALIDATION_FAILED("88060", 400, "遙測資料格式驗證失敗"),
	IOT_INGEST_CREDENTIALS_INVALID("88070", 401, "Ingest 憑證無效"),
	IOT_INGEST_CLIENT_DISABLED("88071", 403, "Ingest 用戶端已停用"), IOT_INGEST_DEVICE_NOT_MAPPED("88072", 404, "外部設備碼尚未映射"),
	IOT_TELEMETRY_QUERY_INVALID_RANGE("88080", 400, "遙測查詢時間範圍不正確"),

	// 881xx: VMS（影像監控）
	VMS_SERVER_NOT_FOUND("88100", 404, "VMS 伺服器不存在"), VMS_CAMERA_NOT_FOUND("88101", 404, "攝影機不存在"),
	VMS_TYPE_UNSUPPORTED("88102", 400, "不支援的 VMS 類型"), VMS_CONNECTION_FAILED("88103", 502, "VMS 連線失敗"),
	VMS_STREAM_NOT_AVAILABLE("88104", 503, "串流無法取得"), VMS_PLAYBACK_INVALID_RANGE("88105", 400, "歷史回放時間範圍無效"),
	VMS_PTZ_FAILED("88106", 500, "PTZ 控制失敗"), VMS_STREAM_TOKEN_INVALID("88107", 401, "串流 Token 無效或已過期"),
	VMS_EVENT_PROCESSING_FAILED("88108", 500, "VMS 事件處理失敗"), VMS_CAMERA_OFFLINE("88109", 400, "攝影機離線"),
	VMS_PLAYBACK_NO_RECORDING("88110", 404, "該時間區間無錄影"), VMS_PLAYBACK_ENDED("88111", 200, "播放已結束"),

	// 90xxx: 簽核引擎
	WORKFLOW_INSTANCE_NOT_FOUND("90001", 404, "流程實例不存在"), WORKFLOW_INVALID_TRANSITION("90002", 400, "不合法的狀態轉換"),
	WORKFLOW_NOT_ASSIGNED_TO_USER("90003", 403, "非指派簽核人"),
	WORKFLOW_SELF_APPROVAL_NOT_ALLOWED("90004", 400, "不可自己審核自己的案件"), DELEGATE_PERIOD_OVERLAP("90010", 400, "代理期間重疊"),
	DELEGATE_SELF_NOT_ALLOWED("90011", 400, "不可設定自己為代理人"), DELEGATE_END_DATE_REQUIRED("90012", 400, "代理結束日期為必填"),

	// 91xxx: 資產異動申請
	ASSET_TRANSFER_NOT_FOUND("91001", 404, "資產異動申請不存在"), ASSET_TRANSFER_PERMISSION_DENIED("91002", 403, "無權限操作此申請"),
	ASSET_TRANSFER_INVALID_STATUS("91003", 400, "申請狀態不符，無法執行此操作"),
	ASSET_TRANSFER_WORKFLOW_NOT_STARTED("91004", 400, "此申請尚未啟動流程"),

	// 10030-10039: 速率限制
	RATE_LIMIT_EXCEEDED("10030", 429, "請求過於頻繁，請稍後再試"),

	// 10035: CSRF 驗證失敗
	CSRF_VALIDATION_FAILED("10035", 403, "CSRF 驗證失敗"),

	// 99xxx: 系統
	UNKNOWN_ERROR("99999", 500, "未知錯誤");

	private final String code;

	private final int httpStatus;

	private final String message;

}
