package com.taipei.iot.common.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("TreeBuilder [common v2 F-14]")
class TreeBuilderTest {

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class Node {

		Long id;

		Long parentId;

		String name;

		List<Node> children;

	}

	private static List<Node> build(List<Node> items) {
		return TreeBuilder.build(items, Node::getId, Node::getParentId, Node::setChildren);
	}

	@Test
	@DisplayName("null collection 回傳 empty list")
	void nullCollectionReturnsEmpty() {
		assertThat(build(null)).isEmpty();
	}

	@Test
	@DisplayName("empty collection 回傳 empty list")
	void emptyCollectionReturnsEmpty() {
		assertThat(build(Collections.emptyList())).isEmpty();
	}

	@Test
	@DisplayName("單一 root，無子節點")
	void singleRootNoChildren() {
		Node root = new Node(1L, null, "root", null);
		List<Node> result = build(List.of(root));

		assertThat(result).containsExactly(root);
		assertThat(root.getChildren()).isNull();
	}

	@Test
	@DisplayName("單一 root + 多層子節點")
	void multiLevelTree() {
		Node root = new Node(1L, null, "root", null);
		Node child1 = new Node(2L, 1L, "c1", null);
		Node child2 = new Node(3L, 1L, "c2", null);
		Node grandchild = new Node(4L, 2L, "g1", null);

		List<Node> result = build(List.of(root, child1, child2, grandchild));

		assertThat(result).containsExactly(root);
		assertThat(root.getChildren()).containsExactly(child1, child2);
		assertThat(child1.getChildren()).containsExactly(grandchild);
		assertThat(child2.getChildren()).isNull();
	}

	@Test
	@DisplayName("多個 root")
	void multipleRoots() {
		Node r1 = new Node(1L, null, "r1", null);
		Node r2 = new Node(2L, null, "r2", null);
		Node c1 = new Node(3L, 1L, "c1", null);

		List<Node> result = build(List.of(r1, r2, c1));

		assertThat(result).containsExactly(r1, r2);
		assertThat(r1.getChildren()).containsExactly(c1);
	}

	@Test
	@DisplayName("孤兒（parentId 不存在於 set）救援為 root")
	void orphanBecomesRoot() {
		Node root = new Node(1L, null, "root", null);
		Node orphan = new Node(99L, 999L, "orphan", null);

		List<Node> result = build(List.of(root, orphan));

		assertThat(result).containsExactly(root, orphan);
		assertThat(orphan.getChildren()).isNull();
	}

	@Test
	@DisplayName("null item 會被略過")
	void nullItemSkipped() {
		Node root = new Node(1L, null, "root", null);
		List<Node> input = new java.util.ArrayList<>();
		input.add(root);
		input.add(null);

		List<Node> result = build(input);

		assertThat(result).containsExactly(root);
	}

	@Test
	@DisplayName("輸入順序保留")
	void preservesInputOrder() {
		Node r2 = new Node(2L, null, "r2", null);
		Node r1 = new Node(1L, null, "r1", null);

		List<Node> result = build(List.of(r2, r1));

		assertThat(result).containsExactly(r2, r1);
	}

	@Test
	@DisplayName("idExtractor 為 null 拋 NPE")
	void nullIdExtractorThrows() {
		assertThatNullPointerException().isThrownBy(() -> TreeBuilder.build(List.of(new Node(1L, null, "n", null)),
				null, Node::getParentId, Node::setChildren));
	}

	@Test
	@DisplayName("parentIdExtractor 為 null 拋 NPE")
	void nullParentIdExtractorThrows() {
		assertThatNullPointerException().isThrownBy(
				() -> TreeBuilder.build(List.of(new Node(1L, null, "n", null)), Node::getId, null, Node::setChildren));
	}

	@Test
	@DisplayName("childrenSetter 為 null 拋 NPE")
	void nullChildrenSetterThrows() {
		assertThatNullPointerException().isThrownBy(
				() -> TreeBuilder.build(List.of(new Node(1L, null, "n", null)), Node::getId, Node::getParentId, null));
	}

}
