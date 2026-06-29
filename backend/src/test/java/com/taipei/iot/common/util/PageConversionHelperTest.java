package com.taipei.iot.common.util;

import com.taipei.iot.common.dto.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [common v2 F-10] {@link PageConversionHelper} 單元測試。
 */
class PageConversionHelperTest {

	@Nested
	@DisplayName("from(Page<T>)")
	class FromPage {

		@Test
		@DisplayName("回傳值應保留原 Page 的分頁中繼資料與內容")
		void shouldPreserveMetadataAndContent() {
			Page<String> page = new PageImpl<>(List.of("a", "b", "c"), PageRequest.of(1, 3), 10L);

			PageResponse<String> response = PageConversionHelper.from(page);

			assertThat(response.getContent()).containsExactly("a", "b", "c");
			assertThat(response.getTotalElements()).isEqualTo(10L);
			assertThat(response.getTotalPages()).isEqualTo(4);
			assertThat(response.getPage()).isEqualTo(1);
			assertThat(response.getSize()).isEqualTo(3);
		}

		@Test
		@DisplayName("空 Page 應回傳空 content + 0 totalElements")
		void shouldHandleEmptyPage() {
			Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);

			PageResponse<String> response = PageConversionHelper.from(page);

			assertThat(response.getContent()).isEmpty();
			assertThat(response.getTotalElements()).isZero();
			assertThat(response.getTotalPages()).isZero();
		}

		@Test
		@DisplayName("null Page 應回傳空 PageResponse")
		void shouldReturnEmptyWhenNull() {
			PageResponse<String> response = PageConversionHelper.from((Page<String>) null);

			assertThat(response.getContent()).isEmpty();
			assertThat(response.getTotalElements()).isZero();
			assertThat(response.getTotalPages()).isZero();
			assertThat(response.getPage()).isZero();
			assertThat(response.getSize()).isZero();
		}

	}

	@Nested
	@DisplayName("from(Page<E>, Function<E,D>)")
	class FromPageWithMapper {

		@Test
		@DisplayName("mapper 應作用於每一個 element")
		void shouldApplyMapper() {
			Page<String> page = new PageImpl<>(List.of("1", "22", "333"), PageRequest.of(0, 3), 3L);

			PageResponse<Integer> response = PageConversionHelper.from(page, String::length);

			assertThat(response.getContent()).containsExactly(1, 2, 3);
			assertThat(response.getTotalElements()).isEqualTo(3L);
			assertThat(response.getPage()).isZero();
			assertThat(response.getSize()).isEqualTo(3);
		}

		@Test
		@DisplayName("null mapper 應拋出 IllegalArgumentException")
		void shouldRejectNullMapper() {
			Page<String> page = new PageImpl<>(List.of("a"));

			assertThatThrownBy(() -> PageConversionHelper.from(page, null)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("mapper");
		}

		@Test
		@DisplayName("null Page 應回傳空 PageResponse 而不呼叫 mapper")
		void shouldReturnEmptyWhenNullPage() {
			PageResponse<Integer> response = PageConversionHelper.from(null, (String s) -> {
				throw new AssertionError("mapper should not be invoked");
			});

			assertThat(response.getContent()).isEmpty();
			assertThat(response.getTotalElements()).isZero();
		}

	}

	@Nested
	@DisplayName("from(List<D>, Page<?>)")
	class FromListWithPage {

		@Test
		@DisplayName("應使用傳入 content + Page 中繼資料")
		void shouldUseProvidedContentAndPageMetadata() {
			Page<Object> page = new PageImpl<>(List.of(new Object(), new Object()), PageRequest.of(2, 5), 25L);
			List<String> mapped = List.of("x", "y");

			PageResponse<String> response = PageConversionHelper.from(mapped, page);

			assertThat(response.getContent()).containsExactly("x", "y");
			assertThat(response.getTotalElements()).isEqualTo(25L);
			assertThat(response.getTotalPages()).isEqualTo(5);
			assertThat(response.getPage()).isEqualTo(2);
			assertThat(response.getSize()).isEqualTo(5);
		}

		@Test
		@DisplayName("null content 應轉換為空 list")
		void shouldTreatNullContentAsEmpty() {
			Page<Object> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);

			PageResponse<String> response = PageConversionHelper.from(null, page);

			assertThat(response.getContent()).isEmpty();
		}

		@Test
		@DisplayName("null page 應回傳 content 並使用 0 中繼資料")
		void shouldHandleNullPage() {
			PageResponse<String> response = PageConversionHelper.from(List.of("only"), null);

			assertThat(response.getContent()).containsExactly("only");
			assertThat(response.getTotalElements()).isZero();
			assertThat(response.getTotalPages()).isZero();
			assertThat(response.getPage()).isZero();
			assertThat(response.getSize()).isZero();
		}

	}

	@Test
	@DisplayName("empty() 應回傳空 PageResponse")
	void emptyShouldReturnZeros() {
		PageResponse<String> response = PageConversionHelper.empty();

		assertThat(response.getContent()).isEmpty();
		assertThat(response.getTotalElements()).isZero();
		assertThat(response.getTotalPages()).isZero();
		assertThat(response.getPage()).isZero();
		assertThat(response.getSize()).isZero();
	}

}
