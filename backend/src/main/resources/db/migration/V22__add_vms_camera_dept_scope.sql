-- V22: vms_cameras 新增 dept_id 欄位，支援單位資料權限過濾 (data scope)
-- 與 devices.dept_id 相同模式，讓單位只能看見自己單位的攝影機

ALTER TABLE vms_cameras
    ADD COLUMN dept_id BIGINT;

CREATE INDEX idx_vms_cameras_dept ON vms_cameras (dept_id);

ALTER TABLE vms_cameras
    ADD CONSTRAINT fk_vms_cameras_dept
    FOREIGN KEY (dept_id) REFERENCES dept_info(dept_id) ON DELETE SET NULL;
