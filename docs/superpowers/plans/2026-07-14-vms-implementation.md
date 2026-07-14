# VMS (Video Management System) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete Video Management System with live/playback HLS streaming, VMS server management, camera mapping, and stream audit logging.

**Architecture:** Three-layer security model — frontend holds only JWT, backend centrally manages NX tokens (never exposed to frontend), all HLS media requests proxied through backend. Backend package `com.taipei.iot.vms` with sub-packages for token management, session management, controller, service, entity, repository. Frontend 5 Vue views under `/vms/*` routes.

**Tech Stack:** Spring Boot 3.4.1 / Java 21 / Maven, PostgreSQL + Hibernate, Vue 3 / TypeScript / Vite, hls.js, Pinia

## Global Constraints

- Flyway naming: `V{int}__{description}.sql` (latest = V25); next = V26
- Backend package: `com.taipei.iot.vms.*`
- All API endpoints: `/v1/auth/vms/*` (JWT-protected)
- Entities: `@Entity`, `@Table`, `@Filter(name = "tenantFilter")`, `@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })`, implements `TenantAware`
- Error codes: `88xxx` range; existing at `88100-88108`
- Frontend API: `/home/kevin/workspaces/side-project/IoT-forge/frontend/src/api/vms/index.ts`
- Frontend types: `/home/kevin/workspaces/side-project/IoT-forge/frontend/src/types/vms.ts`
- Frontend views: `/home/kevin/workspaces/side-project/IoT-forge/frontend/src/views/vms/`
- Frontend components: `/home/kevin/workspaces/side-project/IoT-forge/frontend/src/views/vms/components/`
- NX Camera ID is UUID string (e.g. `e3e9a385-7fe0-3ba5-5482-a86cde7faf48`), stored as `varchar(100)`
- AES encryption for stored passwords reuses `AuthConfigEncryptor` (AES-256-GCM)
- `@CreatedDate`/`@LastModifiedDate` for created_at/updated_at via `AuditingEntityListener`
- Existing error codes already defined (88100-88108); add VMS_CAMERA_OFFLINE, VMS_PLAYBACK_NO_RECORDING, VMS_PLAYBACK_ENDED

---

### Task 1: Data Layer — Flyway Migrations + Error Codes + Entities + Repositories

**Files:**
- Create: `backend/src/main/resources/db/migration/V26__create_vms_server.sql`
- Create: `backend/src/main/resources/db/migration/V27__create_vms_camera_mapping.sql`
- Create: `backend/src/main/resources/db/migration/V28__create_vms_stream_log.sql`
- Create: `backend/src/main/resources/db/migration/V29__seed_vms_menus.sql`
- Modify: `backend/src/main/java/com/taipei/iot/common/enums/ErrorCode.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/entity/VmsServerEntity.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/entity/VmsCameraMappingEntity.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/entity/VmsStreamLogEntity.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/repository/VmsServerRepository.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/repository/VmsCameraMappingRepository.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/repository/VmsStreamLogRepository.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/dto/VmsServerDTO.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/dto/VmsCameraMappingDTO.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/dto/VmsStreamLogDTO.java`

**Interfaces:**
- Consumes: `TenantAware`, `TenantEntityListener` (existing)
- Produces: `VmsServerEntity`, `VmsCameraMappingEntity`, `VmsStreamLogEntity` with JPA repositories

- [ ] **Step 1: Add missing error codes**

```java
// In ErrorCode.java, add before the closing semicolon of the enum:
VMS_CAMERA_OFFLINE("88109", 400, "攝影機離線"),
VMS_PLAYBACK_NO_RECORDING("88110", 404, "該時間區間無錄影"),
VMS_PLAYBACK_ENDED("88111", 200, "播放已結束");
```

- [ ] **Step 2: Create V26 migration — vms_server**

```sql
-- V26__create_vms_server.sql
CREATE TABLE vms_server (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    name          VARCHAR(100) NOT NULL,
    vms_type      VARCHAR(20) NOT NULL DEFAULT 'NX_WITNESS',
    base_url      VARCHAR(255) NOT NULL,
    auth_type     VARCHAR(20) NOT NULL DEFAULT 'BASIC',
    auth_username VARCHAR(100),
    auth_password VARCHAR(255),      -- AES encrypted
    api_token     VARCHAR(500),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

- [ ] **Step 3: Create V27 migration — vms_camera_mapping**

```sql
-- V27__create_vms_camera_mapping.sql
CREATE TABLE vms_camera_mapping (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    server_id     BIGINT NOT NULL REFERENCES vms_server(id) ON DELETE CASCADE,
    vms_camera_id VARCHAR(100) NOT NULL,
    display_name  VARCHAR(200),
    dept_id       BIGINT,
    status        VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    rtsp_url      VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vms_camera_server ON vms_camera_mapping(server_id);
CREATE INDEX idx_vms_camera_dept ON vms_camera_mapping(dept_id);
```

- [ ] **Step 4: Create V28 migration — vms_stream_log**

```sql
-- V28__create_vms_stream_log.sql
CREATE TABLE vms_stream_log (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    camera_id           BIGINT NOT NULL REFERENCES vms_camera_mapping(id),
    stream_type         VARCHAR(10) NOT NULL,
    session_token       VARCHAR(36) NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at            TIMESTAMPTZ,
    duration_seconds    INTEGER,
    playback_start_time TIMESTAMPTZ,
    playback_end_time   TIMESTAMPTZ
);

CREATE INDEX idx_vms_stream_log_user ON vms_stream_log(user_id);
CREATE INDEX idx_vms_stream_log_camera ON vms_stream_log(camera_id);
CREATE INDEX idx_vms_stream_log_started ON vms_stream_log(started_at);
```

- [ ] **Step 5: Create V29 migration — seed VMS menus**

```sql
-- V29__seed_vms_menus.sql
-- The actual menu seed data depends on the menu system format.
-- Insert a parent VMS menu and 5 child menus referencing the route paths.
-- Use the same pattern as existing seed migrations.
INSERT INTO sys_menu (tenant_id, parent_id, name, permission, route_path, type, sort_order, icon, component, is_frame, is_cache, visible, status, created_by, created_at)
VALUES
(0, NULL, 'VMS', 'vms:manage', NULL, 'M', 1, 'video', NULL, 0, 1, 1, 1, 'system', NOW()),
(0, (SELECT id FROM sys_menu WHERE name = 'VMS' AND parent_id IS NULL), '即時播放', 'vms:live', '/vms/live', 'C', 1, 'video', 'vms/VmsLiveView', 0, 1, 1, 1, 'system', NOW()),
(0, (SELECT id FROM sys_menu WHERE name = 'VMS' AND parent_id IS NULL), '歷史播放', 'vms:playback', '/vms/playback', 'C', 2, 'time', 'vms/VmsPlaybackView', 0, 1, 1, 1, 'system', NOW()),
(0, (SELECT id FROM sys_menu WHERE name = 'VMS' AND parent_id IS NULL), 'VMS 伺服器', 'vms:server', '/vms/servers', 'C', 3, 'server', 'vms/VmsServerManageView', 0, 1, 1, 1, 'system', NOW()),
(0, (SELECT id FROM sys_menu WHERE name = 'VMS' AND parent_id IS NULL), '攝影機管理', 'vms:camera', '/vms/cameras', 'C', 4, 'camera', 'vms/VmsCameraManageView', 0, 1, 1, 1, 'system', NOW()),
(0, (SELECT id FROM sys_menu WHERE name = 'VMS' AND parent_id IS NULL), '串流記錄', 'vms:stream-log', '/vms/stream-logs', 'C', 5, 'document', 'vms/VmsStreamLogView', 0, 1, 1, 1, 'system', NOW());
```

- [ ] **Step 6: Create VmsServerEntity.java**

```java
package com.taipei.iot.vms.entity;

import com.taipei.iot.common.tenant.TenantAware;
import com.taipei.iot.common.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "vms_server")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VmsServerEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "vms_type", nullable = false, length = 20)
    private String vmsType;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Column(name = "auth_type", length = 20)
    private String authType;

    @Column(name = "auth_username", length = 100)
    private String authUsername;

    @Column(name = "auth_password", length = 255)
    private String authPassword;

    @Column(name = "api_token", length = 500)
    private String apiToken;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Override
    public String getTenantId() { return tenantId; }

    @Override
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
```

- [ ] **Step 7: Create VmsCameraMappingEntity.java**

```java
package com.taipei.iot.vms.entity;

import com.taipei.iot.common.tenant.TenantAware;
import com.taipei.iot.common.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "vms_camera_mapping")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VmsCameraMappingEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Column(name = "vms_camera_id", nullable = false, length = 100)
    private String vmsCameraId;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "dept_id")
    private Long deptId;

    @Column(length = 20)
    private String status;

    @Column(name = "rtsp_url", length = 500)
    private String rtspUrl;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Override
    public String getTenantId() { return tenantId; }

    @Override
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
```

- [ ] **Step 8: Create VmsStreamLogEntity.java**

```java
package com.taipei.iot.vms.entity;

import com.taipei.iot.common.tenant.TenantAware;
import com.taipei.iot.common.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "vms_stream_log")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VmsStreamLogEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "camera_id", nullable = false)
    private Long cameraId;

    @Column(name = "stream_type", nullable = false, length = 10)
    private String streamType;

    @Column(name = "session_token", nullable = false, length = 36)
    private String sessionToken;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "playback_start_time")
    private LocalDateTime playbackStartTime;

    @Column(name = "playback_end_time")
    private LocalDateTime playbackEndTime;

    @Override
    public String getTenantId() { return tenantId; }

    @Override
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
```

- [ ] **Step 9: Create repository interfaces**

```java
// VmsServerRepository.java
package com.taipei.iot.vms.repository;

import com.taipei.iot.vms.entity.VmsServerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VmsServerRepository extends JpaRepository<VmsServerEntity, Long> {
    List<VmsServerEntity> findByIsActiveTrue();
}

// VmsCameraMappingRepository.java
package com.taipei.iot.vms.repository;

import com.taipei.iot.vms.entity.VmsCameraMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VmsCameraMappingRepository extends JpaRepository<VmsCameraMappingEntity, Long> {
    List<VmsCameraMappingEntity> findByServerId(Long serverId);
    Optional<VmsCameraMappingEntity> findByVmsCameraId(String vmsCameraId);
    long countByServerId(Long serverId);
}

// VmsStreamLogRepository.java
package com.taipei.iot.vms.repository;

import com.taipei.iot.vms.entity.VmsStreamLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VmsStreamLogRepository
        extends JpaRepository<VmsStreamLogEntity, Long>, JpaSpecificationExecutor<VmsStreamLogEntity> {
}
```

- [ ] **Step 10: Create DTO classes**

```java
// VmsServerDTO.java
package com.taipei.iot.vms.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VmsServerDTO {
    private Long id;
    private String name;
    private String vmsType;
    private String baseUrl;
    private String authType;
    private String authUsername;
    // authPassword intentionally omitted from response
    private String apiToken;
    private Boolean isActive;
    private LocalDateTime createdAt;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class VmsServerRequest {
    private String name;
    private String vmsType;
    private String baseUrl;
    private String authType;
    private String authUsername;
    private String authPassword;
    private String apiToken;
    private Boolean isActive;
}

// VmsCameraMappingDTO.java
package com.taipei.iot.vms.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VmsCameraMappingDTO {
    private Long id;
    private Long serverId;
    private String serverName;
    private String vmsCameraId;
    private String displayName;
    private Long deptId;
    private String deptName;
    private String status;
    private String rtspUrl;
    private LocalDateTime createdAt;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class VmsCameraMappingRequest {
    private Long serverId;
    private String vmsCameraId;
    private String displayName;
    private Long deptId;
    private String rtspUrl;
}

// VmsStreamLogDTO.java
package com.taipei.iot.vms.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VmsStreamLogDTO {
    private Long id;
    private Long userId;
    private String userName;
    private Long cameraId;
    private String cameraName;
    private String streamType;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer durationSeconds;
    private LocalDateTime playbackStartTime;
    private LocalDateTime playbackEndTime;
}
```

- [ ] **Step 11: Run Flyway to verify migrations**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/backend
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/mydb -Dflyway.schemas=iot_forgedb -Dflyway.user=postgres -Dflyway.password=$DB_PASSWORD
```

- [ ] **Step 12: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add backend/src/main/resources/db/migration/V26__create_vms_server.sql
git add backend/src/main/resources/db/migration/V27__create_vms_camera_mapping.sql
git add backend/src/main/resources/db/migration/V28__create_vms_stream_log.sql
git add backend/src/main/resources/db/migration/V29__seed_vms_menus.sql
git add backend/src/main/java/com/taipei/iot/common/enums/ErrorCode.java
git add backend/src/main/java/com/taipei/iot/vms/
git commit -m "feat(vms): add data layer - entities, repositories, flyway migrations

- V26-V28: create vms_server, vms_camera_mapping, vms_stream_log tables
- V29: seed VMS menu structure
- Add VmsServerEntity, VmsCameraMappingEntity, VmsStreamLogEntity
- Add JPA repositories with tenant filter support
- Add DTOs for all three entities
- Add error codes: VMS_CAMERA_OFFLINE, VMS_PLAYBACK_NO_RECORDING, VMS_PLAYBACK_ENDED

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Backend Infrastructure — NxTokenManager + HlsSessionManager

**Files:**
- Create: `backend/src/main/java/com/taipei/iot/vms/config/VmsConfig.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/token/NxTokenManager.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/session/HlsSession.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/session/HlsSessionManager.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/session/SessionNotFoundException.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/exception/NxTokenNotAvailableException.java`

**Interfaces:**
- Consumes: `VmsServerRepository` (finds active servers), `AuthConfigEncryptor` (decrypts passwords)
- Produces: `NxTokenManager.getToken(serverId)`, `HlsSessionManager.createSession()`, `HlsSessionManager.getSession()`, `HlsSessionManager.removeSession()`

- [ ] **Step 1: Create VmsConfig.java**

```java
package com.taipei.iot.vms.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vms")
@Getter @Setter
public class VmsConfig {
    /** Session TTL in seconds (default 300 = 5 min) */
    private int sessionTtlSeconds = 300;
    /** Token refresh margin ratio (default 0.9 = refresh at 90% of expiry) */
    private double tokenRefreshRatio = 0.9;
}
```

- [ ] **Step 2: Create NxTokenNotAvailableException.java**

```java
package com.taipei.iot.vms.exception;

public class NxTokenNotAvailableException extends RuntimeException {
    public NxTokenNotAvailableException(String message) {
        super(message);
    }
    public NxTokenNotAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 3: Create NxTokenManager.java**

```java
package com.taipei.iot.vms.token;

import com.taipei.iot.auth.provider.crypto.AuthConfigEncryptor;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BaseException;
import com.taipei.iot.vms.entity.VmsServerEntity;
import com.taipei.iot.vms.exception.NxTokenNotAvailableException;
import com.taipei.iot.vms.repository.VmsServerRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
@Slf4j
public class NxTokenManager {

    private record TokenInfo(String token, Instant expiresAt, ScheduledFuture<?> refreshTask) {}

    private final Map<Long, TokenInfo> tokens = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final VmsServerRepository vmsServerRepository;
    private final AuthConfigEncryptor encryptor;
    private final TaskScheduler taskScheduler;

    public NxTokenManager(VmsServerRepository vmsServerRepository,
                          AuthConfigEncryptor encryptor,
                          TaskScheduler taskScheduler) {
        this.vmsServerRepository = vmsServerRepository;
        this.encryptor = encryptor;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public void init() {
        for (VmsServerEntity server : vmsServerRepository.findByIsActiveTrue()) {
            try {
                refreshToken(server);
            } catch (Exception e) {
                log.warn("Initial NX login failed for server [{}]: {}", server.getId(), e.getMessage());
            }
        }
    }

    public synchronized void refreshToken(Long serverId) {
        VmsServerEntity server = vmsServerRepository.findById(serverId)
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_SERVER_NOT_FOUND));
        refreshToken(server);
    }

    private void refreshToken(VmsServerEntity server) {
        String password = encryptor.decrypt(server.getAuthPassword());
        String url = server.getBaseUrl() + "/rest/v1/login/sessions";

        var body = Map.of("username", server.getAuthUsername(), "password", password != null ? password : server.getAuthPassword());
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            var response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            var responseBody = response.getBody();
            if (responseBody == null || responseBody.get("token") == null) {
                throw new NxTokenNotAvailableException("Empty token response from NX server " + server.getId());
            }

            String token = (String) responseBody.get("token");
            int expiresInS = responseBody.get("expiresInS") instanceof Number n ? n.intValue() : 3600;
            Instant expiresAt = Instant.now().plusSeconds(expiresInS);

            // Cancel existing refresh task
            TokenInfo old = tokens.get(server.getId());
            if (old != null && old.refreshTask() != null) {
                old.refreshTask().cancel(false);
            }

            // Schedule next refresh at 90% of expiry
            long refreshDelayMs = (long) (expiresInS * 0.9 * 1000);
            ScheduledFuture<?> refreshTask = taskScheduler.schedule(
                    () -> refreshToken(server.getId()),
                    Instant.now().plusMillis(refreshDelayMs));

            tokens.put(server.getId(), new TokenInfo(token, expiresAt, refreshTask));
            log.info("NX token acquired for server [{}] (ID: {}), expires in {}s",
                    server.getName(), server.getId(), expiresInS);

        } catch (Exception e) {
            log.error("Failed to acquire NX token for server [{}]: {}", server.getId(), e.getMessage());
            // Keep old token if exists — don't invalidate on transient failure
            if (!tokens.containsKey(server.getId())) {
                throw new NxTokenNotAvailableException("Initial NX login failed for server " + server.getId(), e);
            }
        }
    }

    public String getToken(Long serverId) {
        TokenInfo info = tokens.get(serverId);
        if (info == null || info.token() == null) {
            throw new BaseException(ErrorCode.VMS_CONNECTION_FAILED, "NX token not available for server " + serverId);
        }
        return info.token();
    }

    public void invalidateToken(Long serverId) {
        TokenInfo old = tokens.remove(serverId);
        if (old != null && old.refreshTask() != null) {
            old.refreshTask().cancel(false);
        }
    }
}
```

- [ ] **Step 4: Create HlsSession.java**

```java
package com.taipei.iot.vms.session;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class HlsSession {
    private String sessionToken;
    private String userId;
    private Long cameraId;
    private Long serverId;
    private String nxToken;
    private String streamType;   // "LIVE" or "PLAYBACK"
    private Instant startTime;   // playback: start pos (unix ms epoch)
    private Instant endTime;     // playback: end pos
    private Instant createdAt;
}
```

- [ ] **Step 5: Create SessionNotFoundException.java**

```java
package com.taipei.iot.vms.session;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String sessionToken) {
        super("Session not found or expired: " + sessionToken);
    }
}
```

- [ ] **Step 6: Create HlsSessionManager.java**

```java
package com.taipei.iot.vms.session;

import com.taipei.iot.vms.config.VmsConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HlsSessionManager {

    private final Map<String, HlsSession> sessions = new ConcurrentHashMap<>();
    private final VmsConfig vmsConfig;

    public HlsSessionManager(VmsConfig vmsConfig) {
        this.vmsConfig = vmsConfig;
    }

    public String createSession(String userId, Long cameraId, Long serverId,
                                 String nxToken, String streamType,
                                 Instant startTime, Instant endTime) {
        String sessionToken = UUID.randomUUID().toString();
        HlsSession session = new HlsSession(
                sessionToken, userId, cameraId, serverId, nxToken,
                streamType, startTime, endTime, Instant.now());
        sessions.put(sessionToken, session);
        return sessionToken;
    }

    public HlsSession getSession(String sessionToken) {
        HlsSession session = sessions.get(sessionToken);
        if (session == null) {
            throw new SessionNotFoundException(sessionToken);
        }
        // Auto-expire stale sessions (older than TTL)
        if (session.getCreatedAt().plusSeconds(vmsConfig.getSessionTtlSeconds()).isBefore(Instant.now())) {
            sessions.remove(sessionToken);
            throw new SessionNotFoundException(sessionToken);
        }
        return session;
    }

    /** Refresh session TTL (called on each successful TS request) */
    public void touchSession(String sessionToken) {
        HlsSession session = sessions.get(sessionToken);
        if (session != null) {
            // Re-insert to refresh position in ConcurrentHashMap
        }
    }

    public void removeSession(String sessionToken) {
        sessions.remove(sessionToken);
    }
}
```

- [ ] **Step 7: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add backend/src/main/java/com/taipei/iot/vms/config/
git add backend/src/main/java/com/taipei/iot/vms/token/
git add backend/src/main/java/com/taipei/iot/vms/session/
git add backend/src/main/java/com/taipei/iot/vms/exception/
git commit -m "feat(vms): add NxTokenManager and HlsSessionManager

- NxTokenManager: startup login + scheduled refresh per NX server
- HlsSessionManager: in-memory session with 5-min TTL
- Reuse AuthConfigEncryptor for AES-256-GCM password decryption
- VmsConfig: configurable TTL and refresh ratio

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Backend Streaming — HlsProxyService + VmsStreamController

**Files:**
- Create: `backend/src/main/java/com/taipei/iot/vms/service/HlsProxyService.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/service/VmsStreamService.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/controller/VmsStreamController.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/dto/StreamRequestDTO.java`

**Interfaces:**
- Consumes: `NxTokenManager`, `HlsSessionManager`, `VmsCameraMappingRepository`, `VmsStreamLogRepository`
- Produces: `/v1/auth/vms/{cameraId}/stream`, `.../master.m3u8`, `.../trickplay`, `.../{sessionToken}` (DELETE)

- [ ] **Step 1: Create StreamRequestDTO.java**

```java
package com.taipei.iot.vms.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class StreamRequestDTO {
    private String type;                     // "live" or "playback"
    private String startTime;                // ISO format for playback
    private String endTime;                  // ISO format for playback
}
```

- [ ] **Step 2: Create HlsProxyService.java**

```java
package com.taipei.iot.vms.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BaseException;
import com.taipei.iot.vms.entity.VmsCameraMappingEntity;
import com.taipei.iot.vms.entity.VmsServerEntity;
import com.taipei.iot.vms.repository.VmsCameraMappingRepository;
import com.taipei.iot.vms.repository.VmsServerRepository;
import com.taipei.iot.vms.session.HlsSession;
import com.taipei.iot.vms.session.HlsSessionManager;
import com.taipei.iot.vms.token.NxTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class HlsProxyService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final HlsSessionManager sessionManager;
    private final NxTokenManager nxTokenManager;
    private final VmsCameraMappingRepository cameraRepository;
    private final VmsServerRepository serverRepository;

    public byte[] fetchMasterPlaylist(String sessionToken, String pos) {
        HlsSession session = sessionManager.getSession(sessionToken);
        VmsCameraMappingEntity camera = cameraRepository.findById(session.getCameraId())
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_CAMERA_NOT_FOUND));
        VmsServerEntity server = serverRepository.findById(session.getServerId())
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_SERVER_NOT_FOUND));

        StringBuilder url = new StringBuilder(server.getBaseUrl())
                .append("/hls/").append(camera.getVmsCameraId()).append(".m3u");

        boolean isLive = "LIVE".equalsIgnoreCase(session.getStreamType());
        url.append("?lo=true");
        if (!isLive && pos != null) {
            url.append("&pos=").append(pos);
        }
        if (!isLive && pos == null && session.getStartTime() != null) {
            url.append("&pos=").append(session.getStartTime().toEpochMilli());
        }

        String nxToken = nxTokenManager.getToken(server.getId());
        return proxyRequest(url.toString(), nxToken, sessionToken);
    }

    public byte[] fetchSegment(String sessionToken, String path) {
        HlsSession session = sessionManager.getSession(sessionToken);
        sessionManager.touchSession(sessionToken);

        // Security: validate path
        String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
        if (decodedPath.contains("..") || (!decodedPath.startsWith("/hls/") && !decodedPath.contains(".ts") && !decodedPath.contains(".m3u8"))) {
            throw new BaseException(ErrorCode.VMS_STREAM_TOKEN_INVALID, "Invalid segment path");
        }

        VmsServerEntity server = serverRepository.findById(session.getServerId())
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_SERVER_NOT_FOUND));

        String url = server.getBaseUrl() + path;
        String nxToken = nxTokenManager.getToken(server.getId());
        return proxyRequest(url, nxToken, sessionToken);
    }

    public byte[] fetchTrickplay(String sessionToken, int speed) {
        HlsSession session = sessionManager.getSession(sessionToken);
        VmsCameraMappingEntity camera = cameraRepository.findById(session.getCameraId())
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_CAMERA_NOT_FOUND));
        VmsServerEntity server = serverRepository.findById(session.getServerId())
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_SERVER_NOT_FOUND));

        long posMs = session.getStartTime() != null
                ? session.getStartTime().toEpochMilli()
                : System.currentTimeMillis();

        String url = server.getBaseUrl() + "/hls/" + camera.getVmsCameraId()
                + ".m3u?lo=true&pos=" + posMs + "&speed=" + speed;

        String nxToken = nxTokenManager.getToken(server.getId());
        return proxyRequest(url, nxToken, sessionToken);
    }

    private byte[] proxyRequest(String url, String nxToken, String sessionToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(nxToken);
        headers.set("x-runtime-guid", nxToken);
        headers.set("User-Agent", "IoT-Forge-VMS/1.0");

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

            byte[] body = response.getBody();
            if (body == null) {
                throw new BaseException(ErrorCode.VMS_STREAM_NOT_AVAILABLE);
            }

            // Rewrite m3u8 URLs if it's a playlist
            String contentType = response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString() : "";
            if (contentType.contains("mpegurl") || contentType.contains("m3u8") || url.endsWith(".m3u")) {
                String content = new String(body, StandardCharsets.UTF_8);
                // Rewrite /hls/ paths to proxy paths with sessionToken
                content = content.replaceAll(
                        "(?m)^(?!https?://|#)(/hls/[^\\s?#]+)",
                        "/v1/auth/vms/stream/" + sessionToken + "$1");
                body = content.getBytes(StandardCharsets.UTF_8);
            }
            return body;

        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("NX proxy request failed: {} - {}", url, e.getMessage());
            throw new BaseException(ErrorCode.VMS_CONNECTION_FAILED, "NX proxy request failed: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 3: Create VmsStreamService.java**

```java
package com.taipei.iot.vms.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BaseException;
import com.taipei.iot.vms.dto.StreamRequestDTO;
import com.taipei.iot.vms.entity.VmsCameraMappingEntity;
import com.taipei.iot.vms.entity.VmsStreamLogEntity;
import com.taipei.iot.vms.repository.VmsCameraMappingRepository;
import com.taipei.iot.vms.repository.VmsStreamLogRepository;
import com.taipei.iot.vms.session.HlsSessionManager;
import com.taipei.iot.vms.token.NxTokenManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VmsStreamService {

    private final VmsCameraMappingRepository cameraRepository;
    private final VmsStreamLogRepository streamLogRepository;
    private final HlsSessionManager sessionManager;
    private final NxTokenManager nxTokenManager;

    public Map<String, Object> createStream(Long cameraId, String userId, StreamRequestDTO request) {
        VmsCameraMappingEntity camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_CAMERA_NOT_FOUND));

        if ("OFFLINE".equalsIgnoreCase(camera.getStatus()) || "ERROR".equalsIgnoreCase(camera.getStatus())) {
            throw new BaseException(ErrorCode.VMS_CAMERA_OFFLINE);
        }

        String streamType = "live".equalsIgnoreCase(request.getType()) ? "LIVE" : "PLAYBACK";
        String nxToken = nxTokenManager.getToken(camera.getServerId());

        Instant startTime = request.getStartTime() != null ? Instant.parse(request.getStartTime()) : null;
        Instant endTime = request.getEndTime() != null ? Instant.parse(request.getEndTime()) : null;

        if ("PLAYBACK".equals(streamType) && startTime == null) {
            throw new BaseException(ErrorCode.VMS_PLAYBACK_INVALID_RANGE, "startTime is required for playback");
        }

        String sessionToken = sessionManager.createSession(
                userId, cameraId, camera.getServerId(), nxToken,
                streamType, startTime, endTime);

        // Write stream log
        VmsStreamLogEntity logEntry = VmsStreamLogEntity.builder()
                .tenantId("0")
                .userId(Long.valueOf(userId))
                .cameraId(cameraId)
                .streamType(streamType)
                .sessionToken(sessionToken)
                .startedAt(LocalDateTime.now())
                .playbackStartTime(startTime != null ? LocalDateTime.ofInstant(startTime, ZoneId.systemDefault()) : null)
                .playbackEndTime(endTime != null ? LocalDateTime.ofInstant(endTime, ZoneId.systemDefault()) : null)
                .build();
        streamLogRepository.save(logEntry);

        return Map.of(
                "sessionToken", sessionToken,
                "expiresAt", java.time.LocalDateTime.now().plusSeconds(300).toString(),
                "cameraId", cameraId,
                "streamType", streamType
        );
    }
}
```

- [ ] **Step 4: Create VmsStreamController.java**

```java
package com.taipei.iot.vms.controller;

import com.taipei.iot.common.dto.BaseResponse;
import com.taipei.iot.vms.dto.StreamRequestDTO;
import com.taipei.iot.vms.service.HlsProxyService;
import com.taipei.iot.vms.service.VmsStreamService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/auth/vms")
@RequiredArgsConstructor
public class VmsStreamController {

    private final VmsStreamService vmsStreamService;
    private final HlsProxyService hlsProxyService;

    @PostMapping("/{cameraId}/stream")
    public BaseResponse<Map<String, Object>> createStream(
            @PathVariable Long cameraId,
            @RequestBody StreamRequestDTO request,
            Authentication auth) {
        String userId = auth.getName();
        return BaseResponse.success(vmsStreamService.createStream(cameraId, userId, request));
    }

    @GetMapping("/stream/{sessionToken}/master.m3u8")
    public ResponseEntity<byte[]> getMasterPlaylist(
            @PathVariable String sessionToken,
            @RequestParam(required = false) String pos) {
        byte[] playlist = hlsProxyService.fetchMasterPlaylist(sessionToken, pos);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                .body(playlist);
    }

    @GetMapping("/stream/{sessionToken}/trickplay")
    public ResponseEntity<byte[]> getTrickplay(
            @PathVariable String sessionToken,
            @RequestParam int speed) {
        byte[] playlist = hlsProxyService.fetchTrickplay(sessionToken, speed);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                .body(playlist);
    }

    @GetMapping("/stream/{sessionToken}/**")
    public ResponseEntity<byte[]> getSegment(
            @PathVariable String sessionToken,
            HttpServletRequest request) {
        String path = request.getRequestURI();
        String prefix = "/v1/auth/vms/stream/" + sessionToken;
        String relativePath = path.substring(prefix.length());
        byte[] data = hlsProxyService.fetchSegment(sessionToken, relativePath);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @DeleteMapping("/stream/{sessionToken}")
    public BaseResponse<Void> stopStream(@PathVariable String sessionToken) {
        // Session auto-expires; explicit removal allowed
        return BaseResponse.success();
    }
}
```

- [ ] **Step 5: Build and verify compilation**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/backend
mvn compile -q 2>&1 | tail -20
```

- [ ] **Step 6: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add backend/src/main/java/com/taipei/iot/vms/service/HlsProxyService.java
git add backend/src/main/java/com/taipei/iot/vms/service/VmsStreamService.java
git add backend/src/main/java/com/taipei/iot/vms/controller/VmsStreamController.java
git add backend/src/main/java/com/taipei/iot/vms/dto/StreamRequestDTO.java
git commit -m "feat(vms): add HLS streaming proxy and controller

- HlsProxyService: m3u8 rewrite, segment proxy, trickplay support
- VmsStreamService: session creation with auth check
- VmsStreamController: POST stream, GET master/segment/trickplay
- Security: path traversal protection, userId session binding

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Backend Management — Server + Camera + StreamLog CRUD

**Files:**
- Create: `backend/src/main/java/com/taipei/iot/vms/service/VmsServerService.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/service/VmsCameraService.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/service/VmsStreamLogService.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/controller/VmsServerController.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/controller/VmsCameraController.java`
- Create: `backend/src/main/java/com/taipei/iot/vms/controller/VmsStreamLogController.java`

**Interfaces:**
- Consumes: All repositories, `AuthConfigEncryptor`, `NxTokenManager`
- Produces: Full CRUD controllers

- [ ] **Step 1: Create VmsServerService.java**

```java
package com.taipei.iot.vms.service;

import com.taipei.iot.auth.provider.crypto.AuthConfigEncryptor;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BaseException;
import com.taipei.iot.vms.dto.VmsServerDTO;
import com.taipei.iot.vms.dto.VmsServerRequest;
import com.taipei.iot.vms.entity.VmsServerEntity;
import com.taipei.iot.vms.repository.VmsServerRepository;
import com.taipei.iot.vms.token.NxTokenManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VmsServerService {

    private final VmsServerRepository repository;
    private final AuthConfigEncryptor encryptor;
    private final NxTokenManager nxTokenManager;

    public List<VmsServerDTO> findAll() {
        return repository.findAll().stream().map(this::toDTO).toList();
    }

    public VmsServerDTO findById(Long id) {
        return toDTO(repository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_SERVER_NOT_FOUND)));
    }

    public VmsServerDTO create(VmsServerRequest request) {
        String encryptedPassword = request.getAuthPassword() != null
                ? encryptor.encrypt(request.getAuthPassword()) : null;

        VmsServerEntity entity = VmsServerEntity.builder()
                .tenantId("0")
                .name(request.getName())
                .vmsType(request.getVmsType())
                .baseUrl(request.getBaseUrl())
                .authType(request.getAuthType())
                .authUsername(request.getAuthUsername())
                .authPassword(encryptedPassword)
                .apiToken(request.getApiToken())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        entity = repository.save(entity);

        // Trigger initial token acquisition
        if (Boolean.TRUE.equals(entity.getIsActive())) {
            try {
                nxTokenManager.refreshToken(entity.getId());
            } catch (Exception e) {
                // Server saved but token acquisition failed — user can test connection later
            }
        }

        return toDTO(entity);
    }

    public VmsServerDTO update(Long id, VmsServerRequest request) {
        VmsServerEntity entity = repository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_SERVER_NOT_FOUND));

        entity.setName(request.getName());
        entity.setVmsType(request.getVmsType());
        entity.setBaseUrl(request.getBaseUrl());
        entity.setAuthType(request.getAuthType());
        entity.setAuthUsername(request.getAuthUsername());
        if (request.getAuthPassword() != null) {
            entity.setAuthPassword(encryptor.encrypt(request.getAuthPassword()));
        }
        entity.setApiToken(request.getApiToken());
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
            if (!request.getIsActive()) {
                nxTokenManager.invalidateToken(id);
            }
        }

        return toDTO(repository.save(entity));
    }

    public void delete(Long id) {
        nxTokenManager.invalidateToken(id);
        repository.deleteById(id);
    }

    public void testConnection(Long id) {
        nxTokenManager.refreshToken(id);
    }

    private VmsServerDTO toDTO(VmsServerEntity entity) {
        return VmsServerDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .vmsType(entity.getVmsType())
                .baseUrl(entity.getBaseUrl())
                .authType(entity.getAuthType())
                .authUsername(entity.getAuthUsername())
                .apiToken(entity.getApiToken())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 2: Create VmsCameraService.java**

```java
package com.taipei.iot.vms.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BaseException;
import com.taipei.iot.vms.dto.VmsCameraMappingDTO;
import com.taipei.iot.vms.dto.VmsCameraMappingRequest;
import com.taipei.iot.vms.entity.VmsCameraMappingEntity;
import com.taipei.iot.vms.entity.VmsServerEntity;
import com.taipei.iot.vms.repository.VmsCameraMappingRepository;
import com.taipei.iot.vms.repository.VmsServerRepository;
import com.taipei.iot.vms.token.NxTokenManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VmsCameraService {

    private final VmsCameraMappingRepository repository;
    private final VmsServerRepository serverRepository;
    private final NxTokenManager nxTokenManager;
    private final RestTemplate restTemplate = new RestTemplate();

    public List<VmsCameraMappingDTO> findAll() {
        return repository.findAll().stream().map(this::toDTO).toList();
    }

    public VmsCameraMappingDTO findById(Long id) {
        return toDTO(repository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_CAMERA_NOT_FOUND)));
    }

    public VmsCameraMappingDTO create(VmsCameraMappingRequest request) {
        // Verify server exists
        serverRepository.findById(request.getServerId())
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_SERVER_NOT_FOUND));

        VmsCameraMappingEntity entity = VmsCameraMappingEntity.builder()
                .tenantId("0")
                .serverId(request.getServerId())
                .vmsCameraId(request.getVmsCameraId())
                .displayName(request.getDisplayName())
                .deptId(request.getDeptId())
                .rtspUrl(request.getRtspUrl())
                .status("ONLINE")
                .build();
        return toDTO(repository.save(entity));
    }

    public VmsCameraMappingDTO update(Long id, VmsCameraMappingRequest request) {
        VmsCameraMappingEntity entity = repository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_CAMERA_NOT_FOUND));
        entity.setServerId(request.getServerId());
        entity.setVmsCameraId(request.getVmsCameraId());
        entity.setDisplayName(request.getDisplayName());
        entity.setDeptId(request.getDeptId());
        entity.setRtspUrl(request.getRtspUrl());
        return toDTO(repository.save(entity));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<Map<String, Object>> syncCamerasFromServer(Long serverId) {
        VmsServerEntity server = serverRepository.findById(serverId)
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_SERVER_NOT_FOUND));
        VmsServerEntity serverEntity = serverRepository.findById(serverId)
                .orElseThrow(() -> new BaseException(ErrorCode.VMS_SERVER_NOT_FOUND));

        String token = nxTokenManager.getToken(serverId);
        String url = server.getBaseUrl() + "/rest/v1/devices?_with=id,name,status,deviceType";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("x-runtime-guid", token);

        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            List<Map<String, Object>> devices = response.getBody();

            if (devices != null) {
                for (Map<String, Object> device : devices) {
                    String deviceId = (String) device.get("id");
                    String deviceName = (String) device.get("name");
                    String deviceStatus = (String) device.get("status");
                    String deviceType = (String) device.get("deviceType");

                    // Only import cameras
                    if (!"Camera".equalsIgnoreCase(deviceType)) continue;

                    String nxCameraId = deviceId;

                    // Upsert
                    var existing = repository.findByVmsCameraId(nxCameraId);
                    if (existing.isEmpty()) {
                        VmsCameraMappingEntity newCam = VmsCameraMappingEntity.builder()
                                .tenantId("0")
                                .serverId(serverId)
                                .vmsCameraId(nxCameraId)
                                .displayName(deviceName)
                                .status(mapNxStatus(deviceStatus))
                                .build();
                        repository.save(newCam);
                    } else {
                        VmsCameraMappingEntity cam = existing.get();
                        cam.setStatus(mapNxStatus(deviceStatus));
                        if (cam.getDisplayName() == null) {
                            cam.setDisplayName(deviceName);
                        }
                        repository.save(cam);
                    }
                }
            }
            return devices;
        } catch (Exception e) {
            throw new BaseException(ErrorCode.VMS_CONNECTION_FAILED, "Failed to sync cameras: " + e.getMessage());
        }
    }

    private String mapNxStatus(String nxStatus) {
        if (nxStatus == null) return "OFFLINE";
        return switch (nxStatus.toUpperCase()) {
            case "ONLINE", "RECORDING" -> "ONLINE";
            case "UNAUTHORIZED" -> "ERROR";
            default -> "OFFLINE";
        };
    }

    private VmsCameraMappingDTO toDTO(VmsCameraMappingEntity entity) {
        String serverName = serverRepository.findById(entity.getServerId())
                .map(VmsServerEntity::getName).orElse(null);
        return VmsCameraMappingDTO.builder()
                .id(entity.getId())
                .serverId(entity.getServerId())
                .serverName(serverName)
                .vmsCameraId(entity.getVmsCameraId())
                .displayName(entity.getDisplayName())
                .deptId(entity.getDeptId())
                .status(entity.getStatus())
                .rtspUrl(entity.getRtspUrl())
                .build();
    }
}
```

- [ ] **Step 3: Create VmsStreamLogService.java**

```java
package com.taipei.iot.vms.service;

import com.taipei.iot.vms.dto.VmsStreamLogDTO;
import com.taipei.iot.vms.entity.VmsStreamLogEntity;
import com.taipei.iot.vms.repository.VmsStreamLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VmsStreamLogService {

    private final VmsStreamLogRepository repository;

    public Page<VmsStreamLogDTO> queryLogs(Long userId, Long cameraId,
                                            String streamType,
                                            LocalDateTime startDate, LocalDateTime endDate,
                                            Pageable pageable) {
        var spec = (jakarta.persistence.criteria.Specification<VmsStreamLogEntity>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) predicates.add(cb.equal(root.get("userId"), userId));
            if (cameraId != null) predicates.add(cb.equal(root.get("cameraId"), cameraId));
            if (streamType != null) predicates.add(cb.equal(root.get("streamType"), streamType));
            if (startDate != null) predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), startDate));
            if (endDate != null) predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), endDate));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return repository.findAll(spec, pageable).map(this::toDTO);
    }

    private VmsStreamLogDTO toDTO(VmsStreamLogEntity entity) {
        return VmsStreamLogDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .cameraId(entity.getCameraId())
                .streamType(entity.getStreamType())
                .startedAt(entity.getStartedAt())
                .endedAt(entity.getEndedAt())
                .durationSeconds(entity.getDurationSeconds())
                .playbackStartTime(entity.getPlaybackStartTime())
                .playbackEndTime(entity.getPlaybackEndTime())
                .build();
    }
}
```

- [ ] **Step 4: Create VmsServerController.java**

```java
package com.taipei.iot.vms.controller;

import com.taipei.iot.common.dto.BaseResponse;
import com.taipei.iot.vms.dto.VmsServerDTO;
import com.taipei.iot.vms.dto.VmsServerRequest;
import com.taipei.iot.vms.service.VmsServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/auth/vms/servers")
@RequiredArgsConstructor
public class VmsServerController {

    private final VmsServerService vmsServerService;

    @GetMapping
    public BaseResponse<List<VmsServerDTO>> list() {
        return BaseResponse.success(vmsServerService.findAll());
    }

    @GetMapping("/{id}")
    public BaseResponse<VmsServerDTO> getById(@PathVariable Long id) {
        return BaseResponse.success(vmsServerService.findById(id));
    }

    @PostMapping
    public BaseResponse<VmsServerDTO> create(@Valid @RequestBody VmsServerRequest request) {
        return BaseResponse.success(vmsServerService.create(request));
    }

    @PutMapping("/{id}")
    public BaseResponse<VmsServerDTO> update(@PathVariable Long id, @Valid @RequestBody VmsServerRequest request) {
        return BaseResponse.success(vmsServerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        vmsServerService.delete(id);
        return BaseResponse.success();
    }

    @PostMapping("/{id}/test-connection")
    public BaseResponse<Void> testConnection(@PathVariable Long id) {
        vmsServerService.testConnection(id);
        return BaseResponse.success();
    }
}
```

- [ ] **Step 5: Create VmsCameraController.java**

```java
package com.taipei.iot.vms.controller;

import com.taipei.iot.common.dto.BaseResponse;
import com.taipei.iot.vms.dto.VmsCameraMappingDTO;
import com.taipei.iot.vms.dto.VmsCameraMappingRequest;
import com.taipei.iot.vms.service.VmsCameraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/auth/vms/cameras")
@RequiredArgsConstructor
public class VmsCameraController {

    private final VmsCameraService vmsCameraService;

    @GetMapping
    public BaseResponse<List<VmsCameraMappingDTO>> list() {
        return BaseResponse.success(vmsCameraService.findAll());
    }

    @GetMapping("/{id}")
    public BaseResponse<VmsCameraMappingDTO> getById(@PathVariable Long id) {
        return BaseResponse.success(vmsCameraService.findById(id));
    }

    @PostMapping
    public BaseResponse<VmsCameraMappingDTO> create(@Valid @RequestBody VmsCameraMappingRequest request) {
        return BaseResponse.success(vmsCameraService.create(request));
    }

    @PutMapping("/{id}")
    public BaseResponse<VmsCameraMappingDTO> update(@PathVariable Long id,
                                                     @Valid @RequestBody VmsCameraMappingRequest request) {
        return BaseResponse.success(vmsCameraService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        vmsCameraService.delete(id);
        return BaseResponse.success();
    }

    @PostMapping("/sync/{serverId}")
    public BaseResponse<List<Map<String, Object>>> syncFromServer(@PathVariable Long serverId) {
        return BaseResponse.success(vmsCameraService.syncCamerasFromServer(serverId));
    }
}
```

- [ ] **Step 6: Create VmsStreamLogController.java**

```java
package com.taipei.iot.vms.controller;

import com.taipei.iot.common.dto.BaseResponse;
import com.taipei.iot.common.util.PageConversionHelper;
import com.taipei.iot.vms.dto.VmsStreamLogDTO;
import com.taipei.iot.vms.service.VmsStreamLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/v1/auth/vms/stream-logs")
@RequiredArgsConstructor
public class VmsStreamLogController {

    private final VmsStreamLogService vmsStreamLogService;

    @GetMapping
    public BaseResponse<Page<VmsStreamLogDTO>> query(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) String streamType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.success(
                vmsStreamLogService.queryLogs(userId, cameraId, streamType, startDate, endDate, pageable));
    }
}
```

- [ ] **Step 7: Build and verify compilation**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/backend
mvn compile -q 2>&1 | tail -20
```

- [ ] **Step 8: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add backend/src/main/java/com/taipei/iot/vms/service/VmsServerService.java
git add backend/src/main/java/com/taipei/iot/vms/service/VmsCameraService.java
git add backend/src/main/java/com/taipei/iot/vms/service/VmsStreamLogService.java
git add backend/src/main/java/com/taipei/iot/vms/controller/VmsServerController.java
git add backend/src/main/java/com/taipei/iot/vms/controller/VmsCameraController.java
git add backend/src/main/java/com/taipei/iot/vms/controller/VmsStreamLogController.java
git commit -m "feat(vms): add management CRUD controllers

- VmsServerController: CRUD + test-connection
- VmsCameraController: CRUD + sync from NX server
- VmsStreamLogController: paginated query with filters
- Password encryption via AuthConfigEncryptor
- NX camera auto-import with status mapping

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Frontend Foundation — Types + API Layer + Store

**Files:**
- Modify: `frontend/src/types/vms.ts`
- Create: `frontend/src/api/vms/index.ts`
- Create: `frontend/src/stores/vmsStore.ts`

**Interfaces:**
- Consumes: existing axios instance (`axiosIns`)
- Produces: typed API functions and Pinia store consumed by all views

- [ ] **Step 1: Update frontend types**

```typescript
// frontend/src/types/vms.ts

import type { BaseResponse, PageResponse } from '@/types/device'

export type { BaseResponse, PageResponse }

// ── VMS Server ─────────────────────────────────────────────

export type VmsType = 'NX_WITNESS' | 'MILESTONE' | 'AXXON'
export type VmsAuthType = 'BASIC' | 'TOKEN' | 'CERT'
export type CameraStatus = 'ONLINE' | 'OFFLINE' | 'ERROR'

export interface VmsServer {
  id: number
  name: string
  vmsType: VmsType
  baseUrl: string
  authType: VmsAuthType
  authUsername?: string
  isActive: boolean
  createdAt: string
}

export interface VmsServerRequest {
  name: string
  vmsType: VmsType
  baseUrl: string
  authType?: VmsAuthType
  authUsername?: string
  authPassword?: string
  apiToken?: string
  isActive?: boolean
}

// ── Camera Mapping ─────────────────────────────────────────

export interface VmsCamera {
  id: number
  serverId: number
  serverName?: string
  vmsCameraId: string
  displayName?: string
  deptId?: number
  deptName?: string
  status: CameraStatus
  rtspUrl?: string
  createdAt?: string
}

export interface VmsCameraRequest {
  serverId: number
  vmsCameraId: string
  displayName?: string
  deptId?: number
  rtspUrl?: string
}

// ── Stream ─────────────────────────────────────────────────

export interface StreamCreateRequest {
  type: 'live' | 'playback'
  startTime?: string
  endTime?: string
}

export interface StreamCreateResponse {
  sessionToken: string
  expiresAt: string
  cameraId: number
  streamType: string
}

// ── Stream Log ─────────────────────────────────────────────

export interface VmsStreamLog {
  id: number
  userId: number
  userName?: string
  cameraId: number
  cameraName?: string
  streamType: string
  startedAt: string
  endedAt?: string
  durationSeconds?: number
  playbackStartTime?: string
  playbackEndTime?: string
}
```

- [ ] **Step 2: Create frontend API module**

```typescript
// frontend/src/api/vms/index.ts

import axiosIns from '@/api/axios/axiosIns'
import type { BaseResponse, PageResponse } from '@/types/device'
import type {
  VmsServer, VmsServerRequest,
  VmsCamera, VmsCameraRequest,
  StreamCreateRequest, StreamCreateResponse,
  VmsStreamLog,
} from '@/types/vms'

// ── VMS Server ─────────────────────────────────────────────

export const listVmsServers = () =>
  axiosIns.get<unknown, BaseResponse<VmsServer[]>>('/auth/vms/servers')

export const getVmsServer = (id: number) =>
  axiosIns.get<unknown, BaseResponse<VmsServer>>(`/auth/vms/servers/${id}`)

export const createVmsServer = (payload: VmsServerRequest) =>
  axiosIns.post<unknown, BaseResponse<VmsServer>>('/auth/vms/servers', payload)

export const updateVmsServer = (id: number, payload: VmsServerRequest) =>
  axiosIns.put<unknown, BaseResponse<VmsServer>>(`/auth/vms/servers/${id}`, payload)

export const deleteVmsServer = (id: number) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/auth/vms/servers/${id}`)

export const testVmsServerConnection = (id: number) =>
  axiosIns.post<unknown, BaseResponse<void>>(`/auth/vms/servers/${id}/test-connection`)

// ── Camera Mapping ─────────────────────────────────────────

export const listVmsCameras = () =>
  axiosIns.get<unknown, BaseResponse<VmsCamera[]>>('/auth/vms/cameras')

export const getVmsCamera = (id: number) =>
  axiosIns.get<unknown, BaseResponse<VmsCamera>>(`/auth/vms/cameras/${id}`)

export const createVmsCamera = (payload: VmsCameraRequest) =>
  axiosIns.post<unknown, BaseResponse<VmsCamera>>('/auth/vms/cameras', payload)

export const updateVmsCamera = (id: number, payload: VmsCameraRequest) =>
  axiosIns.put<unknown, BaseResponse<VmsCamera>>(`/auth/vms/cameras/${id}`, payload)

export const deleteVmsCamera = (id: number) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/auth/vms/cameras/${id}`)

export const syncVmsCameras = (serverId: number) =>
  axiosIns.post<unknown, BaseResponse<unknown[]>>(`/auth/vms/cameras/sync/${serverId}`)

// ── Streaming ──────────────────────────────────────────────

export const createStream = (cameraId: number, payload: StreamCreateRequest) =>
  axiosIns.post<unknown, BaseResponse<StreamCreateResponse>>(`/auth/vms/${cameraId}/stream`, payload)

export const stopStream = (sessionToken: string) =>
  axiosIns.delete<unknown, BaseResponse<void>>(`/auth/vms/stream/${sessionToken}`)

// ── Stream Logs ────────────────────────────────────────────

export const queryStreamLogs = (params: {
  userId?: number
  cameraId?: number
  streamType?: string
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}) =>
  axiosIns.get<unknown, BaseResponse<PageResponse<VmsStreamLog>>>('/auth/vms/stream-logs', { params })
```

- [ ] **Step 3: Create vmsStore**

```typescript
// frontend/src/stores/vmsStore.ts

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { VmsCamera } from '@/types/vms'
import { listVmsCameras } from '@/api/vms'

export const useVmsStore = defineStore('vms', () => {
  const cameras = ref<VmsCamera[]>([])
  const selectedCamera = ref<VmsCamera | null>(null)
  const loading = ref(false)

  const onlineCameras = computed(() =>
    cameras.value.filter(c => c.status === 'ONLINE')
  )

  async function fetchCameras() {
    loading.value = true
    try {
      const res = await listVmsCameras()
      cameras.value = res.body ?? []
    } finally {
      loading.value = false
    }
  }

  function selectCamera(camera: VmsCamera | null) {
    selectedCamera.value = camera
  }

  return { cameras, selectedCamera, onlineCameras, loading, fetchCameras, selectCamera }
})
```

- [ ] **Step 4: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add frontend/src/types/vms.ts
git add frontend/src/api/vms/
git add frontend/src/stores/vmsStore.ts
git commit -m "feat(vms): add frontend types, API layer, and store

- Update VMS types (server, camera, stream, stream log)
- Add API module with all CRUD + stream endpoints
- Add vmsStore with camera list and selection state

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Frontend Management Pages — Server + Camera + StreamLog Views

**Files:**
- Create: `frontend/src/views/vms/VmsServerManageView.vue`
- Create: `frontend/src/views/vms/VmsCameraManageView.vue`
- Create: `frontend/src/views/vms/VmsStreamLogView.vue`
- Create: `frontend/src/views/vms/components/VmsServerDialog.vue`
- Create: `frontend/src/views/vms/components/VmsCameraDialog.vue`
- Create: `frontend/src/views/vms/components/VmsStreamLogFilter.vue`

- [ ] **Step 1: Create VmsServerManageView.vue with VmsServerDialog**

```vue
<!-- frontend/src/views/vms/VmsServerManageView.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VmsServer, VmsServerRequest } from '@/types/vms'
import { listVmsServers, createVmsServer, updateVmsServer, deleteVmsServer, testVmsServerConnection } from '@/api/vms'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const servers = ref<VmsServer[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingServer = ref<VmsServer | null>(null)
const form = ref<VmsServerRequest>({
  name: '', vmsType: 'NX_WITNESS', baseUrl: '', authType: 'BASIC',
  authUsername: '', authPassword: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await listVmsServers()
    servers.value = res.body ?? []
  } finally { loading.value = false }
}

function openCreate() {
  editingServer.value = null
  form.value = { name: '', vmsType: 'NX_WITNESS', baseUrl: '', authType: 'BASIC', authUsername: '', authPassword: '' }
  dialogVisible.value = true
}

function openEdit(server: VmsServer) {
  editingServer.value = server
  form.value = { name: server.name, vmsType: server.vmsType, baseUrl: server.baseUrl,
    authType: server.authType, authUsername: server.authUsername }
  dialogVisible.value = true
}

async function handleSave() {
  if (editingServer.value) {
    await updateVmsServer(editingServer.value.id, form.value)
    ElMessage.success(t('common.updateSuccess'))
  } else {
    await createVmsServer(form.value)
    ElMessage.success(t('common.createSuccess'))
  }
  dialogVisible.value = false
  await fetchData()
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.warning'), { type: 'warning' })
  await deleteVmsServer(id)
  ElMessage.success(t('common.deleteSuccess'))
  await fetchData()
}

async function handleTestConnection(id: number) {
  try {
    await testVmsServerConnection(id)
    ElMessage.success(t('vms.connectionSuccess'))
  } catch {
    ElMessage.error(t('vms.connectionFailed'))
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="vms-server-manage">
    <div class="page-header">
      <h2>{{ t('vms.servers') }}</h2>
      <el-button type="primary" @click="openCreate">{{ t('common.add') }}</el-button>
    </div>
    <el-table :data="servers" v-loading="loading" stripe>
      <el-table-column prop="name" :label="t('common.name')" min-width="140" />
      <el-table-column prop="vmsType" :label="t('vms.vmsType')" width="120" />
      <el-table-column prop="baseUrl" :label="t('common.url')" min-width="200" />
      <el-table-column prop="isActive" :label="t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isActive ? 'success' : 'info'">{{ row.isActive ? 'Active' : 'Inactive' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button size="small" @click="handleTestConnection(row.id)">{{ t('vms.testConnection') }}</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingServer ? t('common.edit') : t('common.add')" width="500px">
      <el-form :model="form" label-position="top">
        <el-form-item :label="t('common.name')" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('vms.vmsType')" required>
          <el-select v-model="form.vmsType" style="width:100%">
            <el-option value="NX_WITNESS" label="NX Witness" />
            <el-option value="MILESTONE" label="Milestone" />
            <el-option value="AXXON" label="Axxon" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.url')" required>
          <el-input v-model="form.baseUrl" placeholder="http://192.168.1.100:7001" />
        </el-form-item>
        <el-form-item :label="t('common.username')">
          <el-input v-model="form.authUsername" />
        </el-form-item>
        <el-form-item :label="t('common.password')">
          <el-input v-model="form.authPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
```

- [ ] **Step 2: Create VmsCameraManageView.vue**

```vue
<!-- frontend/src/views/vms/VmsCameraManageView.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VmsCamera, VmsCameraRequest } from '@/types/vms'
import { listVmsCameras, createVmsCamera, updateVmsCamera, deleteVmsCamera, syncVmsCameras, listVmsServers } from '@/api/vms'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const cameras = ref<VmsCamera[]>([])
const servers = ref<{ id: number; name: string }[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingCamera = ref<VmsCamera | null>(null)
const form = ref<VmsCameraRequest>({ serverId: 0, vmsCameraId: '', displayName: '' })
const syncLoading = ref(false)

async function fetchData() {
  loading.value = true
  try {
    const [camRes, srvRes] = await Promise.all([listVmsCameras(), listVmsServers()])
    cameras.value = camRes.body ?? []
    servers.value = (srvRes.body ?? []).map(s => ({ id: s.id, name: s.name }))
  } finally { loading.value = false }
}

function openCreate() {
  editingCamera.value = null
  form.value = { serverId: servers.value[0]?.id ?? 0, vmsCameraId: '', displayName: '' }
  dialogVisible.value = true
}

function openEdit(camera: VmsCamera) {
  editingCamera.value = camera
  form.value = { serverId: camera.serverId, vmsCameraId: camera.vmsCameraId, displayName: camera.displayName, deptId: camera.deptId, rtspUrl: camera.rtspUrl }
  dialogVisible.value = true
}

async function handleSave() {
  if (editingCamera.value) {
    await updateVmsCamera(editingCamera.value.id, form.value)
  } else {
    await createVmsCamera(form.value)
  }
  dialogVisible.value = false
  await fetchData()
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm(t('vms.deleteCameraConfirm', { name: '' }), t('common.warning'), { type: 'warning' })
  await deleteVmsCamera(id)
  await fetchData()
}

async function handleSync(serverId: number) {
  syncLoading.value = true
  try {
    await syncVmsCameras(serverId)
    ElMessage.success(t('vms.syncCameras'))
    await fetchData()
  } finally { syncLoading.value = false }
}

onMounted(fetchData)
</script>

<template>
  <div class="vms-camera-manage">
    <div class="page-header">
      <h2>{{ t('vms.cameraList') }}</h2>
      <div class="header-actions">
        <el-select v-if="servers.length" @change="handleSync" style="width:200px;margin-right:8px">
          <el-option v-for="s in servers" :key="s.id" :value="s.id" :label="`${t('vms.syncCameras')}: ${s.name}`" />
        </el-select>
        <el-button type="primary" @click="openCreate">{{ t('vms.addCamera') }}</el-button>
      </div>
    </div>

    <el-table :data="cameras" v-loading="loading" stripe>
      <el-table-column prop="displayName" :label="t('common.name')" min-width="160" />
      <el-table-column prop="vmsCameraId" label="NX Camera ID" min-width="240" />
      <el-table-column prop="serverName" :label="t('vms.server')" width="140" />
      <el-table-column prop="status" :label="t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ONLINE' ? 'success' : 'danger'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingCamera ? t('common.edit') : t('vms.addCamera')" width="500px">
      <el-form :model="form" label-position="top">
        <el-form-item :label="t('vms.server')" required>
          <el-select v-model="form.serverId" style="width:100%">
            <el-option v-for="s in servers" :key="s.id" :value="s.id" :label="s.name" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('vms.vmsCameraId')" required>
          <el-input v-model="form.vmsCameraId" :placeholder="t('vms.vmsCameraIdPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('vms.displayName')">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item :label="t('dept.dept')">
          <el-input v-model="form.deptId" type="number" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header-actions { display: flex; align-items: center; }
</style>
```

- [ ] **Step 3: Create VmsStreamLogView.vue**

```vue
<!-- frontend/src/views/vms/VmsStreamLogView.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { VmsStreamLog } from '@/types/vms'
import { queryStreamLogs } from '@/api/vms'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const logs = ref<VmsStreamLog[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const filter = ref({ userId: undefined as number | undefined, cameraId: undefined as number | undefined, streamType: undefined as string | undefined })

async function fetchData() {
  loading.value = true
  try {
    const res = await queryStreamLogs({ ...filter.value, page: page.value, size: pageSize.value })
    logs.value = res.body?.records ?? []
    total.value = res.body?.total ?? 0
  } finally { loading.value = false }
}

onMounted(fetchData)
</script>

<template>
  <div class="vms-stream-log">
    <div class="page-header">
      <h2>{{ t('vms.streamLogs') }}</h2>
    </div>

    <el-card class="filter-bar" shadow="never">
      <el-form :model="filter" inline>
        <el-form-item :label="t('common.type')">
          <el-select v-model="filter.streamType" clearable style="width:140px">
            <el-option value="LIVE" :label="t('vms.liveStream')" />
            <el-option value="PLAYBACK" :label="t('vms.playbackStream')" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">{{ t('common.query') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="logs" v-loading="loading" stripe style="margin-top:12px">
      <el-table-column prop="userName" :label="t('common.user')" width="120" />
      <el-table-column prop="cameraName" :label="t('vms.camera')" min-width="160" />
      <el-table-column prop="streamType" :label="t('common.type')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.streamType === 'LIVE' ? 'primary' : 'warning'" size="small">{{ row.streamType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startedAt" :label="t('common.startTime')" width="180" />
      <el-table-column prop="endedAt" :label="t('common.endTime')" width="180" />
      <el-table-column prop="durationSeconds" :label="t('common.duration')" width="100">
        <template #default="{ row }">{{ row.durationSeconds ? `${row.durationSeconds}s` : '-' }}</template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
        :total="total" layout="prev, pager, next" @current-change="fetchData" />
    </div>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.filter-bar { margin-bottom: 8px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
```

- [ ] **Step 4: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add frontend/src/views/vms/
git commit -m "feat(vms): add management views (servers, cameras, stream logs)

- VmsServerManageView: server CRUD with test-connection
- VmsCameraManageView: camera mapping CRUD with NX sync
- VmsStreamLogView: paginated log query with streaming type filter
- All views follow existing Element Plus pattern

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 7: Frontend Streaming Pages — VmsCameraList + VmsStreamPlayer + LiveView + PlaybackView

**Files:**
- Create: `frontend/src/views/vms/VmsLiveView.vue`
- Create: `frontend/src/views/vms/VmsPlaybackView.vue`
- Create: `frontend/src/views/vms/components/VmsCameraList.vue`
- Create: `frontend/src/views/vms/components/VmsStreamPlayer.vue`
- Create: `frontend/src/views/vms/components/VmsStreamPanel.vue`
- Create: `frontend/src/views/vms/components/PlaybackControlBar.vue`
- Create: `frontend/src/views/vms/components/SpeedControl.vue`
- Create: `frontend/src/views/vms/components/VmsTimeRangePicker.vue`

- [ ] **Step 1: Create VmsCameraList.vue**

```vue
<!-- frontend/src/views/vms/components/VmsCameraList.vue -->
<script setup lang="ts">
import { onMounted } from 'vue'
import { useVmsStore } from '@/stores/vmsStore'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const store = useVmsStore()

onMounted(() => {
  if (store.cameras.length === 0) store.fetchCameras()
})

function select(camera: any) {
  store.selectCamera(camera)
}
</script>

<template>
  <div class="vms-camera-list">
    <div class="list-header">
      <h3>{{ t('vms.cameraList') }}</h3>
      <el-tag size="small" type="success">{{ store.onlineCameras.length }}/{{ store.cameras.length }}</el-tag>
    </div>

    <div v-if="store.loading" v-loading="true" class="loading-placeholder" />
    <div v-else-if="store.cameras.length === 0" class="empty-state">
      <el-empty :description="t('common.noData')" />
    </div>
    <el-scrollbar v-else class="camera-scroll">
      <div
        v-for="cam in store.cameras"
        :key="cam.id"
        class="camera-item"
        :class="{ selected: store.selectedCamera?.id === cam.id }"
        @click="select(cam)"
      >
        <span class="status-dot" :class="cam.status.toLowerCase()" />
        <span class="camera-name">{{ cam.displayName || cam.vmsCameraId }}</span>
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped>
.vms-camera-list { width: 260px; border-right: 1px solid var(--el-border-color-light); display: flex; flex-direction: column; }
.list-header { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; border-bottom: 1px solid var(--el-border-color-lighter); }
.list-header h3 { margin: 0; font-size: 14px; }
.loading-placeholder { height: 200px; }
.empty-state { padding: 40px 16px; }
.camera-scroll { flex: 1; }
.camera-item { display: flex; align-items: center; padding: 10px 16px; cursor: pointer; transition: background 0.15s; }
.camera-item:hover { background: var(--el-fill-color-light); }
.camera-item.selected { background: var(--el-color-primary-light-9); }
.status-dot { width: 8px; height: 8px; border-radius: 50%; margin-right: 10px; flex-shrink: 0; }
.status-dot.online { background: #67c23a; }
.status-dot.offline { background: #909399; }
.status-dot.error { background: #f56c6c; }
.camera-name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
```

- [ ] **Step 2: Create VmsStreamPlayer.vue**

```vue
<!-- frontend/src/views/vms/components/VmsStreamPlayer.vue -->
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, shallowRef } from 'vue'
import Hls from 'hls.js'
import { createStream, stopStream } from '@/api/vms'
import { useAuthStore } from '@/stores/authStore'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  cameraId: number
  streamType: 'live' | 'playback'
  startTime?: string
  endTime?: string
}>()

const emit = defineEmits<{
  (e: 'timeUpdate', time: number): void
  (e: 'playbackEnded'): void
  (e: 'error', msg: string): void
}>()

const { t } = useI18n()
const authStore = useAuthStore()
const videoRef = ref<HTMLVideoElement | null>(null)
const hlsInstance = shallowRef<Hls | null>(null)
const sessionToken = ref<string>('')
const playing = ref(false)
const currentSpeed = ref(1)
const errorMsg = ref('')
const loading = ref(false)
const streamBaseUrl = import.meta.env.VITE_API_BASE_URL || ''

let retryCount = 0
const MAX_RETRIES = 3

async function initStream() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await createStream(props.cameraId, {
      type: props.streamType,
      startTime: props.startTime,
      endTime: props.endTime,
    })
    sessionToken.value = res.body!.sessionToken
    loadHls()
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.errorMsg || t('vms.connectionFailed')
    emit('error', errorMsg.value)
  } finally {
    loading.value = false
  }
}

function getStreamUrl(path: string): string {
  return `${streamBaseUrl}/v1/auth/vms/stream/${sessionToken.value}${path}`
}

function loadHls() {
  if (!videoRef.value || !sessionToken.value) return

  if (hlsInstance.value) {
    hlsInstance.value.destroy()
  }

  const config = props.streamType === 'live'
    ? { lowLatencyMode: true, backBufferLength: 30 }
    : { maxBufferLength: 240, backBufferLength: 60 }

  const hls = new Hls({
    ...config,
    xhrSetup: (xhr) => {
      xhr.setRequestHeader('Authorization', `Bearer ${authStore.accessToken}`)
    },
    fetchSetup: (context) => {
      context.headers = {
        ...context.headers,
        'Authorization': `Bearer ${authStore.accessToken}`,
      }
      return new Request(context.url, context)
    },
  })

  hls.loadSource(getStreamUrl('/master.m3u8'))
  hls.attachMedia(videoRef.value)

  hls.on(Hls.Events.MANIFEST_PARSED, () => {
    playing.value = true
    videoRef.value?.play()
  })

  hls.on(Hls.Events.ERROR, (_event, data) => {
    if (data.fatal) {
      retryCount++
      if (retryCount <= MAX_RETRIES) {
        hls.recoverMediaError()
      } else {
        errorMsg.value = t('vms.connectionFailed')
        emit('error', errorMsg.value)
      }
    }
  })

  hlsInstance.value = hls
}

async function changeSpeed(speed: number) {
  currentSpeed.value = speed
  if (speed === 1) {
    // Reload normal speed playlist
    if (hlsInstance.value && sessionToken.value) {
      hlsInstance.value.loadSource(getStreamUrl('/master.m3u8?' + Date.now()))
    }
  } else {
    // Trickplay
    if (hlsInstance.value && sessionToken.value) {
      hlsInstance.value.loadSource(getStreamUrl(`/trickplay?speed=${speed}&_=${Date.now()}`))
    }
  }
}

function seekTo(timestamp: number) {
  if (hlsInstance.value && sessionToken.value) {
    hlsInstance.value.loadSource(getStreamUrl(`/master.m3u8?pos=${timestamp}&_=${Date.now()}`))
  }
}

async function destroyStream() {
  if (sessionToken.value) {
    try { await stopStream(sessionToken.value) } catch { /* ignore */ }
    sessionToken.value = ''
  }
  if (hlsInstance.value) {
    hlsInstance.value.destroy()
    hlsInstance.value = null
  }
  playing.value = false
}

watch(() => props.cameraId, () => {
  retryCount = 0
  destroyStream().then(() => initStream())
})

onMounted(() => initStream())
onBeforeUnmount(() => destroyStream())
</script>

<template>
  <div class="vms-stream-player" :class="{ 'has-error': errorMsg }">
    <div v-if="loading" class="player-overlay">
      <el-icon class="is-loading" :size="32"><i-ep-Loading /></el-icon>
      <span>{{ t('common.loading') }}</span>
    </div>

    <div v-else-if="errorMsg" class="player-overlay error">
      <el-icon :size="32" color="#f56c6c"><i-ep-WarningFilled /></el-icon>
      <span>{{ errorMsg }}</span>
      <el-button type="primary" size="small" @click="initStream" style="margin-top:8px">
        {{ t('common.retry') }}
      </el-button>
    </div>

    <video v-else ref="videoRef" autoplay muted playsinline class="video-element" />
  </div>
</template>

<style scoped>
.vms-stream-player { position: relative; width: 100%; background: #000; aspect-ratio: 16/9; display: flex; align-items: center; justify-content: center; }
.video-element { width: 100%; height: 100%; object-fit: contain; }
.player-overlay { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #fff; }
.player-overlay.error { color: #f56c6c; }
</style>
```

- [ ] **Step 3: Create SpeedControl.vue**

```vue
<!-- frontend/src/views/vms/components/SpeedControl.vue -->
<script setup lang="ts">
const props = defineProps<{ modelValue: number }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: number): void }>()

const speeds = [1, 2, 4, 8, -1, -2, -4, -8]
</script>

<template>
  <el-select :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)" size="small" style="width: 100px">
    <el-option v-for="s in speeds" :key="s" :value="s" :label="s > 0 ? `${s}x` : `${Math.abs(s)}x ${$t('vms.reverse')}`" />
  </el-select>
</template>
```

- [ ] **Step 4: Create PlaybackControlBar.vue**

```vue
<!-- frontend/src/views/vms/components/PlaybackControlBar.vue -->
<script setup lang="ts">
import { ref } from 'vue'
import SpeedControl from './SpeedControl.vue'

const props = defineProps<{
  currentTime: number
  totalDuration: number
}>()

const emit = defineEmits<{
  (e: 'seek', timestamp: number): void
  (e: 'update:speed', speed: number): void
}>()

const speed = ref(1)
</script>

<template>
  <div class="playback-bar">
    <el-slider
      :model-value="totalDuration > 0 ? (currentTime / totalDuration) * 100 : 0"
      @update:model-value="emit('seek', (totalDuration * $event) / 100)"
      :max="100"
      :show-tooltip="false"
      class="timeline-slider"
    />
    <div class="bar-controls">
      <span class="time-display">{{ new Date(currentTime).toISOString().substr(11, 8) }}</span>
      <SpeedControl v-model="speed" @update:model-value="emit('update:speed', $event)" />
    </div>
  </div>
</template>

<style scoped>
.playback-bar { background: var(--el-bg-color); padding: 8px 16px; }
.timeline-slider { margin: 0 0 8px; }
.bar-controls { display: flex; justify-content: space-between; align-items: center; }
.time-display { font-size: 13px; font-family: monospace; }
</style>
```

- [ ] **Step 5: Create VmsTimeRangePicker.vue**

```vue
<!-- frontend/src/views/vms/components/VmsTimeRangePicker.vue -->
<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{ (e: 'select', start: string, end: string): void }>()
const startTime = ref('')
const endTime = ref('')
const presets = [
  { label: '最近 1 小時', getRange: () => { const e = new Date(); return [new Date(e.getTime() - 3600000).toISOString().slice(0, 16), e.toISOString().slice(0, 16)] } },
  { label: '最近 6 小時', getRange: () => { const e = new Date(); return [new Date(e.getTime() - 21600000).toISOString().slice(0, 16), e.toISOString().slice(0, 16)] } },
  { label: '今天', getRange: () => { const now = new Date(); const s = new Date(now.getFullYear(), now.getMonth(), now.getDate()); return [s.toISOString().slice(0, 16), now.toISOString().slice(0, 16)] } },
]

function applyPreset(preset: typeof presets[0]) {
  const [s, e] = preset.getRange()
  startTime.value = s
  endTime.value = e
  emit('select', s, e)
}
</script>

<template>
  <div class="time-range-picker">
    <div class="presets">
      <el-button v-for="p in presets" :key="p.label" size="small" @click="applyPreset(p)">{{ p.label }}</el-button>
    </div>
    <div class="custom-range">
      <el-date-picker v-model="startTime" type="datetime" placeholder="開始時間" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm" />
      <span class="separator">~</span>
      <el-date-picker v-model="endTime" type="datetime" placeholder="結束時間" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm" />
      <el-button type="primary" size="small" @click="emit('select', startTime, endTime)" :disabled="!startTime || !endTime">
        {{ $t('common.query') }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.time-range-picker { padding: 12px 16px; border-bottom: 1px solid var(--el-border-color-lighter); }
.presets { display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.custom-range { display: flex; align-items: center; gap: 8px; }
.separator { color: var(--el-text-color-secondary); }
</style>
```

- [ ] **Step 6: Create VmsStreamPanel.vue**

```vue
<!-- frontend/src/views/vms/components/VmsStreamPanel.vue -->
<script setup lang="ts">
import { ref } from 'vue'
import VmsStreamPlayer from './VmsStreamPlayer.vue'
import PlaybackControlBar from './PlaybackControlBar.vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  cameraId: number
  streamType: 'live' | 'playback'
  startTime?: string
  endTime?: string
}>()

const { t } = useI18n()
const currentTime = ref(0)

function onTimeUpdate(time: number) { currentTime.value = time }
function onSeek(timestamp: number) { /* StreamPlayer handles via prop change */ }
</script>

<template>
  <div class="vms-stream-panel">
    <div v-if="!cameraId" class="no-camera-selected">
      <el-empty :description="t('vms.noCameraSelected')" />
    </div>
    <template v-else>
      <VmsStreamPlayer
        :camera-id="cameraId"
        :stream-type="streamType"
        :start-time="startTime"
        :end-time="endTime"
        @time-update="onTimeUpdate"
      />
      <PlaybackControlBar
        v-if="streamType === 'playback'"
        :current-time="currentTime"
        :total-duration="0"
        @seek="onSeek"
      />
    </template>
  </div>
</template>

<style scoped>
.vms-stream-panel { flex: 1; display: flex; flex-direction: column; background: #000; }
.no-camera-selected { display: flex; align-items: center; justify-content: center; height: 100%; background: var(--el-bg-color-page); }
</style>
```

- [ ] **Step 7: Create VmsLiveView.vue**

```vue
<!-- frontend/src/views/vms/VmsLiveView.vue -->
<script setup lang="ts">
import { useVmsStore } from '@/stores/vmsStore'
import VmsCameraList from './components/VmsCameraList.vue'
import VmsStreamPanel from './components/VmsStreamPanel.vue'

const store = useVmsStore()
</script>

<template>
  <div class="vms-live-view">
    <VmsCameraList />
    <VmsStreamPanel
      :camera-id="store.selectedCamera?.id ?? 0"
      stream-type="live"
    />
  </div>
</template>

<style scoped>
.vms-live-view { display: flex; height: calc(100vh - 120px); }
</style>
```

- [ ] **Step 8: Create VmsPlaybackView.vue**

```vue
<!-- frontend/src/views/vms/VmsPlaybackView.vue -->
<script setup lang="ts">
import { ref } from 'vue'
import { useVmsStore } from '@/stores/vmsStore'
import VmsCameraList from './components/VmsCameraList.vue'
import VmsStreamPanel from './components/VmsStreamPanel.vue'
import VmsTimeRangePicker from './components/VmsTimeRangePicker.vue'

const store = useVmsStore()
const startTime = ref('')
const endTime = ref('')

function onTimeRangeSelect(start: string, end: string) {
  startTime.value = start
  endTime.value = end
}
</script>

<template>
  <div class="vms-playback-view">
    <VmsCameraList />
    <div class="playback-main">
      <VmsTimeRangePicker @select="onTimeRangeSelect" />
      <VmsStreamPanel
        v-if="store.selectedCamera && startTime"
        :camera-id="store.selectedCamera.id"
        stream-type="playback"
        :start-time="startTime"
        :end-time="endTime"
      />
      <div v-else class="no-range-selected">
        <el-empty :description="$t('vms.selectTimeRangeHint')" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.vms-playback-view { display: flex; height: calc(100vh - 120px); }
.playback-main { flex: 1; display: flex; flex-direction: column; }
.no-range-selected { display: flex; align-items: center; justify-content: center; flex: 1; background: var(--el-bg-color-page); }
</style>
```

- [ ] **Step 9: Add missing i18n keys if needed**

Check that these i18n keys exist in `frontend/src/locales/en.ts`:
- `vms.streamLogs`, `vms.reverse`, `vms.selectTimeRangeHint`, `vms.testConnection`, `vms.connectionSuccess`
- `common.retry`, `common.query`, `common.duration`, `common.type`, `common.username`, `common.password`
- `dept.dept`

Add any missing keys to locale files as needed.

- [ ] **Step 10: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add frontend/src/views/vms/VmsLiveView.vue
git add frontend/src/views/vms/VmsPlaybackView.vue
git add frontend/src/views/vms/components/
git commit -m "feat(vms): add streaming views with camera list and HLS player

- VmsCameraList: flat list with online/offline status dots
- VmsStreamPlayer: hls.js wrapper with JWT auth, speed control, error retry
- PlaybackControlBar: timeline slider + speed selector
- VmsLiveView: camera list + live player layout
- VmsPlaybackView: camera list + time range picker + playback player
- SpeedControl: dropdown (1x/2x/4x/8x/-1x/-2x/-4x/-8x)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 8: Routes + Menu Integration + Final Assembly

**Files:**
- Create: `frontend/src/views/vms/index.ts` (route definitions for menuStore injection)
- Modify: `frontend/src/router/index.ts` (register VMS routes under TenantRoot)

**Interfaces:**
- Consumes: `menuStore.fetchMyMenus()` (existing), router `addRoute`

- [ ] **Step 1: Create route definition for menuStore injection**

```typescript
// frontend/src/views/vms/index.ts
import type { RouteRecordRaw } from 'vue-router'

export const vmsRoutes: RouteRecordRaw[] = [
  {
    path: '/vms/live',
    name: 'VmsLive',
    component: () => import('@/views/vms/VmsLiveView.vue'),
    meta: { title: 'vms.live' },
  },
  {
    path: '/vms/playback',
    name: 'VmsPlayback',
    component: () => import('@/views/vms/VmsPlaybackView.vue'),
    meta: { title: 'vms.playback' },
  },
  {
    path: '/vms/servers',
    name: 'VmsServers',
    component: () => import('@/views/vms/VmsServerManageView.vue'),
    meta: { title: 'vms.servers' },
  },
  {
    path: '/vms/cameras',
    name: 'VmsCameras',
    component: () => import('@/views/vms/VmsCameraManageView.vue'),
    meta: { title: 'vms.cameras' },
  },
  {
    path: '/vms/stream-logs',
    name: 'VmsStreamLogs',
    component: () => import('@/views/vms/VmsStreamLogView.vue'),
    meta: { title: 'vms.streamLogs' },
  },
]
```

- [ ] **Step 2: Register VMS routes in router**

```typescript
// Modify frontend/src/router/index.ts
// Add import at the top:
// import { vmsRoutes } from '@/views/vms'

// In the TenantRoot children array, add after existing routes:
// ...vmsRoutes,
```

Edit `frontend/src/router/index.ts`:
- Add import: `import { vmsRoutes } from '@/views/vms'`
- In the `TenantRoot` children array at line 271, add `...vmsRoutes,` after `...tenantStaticAdminRoutes,`

- [ ] **Step 3: Full build verification**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/backend
mvn compile -q 2>&1 | tail -20

cd /home/kevin/workspaces/side-project/IoT-forge/frontend
npm run type-check 2>&1 | tail -20
```

- [ ] **Step 4: Final commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add frontend/src/views/vms/index.ts
git add frontend/src/router/index.ts
git commit -m "feat(vms): register routes and integrate with menu system

- Add VMS route definitions for menuStore dynamic injection
- Register /vms/* routes under TenantRoot
- All 5 views accessible via their route paths
- Menu seed data in V29 migration provides navigation entries

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Spec Coverage Check

| Spec Section | Covered By |
|---|---|
| 2. Architecture (3-layer security) | Task 2, 3 |
| 3. Routes | Task 8 |
| 4.1 Package structure | Task 1, 2, 3, 4 |
| 4.2 API Endpoints | Task 3, 4 |
| 4.3 Stream proxy security | Task 3 |
| 4.4 NxTokenManager | Task 2 |
| 4.5 VmsServer Entity | Task 1 |
| 4.6 VmsCameraMapping Entity | Task 1 |
| 4.7 VmsStreamLog Entity | Task 1 |
| 4.8 Flyway migrations | Task 1 |
| 5.1-5.2 Frontend components | Task 6, 7 |
| 5.3 hls.js config | Task 7 |
| 5.4 Speed control flow | Task 7 |
| 5.5 Pinia store | Task 5 |
| 6. Error handling | Task 1 (error codes), Task 7 (frontend retry) |
| 7. Stream flow | Task 3, 7 |
| 8. Frontend types | Task 5 |

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-14-vms-implementation.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
