package com.taipei.iot.audit.entity;

import com.taipei.iot.common.util.SecurityContextUtils;
import org.hibernate.envers.RevisionListener;

public class AuditRevisionListener implements RevisionListener {

	@Override
	public void newRevision(Object revisionEntity) {
		AuditRevisionEntity rev = (AuditRevisionEntity) revisionEntity;
		rev.setActionUserId(SecurityContextUtils.getCurrentUserId());
	}

}
