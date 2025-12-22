-- 2023-01-15
ALTER TABLE platform.t_user ADD COLUMN workers varchar(255) AFTER email;
ALTER TABLE platform.t_job_flow ADD COLUMN timeout varchar(255) AFTER alerts;
ALTER TABLE platform.t_job_flow_run ADD COLUMN timeout varchar(255) AFTER alerts;
ALTER TABLE platform.t_job_flow_run ADD COLUMN config varchar(255) AFTER priority;

CREATE INDEX t_job_run_flow_run_id_idx USING BTREE ON platform.t_job_run (flow_run_id);

-- 2024-07-09
ALTER TABLE platform.t_job_flow ADD COLUMN `type` varchar(64) NOT NULL AFTER description;
ALTER TABLE platform.t_job_flow_run ADD COLUMN `type` varchar(64) NOT NULL AFTER flow_id;
UPDATE platform.t_job_flow SET `type` = 'JOB_FLOW' WHERE `type` is null;
UPDATE platform.t_job_flow_run SET `type` = 'JOB_FLOW' WHERE `type` is null;

CREATE INDEX t_job_flow_run_flow_id_idx USING BTREE ON platform.t_job_flow_run (flow_id);
CREATE INDEX t_job_run_job_id_idx USING BTREE ON platform.t_job_run (job_id);

CREATE INDEX t_job_flow_run_name_idx USING BTREE ON platform.t_job_flow_run (name);
CREATE INDEX t_job_run_name_idx USING BTREE ON platform.t_job_run (name);

-- 2024-12-04
CREATE INDEX t_job_run_stop_time_idx USING BTREE ON platform.t_job_run (stop_time);
CREATE INDEX t_job_flow_run_end_time_idx USING BTREE ON platform.t_job_flow_run (end_time);

--2025-11-18
ALTER TABLE platform.t_job CHANGE COLUMN variables params TEXT COMMENT 'params';
ALTER TABLE platform.t_job_run CHANGE COLUMN variables params TEXT COMMENT 'params';
ALTER TABLE platform.t_job_flow ADD COLUMN params TEXT AFTER `timeout`;
ALTER TABLE platform.t_job_flow_run ADD COLUMN params TEXT AFTER `timeout`;

-- 2025-12-23
CREATE INDEX t_job_flow_run_status_idx USING BTREE ON platform.t_job_flow_run (status);
CREATE INDEX t_job_run_status_idx USING BTREE ON platform.t_job_run (status);
