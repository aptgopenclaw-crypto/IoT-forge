package com.taipei.iot.common.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 分頁查詢參數 DTO — 配合 {@link com.taipei.iot.common.annotation.PaginationParams} 使用， 由
 * {@code PaginationArgumentResolver} 在請求進入 Controller 前注入。
 */
public final class PageQuery {

	private final int page;

	private final int size;

	public PageQuery(int page, int size) {
		this.page = page;
		this.size = size;
	}

	public int getPage() {
		return page;
	}

	public int getSize() {
		return size;
	}

	/**
	 * 轉換為 Spring Data {@link PageRequest}（unsorted）
	 */
	public PageRequest toPageRequest() {
		return PageRequest.of(page, size);
	}

	/**
	 * 轉換為帶排序的 {@link PageRequest}
	 */
	public PageRequest toPageRequest(Sort sort) {
		return PageRequest.of(page, size, sort);
	}

	@Override
	public String toString() {
		return "PageQuery{page=" + page + ", size=" + size + '}';
	}

}
