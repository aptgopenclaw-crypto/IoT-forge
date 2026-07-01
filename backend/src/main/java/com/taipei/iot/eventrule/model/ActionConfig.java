package com.taipei.iot.eventrule.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 觸發動作設定（對應 {@code event_rule.actions} JSONB 陣列中的每個元素）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionConfig {

	private ActionType type;

	/** 通知通道（NOTIFY 動作）：IN_APP / EMAIL 等。 */
	private List<String> channels;

	/** 收件人指定。 */
	private RecipientConfig recipients;

	/** 通知模板代碼（由 notification 模組解析）。 */
	private String template;

	public ActionType getType() {
		return type;
	}

	public void setType(ActionType type) {
		this.type = type;
	}

	public List<String> getChannels() {
		return channels;
	}

	public void setChannels(List<String> channels) {
		this.channels = channels;
	}

	public RecipientConfig getRecipients() {
		return recipients;
	}

	public void setRecipients(RecipientConfig recipients) {
		this.recipients = recipients;
	}

	public String getTemplate() {
		return template;
	}

	public void setTemplate(String template) {
		this.template = template;
	}

	/**
	 * 收件人指定（角色代碼 / 使用者 ID 直指）。
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class RecipientConfig {

		/** 角色代碼（例如 OPERATOR → roleId = ROLE_OPERATOR）；符合此角色的所有使用者均收通知。 */
		private List<String> roleCodes;

		/** 直接指定使用者 ID。 */
		private List<String> userIds;

		public List<String> getRoleCodes() {
			return roleCodes;
		}

		public void setRoleCodes(List<String> roleCodes) {
			this.roleCodes = roleCodes;
		}

		public List<String> getUserIds() {
			return userIds;
		}

		public void setUserIds(List<String> userIds) {
			this.userIds = userIds;
		}

	}

}
