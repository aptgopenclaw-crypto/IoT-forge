package com.taipei.iot.common.util;

import com.taipei.iot.common.dto.PageResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * [common v2 F-10] Spring Data {@link Page} 到 {@link PageResponse} 的統一轉換工具， 取代各 service /
 * controller 自寫的 {@code toPageResponse} / {@code buildPageResponse}
 * boilerplate（命名與實作各異，難以維護）。
 *
 * <p>
 * 三個重載涵蓋常見場景：
 * <ol>
 * <li>{@link #from(Page)} — 內容無需轉型（{@code Page<DTO> → PageResponse<DTO>}）</li>
 * <li>{@link #from(Page, Function)} — Entity → DTO 一條龍轉型（最常見）</li>
 * <li>{@link #from(List, Page)} — 內容已由呼叫端事先 map（例如需要外部資料做 enrich）</li>
 * </ol>
 *
 * <p>
 * 分頁中繼資料（totalElements / totalPages / page / size）一律取自 {@link Page} 自身， 避免「呼叫端誤傳的
 * page/size 與實際分頁不一致」的潛在 bug。
 */
public final class PageConversionHelper {

	private PageConversionHelper() {
		// utility class
	}

	/**
	 * 內容無需轉型 — {@code Page<T> → PageResponse<T>}。
	 */
	public static <T> PageResponse<T> from(Page<T> page) {
		if (page == null) {
			return empty();
		}
		return PageResponse.<T>builder()
			.content(page.getContent())
			.totalElements(page.getTotalElements())
			.totalPages(page.getTotalPages())
			.page(page.getNumber())
			.size(page.getSize())
			.build();
	}

	/**
	 * 將 {@code Page<E>} 透過 {@code mapper} 轉為 {@code PageResponse<D>}。
	 * @param page 來源 page；{@code null} 時回傳空 {@link PageResponse}
	 * @param mapper element 轉換函式
	 */
	public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
		if (page == null) {
			return empty();
		}
		if (mapper == null) {
			throw new IllegalArgumentException("mapper must not be null");
		}
		List<D> content = page.getContent().stream().map(mapper).toList();
		return PageResponse.<D>builder()
			.content(content)
			.totalElements(page.getTotalElements())
			.totalPages(page.getTotalPages())
			.page(page.getNumber())
			.size(page.getSize())
			.build();
	}

	/**
	 * 內容已由呼叫端 map 完成（常見於需要 enrich 跨表資料的場景，例如先抓 dept 名稱再組裝）。 分頁中繼資料一律取自 {@code page}
	 * 以避免不一致。
	 */
	public static <D> PageResponse<D> from(List<D> content, Page<?> page) {
		if (page == null) {
			return PageResponse.<D>builder()
				.content(content == null ? List.of() : content)
				.totalElements(0L)
				.totalPages(0)
				.page(0)
				.size(0)
				.build();
		}
		return PageResponse.<D>builder()
			.content(content == null ? List.of() : content)
			.totalElements(page.getTotalElements())
			.totalPages(page.getTotalPages())
			.page(page.getNumber())
			.size(page.getSize())
			.build();
	}

	/**
	 * 空 {@link PageResponse}（content=[], totals=0）。
	 */
	public static <T> PageResponse<T> empty() {
		return PageResponse.<T>builder().content(List.of()).totalElements(0L).totalPages(0).page(0).size(0).build();
	}

}
