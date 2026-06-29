package com.taipei.iot.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * 帶有「使用者稽核欄位」的實體基礎類別，繼承自 {@link BaseEntity}， 在時間戳之外再加上：
 * <ul>
 * <li>{@code createdBy} — 建立者 ID（由 Spring Data JPA {@link CreatedBy} 自動填入， 來源為
 * {@code com.taipei.iot.config.JpaAuditConfig#auditorAware()}）</li>
 * <li>{@code updatedBy} — 最後更新者 ID（由 {@link LastModifiedBy} 自動填入）</li>
 * <li>{@code createdByName} — 建立者顯示名稱（手動寫入，因姓名快取需顯式策略， 不適合自動填入；建議在 service 層 create
 * 時設定一次）</li>
 * </ul>
 *
 * <h2>使用場景</h2>
 * <p>
 * 適用於「需要追蹤是誰建立 / 修改」的業務實體（如公告、附件、設定變更紀錄）。 純查詢用 / 純設定字典類實體應直接繼承 {@link BaseEntity} 即可。
 *
 * <h2>對應資料表欄位</h2> <pre>
 *   created_by       VARCHAR(50)   -- 建立者 user id
 *   updated_by       VARCHAR(50)   -- 最後更新者 user id
 *   created_by_name  VARCHAR(100)  -- 建立者顯示名稱（snapshot）
 * </pre>
 *
 * <h2>遷移指南</h2>
 * <p>
 * 既有實體若想改繼承本類，需確認：
 * <ol>
 * <li>DB 表已有對應 3 個欄位（必要時透過 Flyway migration 補上）</li>
 * <li>原本手寫的 {@code createdBy / createdAt / updatedAt} 欄位移除</li>
 * <li>若實體使用 Lombok {@code @Builder}，改用 {@code @SuperBuilder} 以支援父類欄位</li>
 * <li>實體類別仍需保留 {@code @EntityListeners(AuditingEntityListener.class)} 才能讓
 * {@code @CreatedBy} / {@code @LastModifiedBy} 生效（已由 {@link BaseEntity} 宣告）</li>
 * </ol>
 *
 * <h2>[common v2 F-12]</h2>
 * <p>
 * 本類別為 common v2 review 提出之跨模組重複樣式上提候選項目。
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditedEntity extends BaseEntity {

	@CreatedBy
	@Column(name = "created_by", length = 50, updatable = false)
	private String createdBy;

	@LastModifiedBy
	@Column(name = "updated_by", length = 50)
	private String updatedBy;

	/**
	 * 建立者顯示名稱 snapshot。
	 * <p>
	 * 不使用 {@code @CreatedBy} 自動填入，因 auditor 預設只提供 ID； 顯示名稱請於 service 層 create 時透過
	 * {@code UserInfo} 取得後寫入。
	 */
	@Column(name = "created_by_name", length = 100)
	private String createdByName;

}
