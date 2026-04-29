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
CREATE TABLE `shedlock` (
  `name` varchar(64) NOT NULL,
  `lock_until` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `locked_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `locked_by` varchar(255) NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='shedlock';

-- 2026-01-30
UPDATE t_job 
SET `subject` = REPLACE(`subject`, '${resource', '${resource:local')
WHERE `subject` LIKE '%${resource%';

UPDATE t_job 
SET `subject` = REPLACE(`subject`, '${setValue', '${setParam')
WHERE `subject` LIKE '%${setValue%';

UPDATE t_job 
SET `config` = REPLACE(`config`, 'inheritParamMode', 'paramTransferMode')
WHERE `config` LIKE '%inheritParamMode%';

UPDATE t_job_run SET status = 'FAILURE' WHERE status in ('NOT_EXIST', 'ABNORMAL');
UPDATE t_job_flow_run SET status = 'FAILURE' WHERE status in ('NOT_EXIST', 'ABNORMAL');

-- 2026-03-24
CREATE TABLE `t_config` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `type` varchar(32) NOT NULL,
  `version` varchar(32) NOT NULL,
  `config` varchar(4096) DEFAULT NULL,
  `status` varchar(32)  NOT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='config';

-- 2026-04-08
CREATE TABLE `t_audit_log` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `entity_id` bigint(11) NOT NULL COMMENT 'entity primary key',
  `entity_type` varchar(64) NOT NULL COMMENT 'entity type: JOB, USER, RESOURCE, etc.',
  `operation` varchar(16) NOT NULL COMMENT 'INSERT | UPDATE | DELETE',
  `snapshot` text NOT NULL COMMENT 'full JSON snapshot of entity state',
  `operator_id` bigint(11) DEFAULT NULL COMMENT 'user id who made the change',
  `operate_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'change time',
  PRIMARY KEY (`id`),
  KEY `t_audit_log_entity_id_idx` (`entity_id`) USING BTREE,
  KEY `t_audit_log_entity_type_idx` (`entity_type`) USING BTREE,
  KEY `t_audit_log_operator_id_idx` (`operator_id`) USING BTREE,
  KEY `t_audit_log_operate_time_idx` (`operate_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='audit log';

-- 2026-04-12
ALTER TABLE platform.t_user ADD COLUMN roles varchar(1024) DEFAULT '{}' NOT NULL COMMENT 'roles json' after workers;
ALTER TABLE platform.t_user ADD COLUMN external_id varchar(255) DEFAULT NULL COMMENT 'external id, NULL for local users' after email;
ALTER TABLE platform.t_user MODIFY COLUMN status varchar(32) COMMENT 'user status';
ALTER TABLE platform.t_user DROP COLUMN `type`;
ALTER TABLE platform.t_user ADD UNIQUE KEY `t_user_username_uk` (`username`);
ALTER TABLE platform.t_user ADD UNIQUE KEY `t_user_external_id_uk` (`external_id`);

ALTER TABLE platform.t_job_flow ADD COLUMN workspace_id bigint(11) NOT NULL COMMENT 'workspace id' AFTER user_id;
ALTER TABLE platform.t_job_flow_run ADD COLUMN workspace_id bigint(11) NOT NULL COMMENT 'workspace id' AFTER user_id;

UPDATE platform.t_job_flow SET workspace_id = user_id;
UPDATE platform.t_job_flow_run SET workspace_id = user_id;
UPDATE platform.t_user SET roles = '{"global":"SUPER_ADMIN","workspaces":{}}' WHERE type = 'ADMIN';
UPDATE t_user SET roles = '{"global":null,"workspaces":{}}' WHERE type != 'ADMIN' OR type IS NULL;
UPDATE t_user SET status = 'NORMAL';

CREATE TABLE platform.`t_workspace` (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT 'workspace name',
  `description` varchar(255) DEFAULT NULL COMMENT 'description',
  `config` varchar(1024) NOT NULL COMMENT 'workspace config',
  `status` varchar(32) NOT NULL COMMENT 'workspace status',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `t_workspace_name_unique` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='workspace';


