-- 2023-12-24
ALTER TABLE platform.t_user ADD COLUMN workers varchar(255) AFTER email;
ALTER TABLE platform.t_job_flow_run ADD COLUMN config varchar(255) AFTER priority;

