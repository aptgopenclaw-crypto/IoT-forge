package com.taipei.iot.eventrule.evaluation;

import java.util.List;
import java.util.Map;

import com.taipei.iot.eventrule.model.ConditionNode;
import com.taipei.iot.eventrule.model.ConditionOperator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConditionEvaluator} 單元測試：AND/OR/NOT 巢狀 + GT/LT/EQ 葉節點。
 */
class ConditionEvaluatorTest {

	private final ConditionEvaluator evaluator = new ConditionEvaluator();

	private static ConditionNode leaf(String field, ConditionOperator op, Object value) {
		ConditionNode n = new ConditionNode();
		n.setField(field);
		n.setOperator(op);
		n.setValue(value);
		return n;
	}

	private static ConditionNode branch(String op, ConditionNode... children) {
		ConditionNode n = new ConditionNode();
		n.setOp(op);
		n.setChildren(List.of(children));
		return n;
	}

	@Test
	void gt_numberAboveThreshold_returnsTrue() {
		Map<String, Object> values = Map.of("temperature", 80.0);
		assertThat(evaluator.evaluate(leaf("temperature", ConditionOperator.GT, 75), values)).isTrue();
	}

	@Test
	void gt_numberBelowThreshold_returnsFalse() {
		Map<String, Object> values = Map.of("temperature", 70.0);
		assertThat(evaluator.evaluate(leaf("temperature", ConditionOperator.GT, 75), values)).isFalse();
	}

	@Test
	void lt_numberBelowThreshold_returnsTrue() {
		Map<String, Object> values = Map.of("humidity", 30.0);
		assertThat(evaluator.evaluate(leaf("humidity", ConditionOperator.LT, 40), values)).isTrue();
	}

	@Test
	void eq_stringMatch_returnsTrue() {
		Map<String, Object> values = Map.of("status", "fault");
		assertThat(evaluator.evaluate(leaf("status", ConditionOperator.EQ, "fault"), values)).isTrue();
	}

	@Test
	void eq_stringMismatch_returnsFalse() {
		Map<String, Object> values = Map.of("status", "ok");
		assertThat(evaluator.evaluate(leaf("status", ConditionOperator.EQ, "fault"), values)).isFalse();
	}

	@Test
	void and_allChildrenTrue_returnsTrue() {
		Map<String, Object> values = Map.of("temperature", 80.0, "status", "on");
		ConditionNode node = branch("AND", leaf("temperature", ConditionOperator.GT, 75),
				leaf("status", ConditionOperator.EQ, "on"));
		assertThat(evaluator.evaluate(node, values)).isTrue();
	}

	@Test
	void and_oneChildFalse_returnsFalse() {
		Map<String, Object> values = Map.of("temperature", 60.0, "status", "on");
		ConditionNode node = branch("AND", leaf("temperature", ConditionOperator.GT, 75),
				leaf("status", ConditionOperator.EQ, "on"));
		assertThat(evaluator.evaluate(node, values)).isFalse();
	}

	@Test
	void or_oneChildTrue_returnsTrue() {
		Map<String, Object> values = Map.of("temperature", 80.0, "status", "ok");
		ConditionNode node = branch("OR", leaf("temperature", ConditionOperator.GT, 75),
				leaf("status", ConditionOperator.EQ, "fault"));
		assertThat(evaluator.evaluate(node, values)).isTrue();
	}

	@Test
	void not_invertsFalseToTrue() {
		Map<String, Object> values = Map.of("temperature", 60.0);
		ConditionNode node = branch("NOT", leaf("temperature", ConditionOperator.GT, 75));
		assertThat(evaluator.evaluate(node, values)).isTrue();
	}

	@Test
	void nestedAndOrNot_evaluatesCorrectly() {
		// (temp > 75 AND status == "fault") OR NOT (humidity < 40)
		Map<String, Object> values = Map.of("temperature", 80.0, "status", "fault", "humidity", 50.0);
		ConditionNode inner = branch("AND", leaf("temperature", ConditionOperator.GT, 75),
				leaf("status", ConditionOperator.EQ, "fault"));
		ConditionNode notHumid = branch("NOT", leaf("humidity", ConditionOperator.LT, 40));
		ConditionNode root = branch("OR", inner, notHumid);
		assertThat(evaluator.evaluate(root, values)).isTrue(); // inner=true → OR=true
	}

	@Test
	void missingField_returnsFalse() {
		Map<String, Object> values = Map.of("other", 100.0);
		assertThat(evaluator.evaluate(leaf("temperature", ConditionOperator.GT, 75), values)).isFalse();
	}

	@Test
	void nullNode_returnsFalse() {
		assertThat(evaluator.evaluate(null, Map.of())).isFalse();
	}

	@Test
	void unsupportedOperator_returnsFalse() {
		Map<String, Object> values = Map.of("temperature", 80.0);
		assertThat(evaluator.evaluate(leaf("temperature", ConditionOperator.BETWEEN, 75), values)).isFalse();
	}

}
