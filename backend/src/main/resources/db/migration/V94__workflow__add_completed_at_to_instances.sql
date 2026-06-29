-- 為 workflow_instances 新增 completed_at 欄位，記錄流程進入終態的時間點
-- 終態包含：COMPLETED（全數通過）、CANCELLED（申請人取消）、REJECTED（最終拒絕）
ALTER TABLE workflow_instances ADD COLUMN completed_at TIMESTAMP;
