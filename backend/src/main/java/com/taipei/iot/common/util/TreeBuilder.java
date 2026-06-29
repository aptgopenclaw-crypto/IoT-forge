package com.taipei.iot.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用樹狀結構建構器。輸入扁平 list + 「id / parentId / 子節點 setter」三個 lambda， 輸出根節點 list（每個根節點底下已遞迴掛好
 * children）。
 *
 * <p>
 * 適用場景：部門樹、選單樹、角色階層、組織圖等任何 self-referencing 父子關係。
 *
 * <h2>使用範例</h2> <pre>{@code
 *   List<DeptDto> roots = TreeBuilder.build(
 *       deptDtos,
 *       DeptDto::getId,
 *       DeptDto::getPid,
 *       DeptDto::setChildren
 *   );
 * }</pre>
 *
 * <h2>行為</h2>
 * <ul>
 * <li>parentId 為 {@code null} 視為根節點。</li>
 * <li>parentId 指向不存在於 list 中的節點，亦視為根節點（孤兒救援，避免資料殘缺造成消失）。</li>
 * <li>{@code null} item 會被略過。</li>
 * <li>不偵測循環依賴（呼叫端應確保 DB constraint 已避免）。</li>
 * <li>不修改輸入 list 順序；輸出根節點順序與輸入順序一致。</li>
 * </ul>
 *
 * <p>
 * [common v2 F-14]
 */
public final class TreeBuilder {

	private TreeBuilder() {
	}

	/**
	 * 將扁平 list 組成樹。
	 * @param items 扁平節點清單
	 * @param idExtractor 取得節點 id 的函式
	 * @param parentIdExtractor 取得節點 parentId 的函式（root 應回 {@code null}）
	 * @param childrenSetter 將 children list 寫入節點的 BiConsumer
	 * @param <T> 節點型別
	 * @param <K> id 型別
	 * @return 根節點 list；items 為 null/empty 時回傳 empty list
	 */
	public static <T, K> List<T> build(Collection<T> items, Function<T, K> idExtractor,
			Function<T, K> parentIdExtractor, BiConsumer<T, List<T>> childrenSetter) {

		Objects.requireNonNull(idExtractor, "idExtractor must not be null");
		Objects.requireNonNull(parentIdExtractor, "parentIdExtractor must not be null");
		Objects.requireNonNull(childrenSetter, "childrenSetter must not be null");

		if (items == null || items.isEmpty()) {
			return new ArrayList<>();
		}

		// 預先過濾 null 與保留輸入順序
		List<T> nonNull = items.stream().filter(Objects::nonNull).collect(Collectors.toList());

		Set<K> idSet = nonNull.stream().map(idExtractor).filter(Objects::nonNull).collect(Collectors.toSet());

		Map<K, List<T>> childrenMap = new HashMap<>();
		for (T item : nonNull) {
			K parentId = parentIdExtractor.apply(item);
			if (parentId != null && idSet.contains(parentId)) {
				childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(item);
			}
		}

		List<T> roots = new ArrayList<>();
		for (T item : nonNull) {
			K parentId = parentIdExtractor.apply(item);
			if (parentId == null || !idSet.contains(parentId)) {
				attachChildren(item, idExtractor, childrenMap, childrenSetter);
				roots.add(item);
			}
		}
		return roots;
	}

	private static <T, K> void attachChildren(T node, Function<T, K> idExtractor, Map<K, List<T>> childrenMap,
			BiConsumer<T, List<T>> childrenSetter) {

		K id = idExtractor.apply(node);
		List<T> children = id == null ? null : childrenMap.get(id);
		if (children != null && !children.isEmpty()) {
			for (T child : children) {
				attachChildren(child, idExtractor, childrenMap, childrenSetter);
			}
			childrenSetter.accept(node, children);
		}
	}

}
