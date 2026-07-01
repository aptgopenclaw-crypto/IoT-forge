package com.taipei.iot.eventrule.evaluation;

import java.util.List;
import java.util.Map;

import com.taipei.iot.eventrule.model.ConditionNode;
import com.taipei.iot.eventrule.model.ConditionOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 結構化條件樹求值器（無外部依賴、純邏輯、可單元測試）。
 *
 * <p>
 * 支援任意巢狀 AND / OR / NOT + v1 運算子 GT / LT / EQ。 運算子白名單：未知 / 尚未實作的運算子一律回傳 {@code false}，並記錄
 * warn。 這確保「只有明確實作的比對才能觸發規則」，避免擴充時意外放行。
 */
@Slf4j
@Component
public class ConditionEvaluator {

	/**
	 * 評估條件樹。
	 * @param node 條件樹根節點
	 * @param values 遙測量測值
	 * @return 條件是否成立
	 */
	public boolean evaluate(ConditionNode node, Map<String, Object> values) {
		if (node == null) {
			return false;
		}
		if (node.isLeaf()) {
			return evaluateLeaf(node, values);
		}
		return evaluateBranch(node, values);
	}

	private boolean evaluateBranch(ConditionNode node, Map<String, Object> values) {
		String op = node.getOp();
		List<ConditionNode> children = node.getChildren();
		if (op == null || children == null || children.isEmpty()) {
			log.warn("[ConditionEvaluator] branch node missing op or children");
			return false;
		}
		return switch (op.toUpperCase()) {
			case "AND" -> children.stream().allMatch(child -> evaluate(child, values));
			case "OR" -> children.stream().anyMatch(child -> evaluate(child, values));
			case "NOT" -> {
				if (children.size() != 1) {
					log.warn("[ConditionEvaluator] NOT node must have exactly 1 child, got {}", children.size());
					yield false;
				}
				yield !evaluate(children.get(0), values);
			}
			default -> {
				log.warn("[ConditionEvaluator] unknown logical op: {}", op);
				yield false;
			}
		};
	}

	private boolean evaluateLeaf(ConditionNode node, Map<String, Object> values) {
		String field = node.getField();
		ConditionOperator operator = node.getOperator();
		Object expected = node.getValue();
		Object actual = values == null ? null : values.get(field);

		if (actual == null) {
			return false;
		}

		return switch (operator) {
			case GT -> compareNumeric(actual, expected) > 0;
			case LT -> compareNumeric(actual, expected) < 0;
			case GTE -> compareNumeric(actual, expected) >= 0;
			case LTE -> compareNumeric(actual, expected) <= 0;
			case EQ -> compareEqual(actual, expected);
			case NEQ -> !compareEqual(actual, expected);
			default -> {
				log.warn("[ConditionEvaluator] operator {} not yet implemented (v1 supports GT/LT/EQ)", operator);
				yield false;
			}
		};
	}

	/**
	 * 兩個值轉為 double 後比較。非數值型別回傳 0（相等），並記錄 warn。
	 */
	private int compareNumeric(Object actual, Object expected) {
		try {
			double a = toDouble(actual);
			double e = toDouble(expected);
			return Double.compare(a, e);
		}
		catch (NumberFormatException ex) {
			log.warn("[ConditionEvaluator] numeric comparison failed: actual={} expected={}", actual, expected);
			return 0;
		}
	}

	private boolean compareEqual(Object actual, Object expected) {
		if (actual instanceof Number an && expected instanceof Number en) {
			return Double.compare(an.doubleValue(), en.doubleValue()) == 0;
		}
		return String.valueOf(actual).equals(String.valueOf(expected));
	}

	private double toDouble(Object v) {
		if (v instanceof Number n) {
			return n.doubleValue();
		}
		return Double.parseDouble(String.valueOf(v));
	}

}
