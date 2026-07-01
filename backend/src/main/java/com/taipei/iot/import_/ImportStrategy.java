package com.taipei.iot.import_;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 匯入策略介面 — 每個實體類型（device、contract、material...）實作此介面， 定義匯入流程中各階段的實體邏輯。
 *
 * @param <T> DTO 類型
 */
public interface ImportStrategy<T> {

	/** 實體類型名稱（用於日誌、錯誤訊息、回傳 entityType） */
	String getEntityType();

	/** 預期的標題列（全小寫），用於比對檔案欄位是否正確 */
	Set<String> expectedHeaders();

	/** 將一列原始資料 Map<欄位名, 值> 映射為 DTO，僅做型別轉換不做關聯查詢 */
	T mapToDto(Map<String, String> row);

	/** 逐筆驗證 DTO（必填、格式、列舉值、device_type 存在等） */
	List<ImportError> validate(T dto, int rowNum);

	/** 批量驗證（依賴資料庫的關聯查詢、唯一性檢查） */
	List<ImportError> batchValidate(List<T> dtos);

	/** 批量寫入（單一 @Transactional） */
	void saveAll(List<T> rows);

	/** 寫入前預處理（可選） */
	default void beforeAll(List<T> rows) {
	}

	/** 寫入後後處理（可選） */
	default void afterAll(List<T> rows) {
	}

}
