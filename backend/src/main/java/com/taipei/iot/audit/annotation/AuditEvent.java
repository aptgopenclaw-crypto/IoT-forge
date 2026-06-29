package com.taipei.iot.audit.annotation;

import com.taipei.iot.audit.enums.AuditEventType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditEvent {

	/** 事件類型（編譯期強制從 enum 選取） */
	AuditEventType value();

}
