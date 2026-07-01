package com.taipei.iot.eventrule.model;

/**
 * 規則觸發語意。
 *
 * <ul>
 * <li>{@link #ON_MATCH} — 每筆遙測滿足條件即觸發（受 cooldown 限制）</li>
 * <li>{@link #FOR_DURATION} — 條件持續滿足 N 秒才觸發（去除瞬時抖動）</li>
 * <li>{@link #ON_CHANGE} — 條件結果由 false→true 時邊緣觸發</li>
 * </ul>
 */
public enum TriggerMode {

	ON_MATCH, FOR_DURATION, ON_CHANGE

}
