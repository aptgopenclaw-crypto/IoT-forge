package com.taipei.iot.eventrule.action;

import com.taipei.iot.eventrule.model.ActionConfig;
import com.taipei.iot.eventrule.model.ActionType;

/**
 * 規則動作處理器擴展點。
 *
 * <p>
 * 新增動作類型 = 實作此介面並加 {@code @Component}，無需修改核心評估引擎。
 */
public interface RuleActionHandler {

	/** 是否支援指定動作類型。 */
	boolean supports(ActionType type);

	/**
	 * 執行動作。實作者應自行捕捉例外並記錄，不應向上拋出（避免影響同規則其他動作）。
	 */
	void execute(RuleMatch match, ActionConfig action);

}
