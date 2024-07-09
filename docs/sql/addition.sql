-- 2023-01-15
ALTER TABLE platform.t_user ADD COLUMN workers varchar(255) AFTER email;
ALTER TABLE platform.t_job_flow ADD COLUMN timeout varchar(255) AFTER alerts;
ALTER TABLE platform.t_job_flow_run ADD COLUMN timeout varchar(255) AFTER alerts;
ALTER TABLE platform.t_job_flow_run ADD COLUMN config varchar(255) AFTER priority;

CREATE INDEX t_job_run_flow_run_id_idx USING BTREE ON platform.t_job_run (flow_run_id);

-- 2024-07-09
ALTER TABLE platform.t_job_flow ADD COLUMN `type` varchar(64) AFTER description;
ALTER TABLE platform.t_job_flow_run ADD COLUMN `type` varchar(64) AFTER flow_id;
