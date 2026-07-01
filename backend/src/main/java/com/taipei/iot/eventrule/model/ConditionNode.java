package com.taipei.iot.eventrule.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 條件樹節點（結構化 JSON，對應 {@code event_rule.condition} JSONB 欄位）。
 *
 * <p>
 * 節點分兩類：
 * <ul>
 * <li><b>分支節點</b>：{@code op}（AND / OR / NOT）+
 * {@code children}，{@code field}/{@code operator}/ {@code value} 留 null</li>
 * <li><b>葉節點</b>：{@code field}、{@code operator}、{@code value}，{@code op}/{@code children}
 * 留 null</li>
 * </ul>
 *
 * <p>
 * 採平坦 POJO 設計，Jackson 序列化忽略 null 欄位（{@link JsonInclude#NON_NULL}）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConditionNode {

	/** 邏輯運算符（分支節點：AND / OR / NOT）。 */
	private String op;

	/** 子節點（分支節點使用）。 */
	private List<ConditionNode> children;

	/** 量測欄位名稱（葉節點使用，必須屬於 schema.telemetry.properties 白名單）。 */
	private String field;

	/** 比較運算子（葉節點使用）。 */
	private ConditionOperator operator;

	/**
	 * 比較目標值（葉節點使用；可為 Number / String / Boolean，以 JSON 原始型別儲存）。
	 */
	private Object value;

	public String getOp() {
		return op;
	}

	public void setOp(String op) {
		this.op = op;
	}

	public List<ConditionNode> getChildren() {
		return children;
	}

	public void setChildren(List<ConditionNode> children) {
		this.children = children;
	}

	public String getField() {
		return field;
	}

	public void setField(String field) {
		this.field = field;
	}

	public ConditionOperator getOperator() {
		return operator;
	}

	public void setOperator(ConditionOperator operator) {
		this.operator = operator;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	/** 判斷是否為葉節點（有 field + operator + value）。 */
	@JsonIgnore
	public boolean isLeaf() {
		return field != null && operator != null && value != null;
	}

}
