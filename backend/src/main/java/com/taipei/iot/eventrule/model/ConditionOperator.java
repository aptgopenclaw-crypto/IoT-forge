package com.taipei.iot.eventrule.model;

/**
 * 葉節點比較運算子白名單。
 *
 * <p>
 * v1
 * 實作：{@link #GT}、{@link #LT}、{@link #EQ}。其餘運算子（GTE/LTE/NEQ/BETWEEN/IN/CONTAINS/CHANGED）
 * 為後續擴充，求值引擎以 switch 明確拒絕（白名單策略，避免未知運算子跳過比對）。
 */
public enum ConditionOperator {

	/** 數值大於（field > value）。 */
	GT,
	/** 數值小於（field < value）。 */
	LT,
	/** 等於（數值 / 字串 / 布林）。 */
	EQ,
	/** 大於等於（後續）。 */
	GTE,
	/** 小於等於（後續）。 */
	LTE,
	/** 不等於（後續）。 */
	NEQ,
	/** 區間（後續）。 */
	BETWEEN,
	/** 包含字串（後續）。 */
	CONTAINS

}
