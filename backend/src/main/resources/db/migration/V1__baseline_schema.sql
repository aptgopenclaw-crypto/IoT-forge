--
-- PostgreSQL database dump
--


-- Dumped from database version 18.3 (Debian 18.3-1+b1)
-- Dumped by pg_dump version 18.3 (Debian 18.3-1+b1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: iot_forgedb; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS iot_forgedb;


--
-- Name: log_summary_search_trigger(); Type: FUNCTION; Schema: iot_forgedb; Owner: -
--

CREATE FUNCTION iot_forgedb.log_summary_search_trigger() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.search_vector := to_tsvector('english',
        coalesce(NEW.level, '') || ' ' ||
        coalesce(NEW.source, '') || ' ' ||
        coalesce(NEW.message, ''));
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: announcement_attachments; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.announcement_attachments (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    announcement_id bigint NOT NULL,
    file_name character varying(255) NOT NULL,
    file_size bigint NOT NULL,
    mime_type character varying(150) NOT NULL,
    file_path character varying(500) NOT NULL,
    created_by character varying(50),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: announcement_attachments_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.announcement_attachments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: announcement_attachments_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.announcement_attachments_id_seq OWNED BY iot_forgedb.announcement_attachments.id;


--
-- Name: announcement_depts; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.announcement_depts (
    announcement_id bigint NOT NULL,
    dept_id bigint NOT NULL
);


--
-- Name: announcement_reads; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.announcement_reads (
    id bigint NOT NULL,
    announcement_id bigint NOT NULL,
    user_id character varying(50) NOT NULL,
    read_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: announcement_reads_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.announcement_reads_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: announcement_reads_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.announcement_reads_id_seq OWNED BY iot_forgedb.announcement_reads.id;


--
-- Name: announcement_translations; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.announcement_translations (
    id bigint NOT NULL,
    announcement_id bigint NOT NULL,
    lang_code character varying(10) NOT NULL,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    content_text text NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: announcement_translations_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.announcement_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: announcement_translations_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.announcement_translations_id_seq OWNED BY iot_forgedb.announcement_translations.id;


--
-- Name: announcements; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.announcements (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    scope character varying(20) DEFAULT 'ALL'::character varying NOT NULL,
    pinned boolean DEFAULT false NOT NULL,
    publish_at timestamp without time zone,
    expire_at timestamp without time zone,
    created_by character varying(50),
    created_by_name character varying(100),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    content_text text,
    category character varying(20) DEFAULT 'GENERAL'::character varying NOT NULL,
    requires_ack boolean DEFAULT false NOT NULL,
    pin_order integer
);


--
-- Name: announcements_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.announcements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: announcements_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.announcements_id_seq OWNED BY iot_forgedb.announcements.id;


--
-- Name: asset_transfer_applications; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.asset_transfer_applications (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    application_no character varying(64) NOT NULL,
    applicant_id character varying(64) NOT NULL,
    applicant_name character varying(128),
    department_id bigint NOT NULL,
    department_name character varying(128),
    asset_code character varying(64) NOT NULL,
    asset_name character varying(256) NOT NULL,
    transfer_type character varying(32) NOT NULL,
    target_department_id bigint,
    reason text,
    asset_value numeric(20,2),
    workflow_instance_id bigint,
    status character varying(32) DEFAULT 'DRAFT'::character varying NOT NULL,
    current_assignee character varying(64),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64),
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by character varying(64),
    approved_at timestamp without time zone,
    approved_by character varying(64),
    reject_reason text,
    CONSTRAINT chk_asset_transfer_type CHECK (((transfer_type)::text = ANY ((ARRAY['INTERNAL'::character varying, 'EXTERNAL'::character varying, 'DISPOSAL'::character varying, 'RETURN'::character varying])::text[])))
);


--
-- Name: asset_transfer_applications_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.asset_transfer_applications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asset_transfer_applications_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.asset_transfer_applications_id_seq OWNED BY iot_forgedb.asset_transfer_applications.id;


--
-- Name: change_password_log; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.change_password_log (
    id bigint NOT NULL,
    user_id character varying(50) NOT NULL,
    change_type character varying(50) NOT NULL,
    ip_address character varying(50),
    create_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: change_password_log_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

ALTER TABLE iot_forgedb.change_password_log ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME iot_forgedb.change_password_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: circuits; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.circuits (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    panel_box_device_id bigint,
    circuit_number character varying(50) NOT NULL,
    circuit_name character varying(200),
    taipower_account character varying(50),
    usage_type character varying(50),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: circuits_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.circuits_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: circuits_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.circuits_id_seq OWNED BY iot_forgedb.circuits.id;


--
-- Name: contracts; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.contracts (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    contract_code character varying(100) NOT NULL,
    contract_name character varying(300) NOT NULL,
    budget_year integer,
    procurement_number character varying(100),
    contractor_name character varying(200),
    contractor_contact character varying(200),
    asset_category character varying(50),
    quantity integer,
    start_date date,
    end_date date,
    acceptance_date date,
    warranty_years integer,
    warranty_expiry date,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    attributes jsonb,
    created_by character varying(50),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: contracts_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.contracts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: contracts_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.contracts_id_seq OWNED BY iot_forgedb.contracts.id;


--
-- Name: delegate_settings; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.delegate_settings (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    delegate_for character varying(100) NOT NULL,
    delegate_to character varying(100) NOT NULL,
    business_type character varying(100),
    effective_from date NOT NULL,
    effective_to date NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_delegate_period CHECK ((effective_to >= effective_from))
);


--
-- Name: TABLE delegate_settings; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON TABLE iot_forgedb.delegate_settings IS '代理人設定';


--
-- Name: COLUMN delegate_settings.tenant_id; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.delegate_settings.tenant_id IS '所屬租戶 ID';


--
-- Name: COLUMN delegate_settings.delegate_for; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.delegate_settings.delegate_for IS '被代理人 user_id';


--
-- Name: COLUMN delegate_settings.delegate_to; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.delegate_settings.delegate_to IS '代理人 user_id';


--
-- Name: COLUMN delegate_settings.business_type; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.delegate_settings.business_type IS 'null 表示適用所有業務類型';


--
-- Name: delegate_settings_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.delegate_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: delegate_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.delegate_settings_id_seq OWNED BY iot_forgedb.delegate_settings.id;


--
-- Name: dept_info; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.dept_info (
    dept_id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    pid bigint,
    dept_name character varying(100) NOT NULL,
    dept_sort integer DEFAULT 0,
    status smallint DEFAULT 1,
    hierarchy_path character varying(500),
    create_by character varying(50),
    update_by character varying(50),
    create_time timestamp with time zone DEFAULT now() NOT NULL,
    update_time timestamp with time zone
);


--
-- Name: dept_info_dept_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.dept_info_dept_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dept_info_dept_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.dept_info_dept_id_seq OWNED BY iot_forgedb.dept_info.dept_id;


--
-- Name: device_events; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.device_events (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    device_id bigint NOT NULL,
    event_type character varying(30) NOT NULL,
    event_date timestamp without time zone NOT NULL,
    description text,
    attachments jsonb,
    repair_ticket_id bigint,
    replacement_item_id bigint,
    created_by character varying(50),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: device_events_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.device_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: device_events_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.device_events_id_seq OWNED BY iot_forgedb.device_events.id;


--
-- Name: device_managers; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.device_managers (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    device_id bigint NOT NULL,
    user_id character varying(50) NOT NULL,
    assigned_at timestamp without time zone DEFAULT now() NOT NULL,
    assigned_by character varying(50)
);


--
-- Name: device_managers_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.device_managers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: device_managers_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.device_managers_id_seq OWNED BY iot_forgedb.device_managers.id;


--
-- Name: device_templates; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.device_templates (
    tenant_id character varying(50) NOT NULL,
    device_type character varying(30) NOT NULL,
    schema jsonb NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_by character varying(50),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    id bigint NOT NULL
);


--
-- Name: device_templates_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.device_templates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: device_templates_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.device_templates_id_seq OWNED BY iot_forgedb.device_templates.id;


--
-- Name: devices; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.devices (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    device_type character varying(30) NOT NULL,
    device_code character varying(100) NOT NULL,
    device_name character varying(200),
    twd97_x numeric(12,3),
    twd97_y numeric(12,3),
    lng numeric(11,7),
    lat numeric(10,7),
    elevation numeric(8,3),
    twd67_x numeric(12,3),
    twd67_y numeric(12,3),
    taipower_coord character varying(100),
    dept_id bigint,
    contract_id bigint,
    property_owner character varying(200),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    installed_at date,
    decommissioned_at date,
    parent_device_id bigint,
    mount_position character varying(50),
    connectivity_type character varying(20),
    network_config jsonb,
    last_heartbeat_at timestamp without time zone,
    circuit_id bigint,
    device_token character varying(200),
    auth_type character varying(20),
    firmware_version character varying(50),
    last_telemetry_at timestamp without time zone,
    format_id bigint,
    attributes jsonb,
    created_by character varying(50),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: devices_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.devices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: devices_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.devices_id_seq OWNED BY iot_forgedb.devices.id;


--
-- Name: impersonation_session; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.impersonation_session (
    id character varying(50) NOT NULL,
    operator_user_id character varying(50) NOT NULL,
    target_tenant_id character varying(50) NOT NULL,
    reason character varying(500) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    revoked_by_user_id character varying(50),
    create_time timestamp with time zone DEFAULT now() NOT NULL,
    update_time timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_imp_session_expires_after_start CHECK ((expires_at > started_at)),
    CONSTRAINT chk_imp_session_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: TABLE impersonation_session; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON TABLE iot_forgedb.impersonation_session IS 'SUPER_ADMIN 代操 session 紀錄；對應 ADR-002 / Phase 1';


--
-- Name: COLUMN impersonation_session.reason; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.impersonation_session.reason IS '建立時必填的代操原因（稽核用）';


--
-- Name: COLUMN impersonation_session.status; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.impersonation_session.status IS 'ACTIVE=進行中；REVOKED=已主動結束；EXPIRED=逾時';


--
-- Name: COLUMN impersonation_session.revoked_by_user_id; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.impersonation_session.revoked_by_user_id IS '主動結束者 userId（通常同 operator_user_id；系統自動結束時為 NULL）';


--
-- Name: menus; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.menus (
    menu_id bigint NOT NULL,
    parent_id bigint,
    name character varying(100) NOT NULL,
    menu_type character varying(20) NOT NULL,
    route_name character varying(100),
    route_path character varying(200),
    component character varying(200),
    permission_code character varying(100),
    icon character varying(50),
    sort_order integer DEFAULT 0,
    visible boolean DEFAULT true,
    keep_alive boolean DEFAULT false,
    redirect character varying(200),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone,
    scope character varying(20) DEFAULT 'TENANT'::character varying NOT NULL,
    CONSTRAINT menus_scope_check CHECK (((scope)::text = ANY ((ARRAY['PLATFORM'::character varying, 'TENANT'::character varying, 'PUBLIC'::character varying])::text[])))
);


--
-- Name: menus_menu_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

ALTER TABLE iot_forgedb.menus ALTER COLUMN menu_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME iot_forgedb.menus_menu_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: notifications; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.notifications (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    user_id character varying(50) NOT NULL,
    type character varying(20) NOT NULL,
    title character varying(200) NOT NULL,
    content character varying(2000),
    ref_type character varying(50),
    ref_id character varying(50),
    read boolean DEFAULT false NOT NULL,
    read_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    archived_at timestamp with time zone
);


--
-- Name: notifications_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.notifications_id_seq OWNED BY iot_forgedb.notifications.id;


--
-- Name: password_history; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.password_history (
    id bigint NOT NULL,
    user_id character varying(50) NOT NULL,
    password_hash character varying(255) NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: password_history_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

ALTER TABLE iot_forgedb.password_history ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME iot_forgedb.password_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: permissions; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.permissions (
    permission_id character varying(50) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    group_name character varying(100),
    sort_order integer DEFAULT 0
);


--
-- Name: platform_announcements; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.platform_announcements (
    id bigint NOT NULL,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    content_text text,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    category character varying(20) DEFAULT 'SYSTEM'::character varying NOT NULL,
    publish_at timestamp without time zone,
    expire_at timestamp without time zone,
    created_by character varying(50),
    created_by_name character varying(100),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone
);


--
-- Name: TABLE platform_announcements; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON TABLE iot_forgedb.platform_announcements IS '平台級公告（跨場域，由 super_admin 管理）';


--
-- Name: COLUMN platform_announcements.content_text; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.platform_announcements.content_text IS '純文字版（供搜尋用）';


--
-- Name: COLUMN platform_announcements.status; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.platform_announcements.status IS 'DRAFT / PUBLISHED';


--
-- Name: COLUMN platform_announcements.category; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.platform_announcements.category IS 'SYSTEM / MAINTENANCE / GENERAL';


--
-- Name: COLUMN platform_announcements.publish_at; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.platform_announcements.publish_at IS '排程發佈時間；null 表示立即發佈';


--
-- Name: COLUMN platform_announcements.expire_at; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.platform_announcements.expire_at IS '失效時間；null 表示永不過期';


--
-- Name: platform_announcements_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

ALTER TABLE iot_forgedb.platform_announcements ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME iot_forgedb.platform_announcements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: rev_info; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.rev_info (
    id integer NOT NULL,
    "timestamp" bigint NOT NULL,
    action_user_id character varying(50)
);


--
-- Name: rev_info_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.rev_info_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rev_info_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.rev_info_id_seq OWNED BY iot_forgedb.rev_info.id;


--
-- Name: role_permissions; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.role_permissions (
    role_id character varying(50) NOT NULL,
    permission_id character varying(50) NOT NULL,
    tenant_id character varying(50)
);


--
-- Name: roles; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.roles (
    role_id character varying(50) NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    built_in boolean DEFAULT true NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    data_scope character varying(30) DEFAULT 'ALL'::character varying
);


--
-- Name: system_settings; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.system_settings (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    setting_key character varying(100) NOT NULL,
    setting_value character varying(500) NOT NULL,
    description character varying(500),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: system_settings_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.system_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.system_settings_id_seq OWNED BY iot_forgedb.system_settings.id;


--
-- Name: tenant; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.tenant (
    tenant_id character varying(50) NOT NULL,
    tenant_code character varying(50) NOT NULL,
    tenant_name character varying(200) NOT NULL,
    deployment_mode character varying(20) DEFAULT 'CLOUD'::character varying NOT NULL,
    config jsonb,
    enabled boolean DEFAULT true NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: tenant_auth_config; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.tenant_auth_config (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    auth_type character varying(20) DEFAULT 'LOCAL'::character varying NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    config_json text,
    fallback_local boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE tenant_auth_config; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON TABLE iot_forgedb.tenant_auth_config IS '租戶認證方式配置（每個租戶最多一筆）';


--
-- Name: COLUMN tenant_auth_config.auth_type; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.tenant_auth_config.auth_type IS 'LOCAL / LDAP / OIDC / SAML';


--
-- Name: COLUMN tenant_auth_config.config_json; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.tenant_auth_config.config_json IS '加密後的 provider 設定 JSON（AES-256-GCM）';


--
-- Name: COLUMN tenant_auth_config.fallback_local; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.tenant_auth_config.fallback_local IS '外部 IdP 失敗時是否允許退回本地帳密';


--
-- Name: tenant_auth_config_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.tenant_auth_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tenant_auth_config_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.tenant_auth_config_id_seq OWNED BY iot_forgedb.tenant_auth_config.id;


--
-- Name: user_event_log; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.user_event_log (
    user_event_log_pk bigint NOT NULL,
    tenant_id character varying(50),
    user_id character varying(50) NOT NULL,
    username character varying(100),
    user_label character varying(100),
    email character varying(200),
    event_type character varying(50) NOT NULL,
    event_desc character varying(50),
    api_endpoint character varying(100),
    payload character varying(2000),
    error_code character varying(50),
    message character varying(50),
    ip_address character varying(50),
    user_agent character varying(500),
    execution_time bigint,
    dept_id bigint,
    create_time timestamp with time zone DEFAULT now() NOT NULL,
    impersonated_by character varying(50),
    impersonation_session_id character varying(50)
);


--
-- Name: COLUMN user_event_log.impersonated_by; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.user_event_log.impersonated_by IS 'NULL=一般操作；NOT NULL=SUPER_ADMIN 在 tenant context 下執行，值為 SUPER_ADMIN userId';


--
-- Name: COLUMN user_event_log.impersonation_session_id; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.user_event_log.impersonation_session_id IS 'NULL=一般操作；NOT NULL=指向 impersonation_session.id，標記此筆 log 屬於哪一場代操';


--
-- Name: user_event_log_user_event_log_pk_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.user_event_log_user_event_log_pk_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_event_log_user_event_log_pk_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.user_event_log_user_event_log_pk_seq OWNED BY iot_forgedb.user_event_log.user_event_log_pk;


--
-- Name: user_info_log; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.user_info_log (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    action_type character varying(20) NOT NULL,
    action_user_id character varying(50) NOT NULL,
    target_user_id character varying(50) NOT NULL,
    email character varying(200),
    display_name character varying(200),
    role_code character varying(50),
    dept_id character varying(50),
    detail character varying(1000),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    impersonated_by character varying(50),
    impersonation_session_id character varying(50)
);


--
-- Name: COLUMN user_info_log.impersonated_by; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.user_info_log.impersonated_by IS 'NULL=一般操作；NOT NULL=SUPER_ADMIN 在 tenant context 下執行，值為 SUPER_ADMIN userId';


--
-- Name: COLUMN user_info_log.impersonation_session_id; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.user_info_log.impersonation_session_id IS 'NULL=一般操作；NOT NULL=指向 impersonation_session.id，標記此筆 log 屬於哪一場代操';


--
-- Name: user_info_log_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

ALTER TABLE iot_forgedb.user_info_log ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME iot_forgedb.user_info_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: user_reset_password_token; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.user_reset_password_token (
    token_id character varying(100) NOT NULL,
    user_id character varying(50) NOT NULL,
    token_hash character varying(64) CONSTRAINT user_reset_password_token_token_not_null NOT NULL,
    expired_at timestamp without time zone NOT NULL,
    used boolean DEFAULT false NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: user_session; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.user_session (
    session_id character varying(64) NOT NULL,
    user_id character varying(50) NOT NULL,
    tenant_id character varying(50),
    ip_address character varying(64),
    user_agent character varying(512),
    issued_at timestamp without time zone NOT NULL,
    last_seen_at timestamp without time zone NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    revoked boolean DEFAULT false NOT NULL,
    revoked_at timestamp without time zone
);


--
-- Name: user_tenant_mapping; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.user_tenant_mapping (
    id bigint NOT NULL,
    user_id character varying(50) NOT NULL,
    tenant_id character varying(50) NOT NULL,
    role_id character varying(50) NOT NULL,
    dept_id bigint,
    default_project_id character varying(50),
    enabled boolean DEFAULT true NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: user_tenant_mapping_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

ALTER TABLE iot_forgedb.user_tenant_mapping ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME iot_forgedb.user_tenant_mapping_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: users; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.users (
    user_id character varying(50) NOT NULL,
    email character varying(200) NOT NULL,
    password_hash character varying(255) NOT NULL,
    display_name character varying(200) NOT NULL,
    phone character varying(50),
    enabled boolean DEFAULT true NOT NULL,
    locked boolean DEFAULT false NOT NULL,
    locked_at timestamp without time zone,
    login_fail_count integer DEFAULT 0 NOT NULL,
    is_super_admin boolean DEFAULT false NOT NULL,
    last_login_at timestamp without time zone,
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp without time zone,
    notify_email_flag boolean DEFAULT false,
    notify_sms_flag boolean DEFAULT false,
    password_changed_at timestamp without time zone NOT NULL,
    force_change_password boolean DEFAULT false NOT NULL,
    auth_type character varying(20) DEFAULT 'LOCAL'::character varying NOT NULL,
    external_id character varying(255)
);


--
-- Name: COLUMN users.auth_type; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.users.auth_type IS '此帳號的認證來源：LOCAL / LDAP / OIDC / SAML';


--
-- Name: COLUMN users.external_id; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.users.external_id IS '外部 IdP 的唯一識別（LDAP DN / OIDC sub / SAML nameId）';


--
-- Name: work_order_logs; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.work_order_logs (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    work_order_id bigint NOT NULL,
    action character varying(30) NOT NULL,
    from_status character varying(20),
    to_status character varying(20),
    operator_id character varying(50),
    operator_name character varying(100),
    latitude numeric(10,7),
    longitude numeric(11,7),
    note text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: work_order_logs_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.work_order_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: work_order_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.work_order_logs_id_seq OWNED BY iot_forgedb.work_order_logs.id;


--
-- Name: work_orders; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.work_orders (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    device_id bigint,
    circuit_id bigint,
    contract_id bigint,
    order_type character varying(20) NOT NULL,
    source_type character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    priority character varying(10),
    reporter_name character varying(100),
    reporter_contact character varying(100),
    reported_at timestamp without time zone,
    description text,
    location_snapshot jsonb,
    assigned_to character varying(50),
    assigned_at timestamp without time zone,
    assigned_by character varying(50),
    started_at timestamp without time zone,
    start_lat numeric(10,7),
    start_lng numeric(11,7),
    completed_at timestamp without time zone,
    completion_remark text,
    fault_cause character varying(100),
    repair_cost integer,
    reviewer_id character varying(50),
    reviewed_at timestamp without time zone,
    reject_reason text,
    review_workflow_instance_id bigint,
    closed_at timestamp without time zone,
    closed_by character varying(50),
    auto_reported_at timestamp without time zone,
    attachments jsonb,
    created_by character varying(50),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: work_orders_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.work_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: work_orders_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.work_orders_id_seq OWNED BY iot_forgedb.work_orders.id;


--
-- Name: workflow_definitions; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.workflow_definitions (
    id bigint NOT NULL,
    code character varying(100) NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    name character varying(200) NOT NULL,
    steps_json jsonb NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    tenant_id character varying(50) NOT NULL
);


--
-- Name: TABLE workflow_definitions; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON TABLE iot_forgedb.workflow_definitions IS '流程定義';


--
-- Name: COLUMN workflow_definitions.code; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.workflow_definitions.code IS '流程代碼，如 asset_transfer';


--
-- Name: COLUMN workflow_definitions.steps_json; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.workflow_definitions.steps_json IS 'JSON 格式的步驟定義';


--
-- Name: COLUMN workflow_definitions.tenant_id; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.workflow_definitions.tenant_id IS '所屬租戶 ID';


--
-- Name: workflow_definitions_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.workflow_definitions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: workflow_definitions_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.workflow_definitions_id_seq OWNED BY iot_forgedb.workflow_definitions.id;


--
-- Name: workflow_instances; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.workflow_instances (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    workflow_def_id bigint NOT NULL,
    business_id character varying(100) NOT NULL,
    business_type character varying(100) NOT NULL,
    current_step_id character varying(100),
    status character varying(50) DEFAULT 'IN_PROGRESS'::character varying NOT NULL,
    context_json jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    completed_at timestamp without time zone,
    CONSTRAINT chk_instance_status CHECK (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: TABLE workflow_instances; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON TABLE iot_forgedb.workflow_instances IS '流程執行實例';


--
-- Name: COLUMN workflow_instances.tenant_id; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.workflow_instances.tenant_id IS '所屬租戶 ID';


--
-- Name: COLUMN workflow_instances.current_step_id; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.workflow_instances.current_step_id IS '當前步驟 ID，對應 steps_json 中的 step.id';


--
-- Name: COLUMN workflow_instances.status; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.workflow_instances.status IS 'IN_PROGRESS | COMPLETED | REJECTED';


--
-- Name: COLUMN workflow_instances.context_json; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.workflow_instances.context_json IS '業務上下文（供 AssigneeResolver 使用）';


--
-- Name: workflow_instances_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.workflow_instances_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: workflow_instances_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.workflow_instances_id_seq OWNED BY iot_forgedb.workflow_instances.id;


--
-- Name: workflow_step_logs; Type: TABLE; Schema: iot_forgedb; Owner: -
--

CREATE TABLE iot_forgedb.workflow_step_logs (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    workflow_instance_id bigint NOT NULL,
    step_id character varying(100) NOT NULL,
    step_name character varying(200) NOT NULL,
    assignee_user_id character varying(100),
    action character varying(50),
    comment character varying(2000),
    target_step_id character varying(100),
    entered_at timestamp without time zone DEFAULT now() NOT NULL,
    completed_at timestamp without time zone,
    CONSTRAINT chk_step_log_action CHECK (((action)::text = ANY ((ARRAY['APPROVE'::character varying, 'REJECT'::character varying, 'RESUBMIT'::character varying, 'CANCEL'::character varying])::text[]))),
    CONSTRAINT chk_step_log_comment_length CHECK ((length((comment)::text) <= 2000))
);


--
-- Name: TABLE workflow_step_logs; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON TABLE iot_forgedb.workflow_step_logs IS '步驟執行歷程';


--
-- Name: COLUMN workflow_step_logs.tenant_id; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.workflow_step_logs.tenant_id IS '所屬租戶 ID（冗餘，避免跨表 JOIN 查詢歷程）';


--
-- Name: COLUMN workflow_step_logs.assignee_user_id; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.workflow_step_logs.assignee_user_id IS '審核人（已套用代理人覆寫後的最終人員）';


--
-- Name: COLUMN workflow_step_logs.action; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.workflow_step_logs.action IS 'approve | reject | resubmit | null（進行中）';


--
-- Name: COLUMN workflow_step_logs.target_step_id; Type: COMMENT; Schema: iot_forgedb; Owner: -
--

COMMENT ON COLUMN iot_forgedb.workflow_step_logs.target_step_id IS 'reject 時的退回目標步驟 ID';


--
-- Name: workflow_step_logs_id_seq; Type: SEQUENCE; Schema: iot_forgedb; Owner: -
--

CREATE SEQUENCE iot_forgedb.workflow_step_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: workflow_step_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: iot_forgedb; Owner: -
--

ALTER SEQUENCE iot_forgedb.workflow_step_logs_id_seq OWNED BY iot_forgedb.workflow_step_logs.id;


--
-- Name: announcement_attachments id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_attachments ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.announcement_attachments_id_seq'::regclass);


--
-- Name: announcement_reads id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_reads ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.announcement_reads_id_seq'::regclass);


--
-- Name: announcement_translations id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_translations ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.announcement_translations_id_seq'::regclass);


--
-- Name: announcements id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcements ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.announcements_id_seq'::regclass);


--
-- Name: asset_transfer_applications id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.asset_transfer_applications ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.asset_transfer_applications_id_seq'::regclass);


--
-- Name: circuits id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.circuits ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.circuits_id_seq'::regclass);


--
-- Name: contracts id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.contracts ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.contracts_id_seq'::regclass);


--
-- Name: delegate_settings id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.delegate_settings ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.delegate_settings_id_seq'::regclass);


--
-- Name: dept_info dept_id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.dept_info ALTER COLUMN dept_id SET DEFAULT nextval('iot_forgedb.dept_info_dept_id_seq'::regclass);


--
-- Name: device_events id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.device_events ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.device_events_id_seq'::regclass);


--
-- Name: device_managers id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.device_managers ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.device_managers_id_seq'::regclass);


--
-- Name: device_templates id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.device_templates ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.device_templates_id_seq'::regclass);


--
-- Name: devices id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.devices ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.devices_id_seq'::regclass);


--
-- Name: notifications id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.notifications ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.notifications_id_seq'::regclass);


--
-- Name: rev_info id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.rev_info ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.rev_info_id_seq'::regclass);


--
-- Name: system_settings id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.system_settings ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.system_settings_id_seq'::regclass);


--
-- Name: tenant_auth_config id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.tenant_auth_config ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.tenant_auth_config_id_seq'::regclass);


--
-- Name: user_event_log user_event_log_pk; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_event_log ALTER COLUMN user_event_log_pk SET DEFAULT nextval('iot_forgedb.user_event_log_user_event_log_pk_seq'::regclass);


--
-- Name: work_order_logs id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.work_order_logs ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.work_order_logs_id_seq'::regclass);


--
-- Name: work_orders id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.work_orders ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.work_orders_id_seq'::regclass);


--
-- Name: workflow_definitions id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.workflow_definitions ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.workflow_definitions_id_seq'::regclass);


--
-- Name: workflow_instances id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.workflow_instances ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.workflow_instances_id_seq'::regclass);


--
-- Name: workflow_step_logs id; Type: DEFAULT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.workflow_step_logs ALTER COLUMN id SET DEFAULT nextval('iot_forgedb.workflow_step_logs_id_seq'::regclass);


--
-- Name: announcement_attachments announcement_attachments_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_attachments
    ADD CONSTRAINT announcement_attachments_pkey PRIMARY KEY (id);


--
-- Name: announcement_depts announcement_depts_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_depts
    ADD CONSTRAINT announcement_depts_pkey PRIMARY KEY (announcement_id, dept_id);


--
-- Name: announcement_reads announcement_reads_announcement_id_user_id_key; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_reads
    ADD CONSTRAINT announcement_reads_announcement_id_user_id_key UNIQUE (announcement_id, user_id);


--
-- Name: announcement_reads announcement_reads_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_reads
    ADD CONSTRAINT announcement_reads_pkey PRIMARY KEY (id);


--
-- Name: announcement_translations announcement_translations_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_translations
    ADD CONSTRAINT announcement_translations_pkey PRIMARY KEY (id);


--
-- Name: announcements announcements_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcements
    ADD CONSTRAINT announcements_pkey PRIMARY KEY (id);


--
-- Name: asset_transfer_applications asset_transfer_applications_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.asset_transfer_applications
    ADD CONSTRAINT asset_transfer_applications_pkey PRIMARY KEY (id);


--
-- Name: change_password_log change_password_log_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.change_password_log
    ADD CONSTRAINT change_password_log_pkey PRIMARY KEY (id);


--
-- Name: circuits circuits_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.circuits
    ADD CONSTRAINT circuits_pkey PRIMARY KEY (id);


--
-- Name: contracts contracts_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.contracts
    ADD CONSTRAINT contracts_pkey PRIMARY KEY (id);


--
-- Name: delegate_settings delegate_settings_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.delegate_settings
    ADD CONSTRAINT delegate_settings_pkey PRIMARY KEY (id);


--
-- Name: dept_info dept_info_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.dept_info
    ADD CONSTRAINT dept_info_pkey PRIMARY KEY (dept_id);


--
-- Name: dept_info dept_info_tenant_id_dept_name_pid_key; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.dept_info
    ADD CONSTRAINT dept_info_tenant_id_dept_name_pid_key UNIQUE (tenant_id, dept_name, pid);


--
-- Name: device_events device_events_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.device_events
    ADD CONSTRAINT device_events_pkey PRIMARY KEY (id);


--
-- Name: device_managers device_managers_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.device_managers
    ADD CONSTRAINT device_managers_pkey PRIMARY KEY (id);


--
-- Name: device_templates device_templates_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.device_templates
    ADD CONSTRAINT device_templates_pkey PRIMARY KEY (id);


--
-- Name: device_templates device_templates_tenant_id_device_type_key; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.device_templates
    ADD CONSTRAINT device_templates_tenant_id_device_type_key UNIQUE (tenant_id, device_type);


--
-- Name: devices devices_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.devices
    ADD CONSTRAINT devices_pkey PRIMARY KEY (id);


--
-- Name: impersonation_session impersonation_session_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.impersonation_session
    ADD CONSTRAINT impersonation_session_pkey PRIMARY KEY (id);


--
-- Name: menus menus_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.menus
    ADD CONSTRAINT menus_pkey PRIMARY KEY (menu_id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: password_history password_history_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.password_history
    ADD CONSTRAINT password_history_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_code_key; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.permissions
    ADD CONSTRAINT permissions_code_key UNIQUE (code);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (permission_id);


--
-- Name: platform_announcements platform_announcements_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.platform_announcements
    ADD CONSTRAINT platform_announcements_pkey PRIMARY KEY (id);


--
-- Name: rev_info rev_info_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.rev_info
    ADD CONSTRAINT rev_info_pkey PRIMARY KEY (id);


--
-- Name: role_permissions role_permissions_role_id_permission_id_tenant_id_key; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.role_permissions
    ADD CONSTRAINT role_permissions_role_id_permission_id_tenant_id_key UNIQUE (role_id, permission_id, tenant_id);


--
-- Name: roles roles_code_key; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.roles
    ADD CONSTRAINT roles_code_key UNIQUE (code);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);


--
-- Name: system_settings system_settings_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.system_settings
    ADD CONSTRAINT system_settings_pkey PRIMARY KEY (id);


--
-- Name: system_settings system_settings_tenant_id_setting_key_key; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.system_settings
    ADD CONSTRAINT system_settings_tenant_id_setting_key_key UNIQUE (tenant_id, setting_key);


--
-- Name: tenant_auth_config tenant_auth_config_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.tenant_auth_config
    ADD CONSTRAINT tenant_auth_config_pkey PRIMARY KEY (id);


--
-- Name: tenant_auth_config tenant_auth_config_tenant_id_key; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.tenant_auth_config
    ADD CONSTRAINT tenant_auth_config_tenant_id_key UNIQUE (tenant_id);


--
-- Name: tenant tenant_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.tenant
    ADD CONSTRAINT tenant_pkey PRIMARY KEY (tenant_id);


--
-- Name: tenant tenant_tenant_code_key; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.tenant
    ADD CONSTRAINT tenant_tenant_code_key UNIQUE (tenant_code);


--
-- Name: announcement_translations uq_announcement_translation; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_translations
    ADD CONSTRAINT uq_announcement_translation UNIQUE (announcement_id, lang_code);


--
-- Name: asset_transfer_applications uq_asset_transfer_tenant_appno; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.asset_transfer_applications
    ADD CONSTRAINT uq_asset_transfer_tenant_appno UNIQUE (tenant_id, application_no);


--
-- Name: workflow_instances uq_workflow_instance_business_tenant; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.workflow_instances
    ADD CONSTRAINT uq_workflow_instance_business_tenant UNIQUE (tenant_id, business_type, business_id);


--
-- Name: user_event_log user_event_log_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_event_log
    ADD CONSTRAINT user_event_log_pkey PRIMARY KEY (user_event_log_pk);


--
-- Name: user_info_log user_info_log_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_info_log
    ADD CONSTRAINT user_info_log_pkey PRIMARY KEY (id);


--
-- Name: user_reset_password_token user_reset_password_token_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_reset_password_token
    ADD CONSTRAINT user_reset_password_token_pkey PRIMARY KEY (token_id);


--
-- Name: user_reset_password_token user_reset_password_token_token_hash_key; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_reset_password_token
    ADD CONSTRAINT user_reset_password_token_token_hash_key UNIQUE (token_hash);


--
-- Name: user_session user_session_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_session
    ADD CONSTRAINT user_session_pkey PRIMARY KEY (session_id);


--
-- Name: user_tenant_mapping user_tenant_mapping_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_tenant_mapping
    ADD CONSTRAINT user_tenant_mapping_pkey PRIMARY KEY (id);


--
-- Name: user_tenant_mapping user_tenant_mapping_user_id_tenant_id_key; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_tenant_mapping
    ADD CONSTRAINT user_tenant_mapping_user_id_tenant_id_key UNIQUE (user_id, tenant_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
-- Name: work_order_logs work_order_logs_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.work_order_logs
    ADD CONSTRAINT work_order_logs_pkey PRIMARY KEY (id);


--
-- Name: work_orders work_orders_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.work_orders
    ADD CONSTRAINT work_orders_pkey PRIMARY KEY (id);


--
-- Name: workflow_definitions workflow_definitions_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.workflow_definitions
    ADD CONSTRAINT workflow_definitions_pkey PRIMARY KEY (id);


--
-- Name: workflow_instances workflow_instances_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.workflow_instances
    ADD CONSTRAINT workflow_instances_pkey PRIMARY KEY (id);


--
-- Name: workflow_step_logs workflow_step_logs_pkey; Type: CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.workflow_step_logs
    ADD CONSTRAINT workflow_step_logs_pkey PRIMARY KEY (id);


--
-- Name: idx_announcement_attachments_announcement; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_announcement_attachments_announcement ON iot_forgedb.announcement_attachments USING btree (announcement_id);


--
-- Name: idx_announcement_attachments_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_announcement_attachments_tenant ON iot_forgedb.announcement_attachments USING btree (tenant_id);


--
-- Name: idx_announcement_depts_dept; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_announcement_depts_dept ON iot_forgedb.announcement_depts USING btree (dept_id);


--
-- Name: idx_announcement_reads_user; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_announcement_reads_user ON iot_forgedb.announcement_reads USING btree (user_id);


--
-- Name: idx_announcement_translations_ann; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_announcement_translations_ann ON iot_forgedb.announcement_translations USING btree (announcement_id);


--
-- Name: idx_announcements_category_publish; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_announcements_category_publish ON iot_forgedb.announcements USING btree (category, publish_at DESC) WHERE ((status)::text = 'PUBLISHED'::text);


--
-- Name: idx_announcements_pin_order; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_announcements_pin_order ON iot_forgedb.announcements USING btree (tenant_id, pin_order) WHERE ((pinned = true) AND (pin_order IS NOT NULL));


--
-- Name: idx_announcements_published_active; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_announcements_published_active ON iot_forgedb.announcements USING btree (tenant_id, scope, publish_at, expire_at) WHERE ((status)::text = 'PUBLISHED'::text);


--
-- Name: idx_announcements_requires_ack; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_announcements_requires_ack ON iot_forgedb.announcements USING btree (tenant_id, publish_at DESC) WHERE ((requires_ack = true) AND ((status)::text = 'PUBLISHED'::text));


--
-- Name: idx_announcements_status; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_announcements_status ON iot_forgedb.announcements USING btree (tenant_id, status, publish_at DESC);


--
-- Name: idx_announcements_tenant_id; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_announcements_tenant_id ON iot_forgedb.announcements USING btree (tenant_id);


--
-- Name: idx_asset_transfer_tenant_applicant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_asset_transfer_tenant_applicant ON iot_forgedb.asset_transfer_applications USING btree (tenant_id, applicant_id);


--
-- Name: idx_asset_transfer_tenant_status; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_asset_transfer_tenant_status ON iot_forgedb.asset_transfer_applications USING btree (tenant_id, status);


--
-- Name: idx_asset_transfer_workflow; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_asset_transfer_workflow ON iot_forgedb.asset_transfer_applications USING btree (workflow_instance_id);


--
-- Name: idx_circuits_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_circuits_tenant ON iot_forgedb.circuits USING btree (tenant_id);


--
-- Name: idx_contracts_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_contracts_tenant ON iot_forgedb.contracts USING btree (tenant_id);


--
-- Name: idx_delegate_settings_lookup; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_delegate_settings_lookup ON iot_forgedb.delegate_settings USING btree (tenant_id, delegate_for, effective_from, effective_to);


--
-- Name: idx_dept_hierarchy_path; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_dept_hierarchy_path ON iot_forgedb.dept_info USING btree (hierarchy_path text_pattern_ops) WHERE (status = 1);


--
-- Name: idx_dept_pid; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_dept_pid ON iot_forgedb.dept_info USING btree (pid);


--
-- Name: idx_dept_tenant_id; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_dept_tenant_id ON iot_forgedb.dept_info USING btree (tenant_id);


--
-- Name: idx_device_events_device; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_device_events_device ON iot_forgedb.device_events USING btree (device_id);


--
-- Name: idx_device_events_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_device_events_tenant ON iot_forgedb.device_events USING btree (tenant_id);


--
-- Name: idx_device_managers_device; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_device_managers_device ON iot_forgedb.device_managers USING btree (device_id);


--
-- Name: idx_device_managers_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_device_managers_tenant ON iot_forgedb.device_managers USING btree (tenant_id);


--
-- Name: idx_device_managers_user; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_device_managers_user ON iot_forgedb.device_managers USING btree (user_id);


--
-- Name: idx_device_templates_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_device_templates_tenant ON iot_forgedb.device_templates USING btree (tenant_id);


--
-- Name: idx_devices_dept; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_devices_dept ON iot_forgedb.devices USING btree (dept_id);


--
-- Name: idx_devices_parent; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_devices_parent ON iot_forgedb.devices USING btree (parent_device_id);


--
-- Name: idx_devices_status; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_devices_status ON iot_forgedb.devices USING btree (status);


--
-- Name: idx_devices_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_devices_tenant ON iot_forgedb.devices USING btree (tenant_id);


--
-- Name: idx_devices_token; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE UNIQUE INDEX idx_devices_token ON iot_forgedb.devices USING btree (device_token) WHERE (device_token IS NOT NULL);


--
-- Name: idx_devices_type; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_devices_type ON iot_forgedb.devices USING btree (device_type);


--
-- Name: idx_imp_session_active; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_imp_session_active ON iot_forgedb.impersonation_session USING btree (operator_user_id, target_tenant_id, expires_at) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: idx_imp_session_operator; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_imp_session_operator ON iot_forgedb.impersonation_session USING btree (operator_user_id);


--
-- Name: idx_imp_session_status; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_imp_session_status ON iot_forgedb.impersonation_session USING btree (status);


--
-- Name: idx_imp_session_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_imp_session_tenant ON iot_forgedb.impersonation_session USING btree (target_tenant_id);


--
-- Name: idx_menus_parent; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_menus_parent ON iot_forgedb.menus USING btree (parent_id);


--
-- Name: idx_menus_permission; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_menus_permission ON iot_forgedb.menus USING btree (permission_code);


--
-- Name: idx_menus_scope; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_menus_scope ON iot_forgedb.menus USING btree (scope);


--
-- Name: idx_notifications_active; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_notifications_active ON iot_forgedb.notifications USING btree (user_id, read, created_at DESC) WHERE (archived_at IS NULL);


--
-- Name: idx_notifications_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_notifications_tenant ON iot_forgedb.notifications USING btree (tenant_id);


--
-- Name: idx_notifications_user_read; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_notifications_user_read ON iot_forgedb.notifications USING btree (user_id, read, created_at DESC);


--
-- Name: idx_password_history_user; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_password_history_user ON iot_forgedb.password_history USING btree (user_id);


--
-- Name: idx_step_logs_assignee_pending; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_step_logs_assignee_pending ON iot_forgedb.workflow_step_logs USING btree (tenant_id, assignee_user_id) WHERE (completed_at IS NULL);


--
-- Name: idx_system_settings_tenant_id; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_system_settings_tenant_id ON iot_forgedb.system_settings USING btree (tenant_id);


--
-- Name: idx_tenant_auth_config_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_tenant_auth_config_tenant ON iot_forgedb.tenant_auth_config USING btree (tenant_id);


--
-- Name: idx_uel_create_time; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_uel_create_time ON iot_forgedb.user_event_log USING btree (create_time);


--
-- Name: idx_uel_event_type; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_uel_event_type ON iot_forgedb.user_event_log USING btree (event_type);


--
-- Name: idx_uel_tenant_id; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_uel_tenant_id ON iot_forgedb.user_event_log USING btree (tenant_id);


--
-- Name: idx_uel_user_id; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_uel_user_id ON iot_forgedb.user_event_log USING btree (user_id);


--
-- Name: idx_user_event_log_imp_session; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_user_event_log_imp_session ON iot_forgedb.user_event_log USING btree (impersonation_session_id) WHERE (impersonation_session_id IS NOT NULL);


--
-- Name: idx_user_event_log_impersonated; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_user_event_log_impersonated ON iot_forgedb.user_event_log USING btree (tenant_id, impersonated_by) WHERE (impersonated_by IS NOT NULL);


--
-- Name: idx_user_info_log_imp_session; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_user_info_log_imp_session ON iot_forgedb.user_info_log USING btree (impersonation_session_id) WHERE (impersonation_session_id IS NOT NULL);


--
-- Name: idx_user_info_log_impersonated; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_user_info_log_impersonated ON iot_forgedb.user_info_log USING btree (tenant_id, impersonated_by) WHERE (impersonated_by IS NOT NULL);


--
-- Name: idx_user_info_log_target; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_user_info_log_target ON iot_forgedb.user_info_log USING btree (target_user_id);


--
-- Name: idx_user_info_log_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_user_info_log_tenant ON iot_forgedb.user_info_log USING btree (tenant_id);


--
-- Name: idx_user_session_expires_at; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_user_session_expires_at ON iot_forgedb.user_session USING btree (expires_at);


--
-- Name: idx_user_session_user_active; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_user_session_user_active ON iot_forgedb.user_session USING btree (user_id, revoked, last_seen_at DESC);


--
-- Name: idx_users_email; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_users_email ON iot_forgedb.users USING btree (email);


--
-- Name: idx_users_external_id; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_users_external_id ON iot_forgedb.users USING btree (external_id) WHERE (external_id IS NOT NULL);


--
-- Name: idx_users_password_changed_at; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_users_password_changed_at ON iot_forgedb.users USING btree (password_changed_at);


--
-- Name: idx_utm_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_utm_tenant ON iot_forgedb.user_tenant_mapping USING btree (tenant_id);


--
-- Name: idx_utm_user; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_utm_user ON iot_forgedb.user_tenant_mapping USING btree (user_id);


--
-- Name: idx_work_order_logs_order; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_work_order_logs_order ON iot_forgedb.work_order_logs USING btree (work_order_id);


--
-- Name: idx_work_order_logs_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_work_order_logs_tenant ON iot_forgedb.work_order_logs USING btree (tenant_id);


--
-- Name: idx_work_orders_device; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_work_orders_device ON iot_forgedb.work_orders USING btree (device_id);


--
-- Name: idx_work_orders_source; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_work_orders_source ON iot_forgedb.work_orders USING btree (source_type);


--
-- Name: idx_work_orders_status; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_work_orders_status ON iot_forgedb.work_orders USING btree (status, created_at);


--
-- Name: idx_work_orders_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_work_orders_tenant ON iot_forgedb.work_orders USING btree (tenant_id);


--
-- Name: idx_workflow_def_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_workflow_def_tenant ON iot_forgedb.workflow_definitions USING btree (tenant_id);


--
-- Name: idx_workflow_def_unique_enabled_per_code; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE UNIQUE INDEX idx_workflow_def_unique_enabled_per_code ON iot_forgedb.workflow_definitions USING btree (tenant_id, code) WHERE (enabled = true);


--
-- Name: idx_workflow_instance_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_workflow_instance_tenant ON iot_forgedb.workflow_instances USING btree (tenant_id);


--
-- Name: idx_workflow_step_log_tenant; Type: INDEX; Schema: iot_forgedb; Owner: -
--

CREATE INDEX idx_workflow_step_log_tenant ON iot_forgedb.workflow_step_logs USING btree (tenant_id);


--
-- Name: announcement_depts announcement_depts_announcement_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_depts
    ADD CONSTRAINT announcement_depts_announcement_id_fkey FOREIGN KEY (announcement_id) REFERENCES iot_forgedb.announcements(id) ON DELETE CASCADE;


--
-- Name: announcement_depts announcement_depts_dept_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_depts
    ADD CONSTRAINT announcement_depts_dept_id_fkey FOREIGN KEY (dept_id) REFERENCES iot_forgedb.dept_info(dept_id);


--
-- Name: announcement_reads announcement_reads_announcement_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_reads
    ADD CONSTRAINT announcement_reads_announcement_id_fkey FOREIGN KEY (announcement_id) REFERENCES iot_forgedb.announcements(id) ON DELETE CASCADE;


--
-- Name: announcement_translations announcement_translations_announcement_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_translations
    ADD CONSTRAINT announcement_translations_announcement_id_fkey FOREIGN KEY (announcement_id) REFERENCES iot_forgedb.announcements(id) ON DELETE CASCADE;


--
-- Name: dept_info dept_info_pid_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.dept_info
    ADD CONSTRAINT dept_info_pid_fkey FOREIGN KEY (pid) REFERENCES iot_forgedb.dept_info(dept_id);


--
-- Name: announcement_attachments fk_announcement_attachments_announcement; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.announcement_attachments
    ADD CONSTRAINT fk_announcement_attachments_announcement FOREIGN KEY (announcement_id) REFERENCES iot_forgedb.announcements(id) ON DELETE CASCADE;


--
-- Name: circuits fk_circuits_panel_box_device; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.circuits
    ADD CONSTRAINT fk_circuits_panel_box_device FOREIGN KEY (panel_box_device_id) REFERENCES iot_forgedb.devices(id) ON DELETE SET NULL;


--
-- Name: device_events fk_device_events_device; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.device_events
    ADD CONSTRAINT fk_device_events_device FOREIGN KEY (device_id) REFERENCES iot_forgedb.devices(id) ON DELETE CASCADE;


--
-- Name: device_events fk_device_events_repair_ticket; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.device_events
    ADD CONSTRAINT fk_device_events_repair_ticket FOREIGN KEY (repair_ticket_id) REFERENCES iot_forgedb.work_orders(id) ON DELETE SET NULL;


--
-- Name: device_managers fk_device_managers_device; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.device_managers
    ADD CONSTRAINT fk_device_managers_device FOREIGN KEY (device_id) REFERENCES iot_forgedb.devices(id) ON DELETE CASCADE;


--
-- Name: devices fk_devices_circuit; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.devices
    ADD CONSTRAINT fk_devices_circuit FOREIGN KEY (circuit_id) REFERENCES iot_forgedb.circuits(id) ON DELETE SET NULL;


--
-- Name: devices fk_devices_contract; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.devices
    ADD CONSTRAINT fk_devices_contract FOREIGN KEY (contract_id) REFERENCES iot_forgedb.contracts(id) ON DELETE SET NULL;


--
-- Name: devices fk_devices_dept; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.devices
    ADD CONSTRAINT fk_devices_dept FOREIGN KEY (dept_id) REFERENCES iot_forgedb.dept_info(dept_id) ON DELETE SET NULL;


--
-- Name: devices fk_devices_parent; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.devices
    ADD CONSTRAINT fk_devices_parent FOREIGN KEY (parent_device_id) REFERENCES iot_forgedb.devices(id) ON DELETE SET NULL;


--
-- Name: tenant_auth_config fk_tenant_auth_config_tenant; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.tenant_auth_config
    ADD CONSTRAINT fk_tenant_auth_config_tenant FOREIGN KEY (tenant_id) REFERENCES iot_forgedb.tenant(tenant_id);


--
-- Name: user_tenant_mapping fk_utm_dept_info; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_tenant_mapping
    ADD CONSTRAINT fk_utm_dept_info FOREIGN KEY (dept_id) REFERENCES iot_forgedb.dept_info(dept_id);


--
-- Name: work_order_logs fk_work_order_logs_work_order; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.work_order_logs
    ADD CONSTRAINT fk_work_order_logs_work_order FOREIGN KEY (work_order_id) REFERENCES iot_forgedb.work_orders(id) ON DELETE CASCADE;


--
-- Name: work_orders fk_work_orders_circuit; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.work_orders
    ADD CONSTRAINT fk_work_orders_circuit FOREIGN KEY (circuit_id) REFERENCES iot_forgedb.circuits(id) ON DELETE SET NULL;


--
-- Name: work_orders fk_work_orders_contract; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.work_orders
    ADD CONSTRAINT fk_work_orders_contract FOREIGN KEY (contract_id) REFERENCES iot_forgedb.contracts(id) ON DELETE SET NULL;


--
-- Name: work_orders fk_work_orders_device; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.work_orders
    ADD CONSTRAINT fk_work_orders_device FOREIGN KEY (device_id) REFERENCES iot_forgedb.devices(id) ON DELETE SET NULL;


--
-- Name: work_orders fk_work_orders_workflow_instance; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.work_orders
    ADD CONSTRAINT fk_work_orders_workflow_instance FOREIGN KEY (review_workflow_instance_id) REFERENCES iot_forgedb.workflow_instances(id) ON DELETE SET NULL;


--
-- Name: impersonation_session impersonation_session_operator_user_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.impersonation_session
    ADD CONSTRAINT impersonation_session_operator_user_id_fkey FOREIGN KEY (operator_user_id) REFERENCES iot_forgedb.users(user_id);


--
-- Name: impersonation_session impersonation_session_revoked_by_user_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.impersonation_session
    ADD CONSTRAINT impersonation_session_revoked_by_user_id_fkey FOREIGN KEY (revoked_by_user_id) REFERENCES iot_forgedb.users(user_id);


--
-- Name: impersonation_session impersonation_session_target_tenant_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.impersonation_session
    ADD CONSTRAINT impersonation_session_target_tenant_id_fkey FOREIGN KEY (target_tenant_id) REFERENCES iot_forgedb.tenant(tenant_id);


--
-- Name: role_permissions role_permissions_permission_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.role_permissions
    ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES iot_forgedb.permissions(permission_id);


--
-- Name: role_permissions role_permissions_role_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES iot_forgedb.roles(role_id);


--
-- Name: role_permissions role_permissions_tenant_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.role_permissions
    ADD CONSTRAINT role_permissions_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES iot_forgedb.tenant(tenant_id);


--
-- Name: system_settings system_settings_tenant_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.system_settings
    ADD CONSTRAINT system_settings_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES iot_forgedb.tenant(tenant_id);


--
-- Name: user_reset_password_token user_reset_password_token_user_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_reset_password_token
    ADD CONSTRAINT user_reset_password_token_user_id_fkey FOREIGN KEY (user_id) REFERENCES iot_forgedb.users(user_id);


--
-- Name: user_tenant_mapping user_tenant_mapping_role_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_tenant_mapping
    ADD CONSTRAINT user_tenant_mapping_role_id_fkey FOREIGN KEY (role_id) REFERENCES iot_forgedb.roles(role_id);


--
-- Name: user_tenant_mapping user_tenant_mapping_tenant_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_tenant_mapping
    ADD CONSTRAINT user_tenant_mapping_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES iot_forgedb.tenant(tenant_id);


--
-- Name: user_tenant_mapping user_tenant_mapping_user_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.user_tenant_mapping
    ADD CONSTRAINT user_tenant_mapping_user_id_fkey FOREIGN KEY (user_id) REFERENCES iot_forgedb.users(user_id);


--
-- Name: workflow_instances workflow_instances_workflow_def_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.workflow_instances
    ADD CONSTRAINT workflow_instances_workflow_def_id_fkey FOREIGN KEY (workflow_def_id) REFERENCES iot_forgedb.workflow_definitions(id);


--
-- Name: workflow_step_logs workflow_step_logs_workflow_instance_id_fkey; Type: FK CONSTRAINT; Schema: iot_forgedb; Owner: -
--

ALTER TABLE ONLY iot_forgedb.workflow_step_logs
    ADD CONSTRAINT workflow_step_logs_workflow_instance_id_fkey FOREIGN KEY (workflow_instance_id) REFERENCES iot_forgedb.workflow_instances(id);


--
-- PostgreSQL database dump complete
--


