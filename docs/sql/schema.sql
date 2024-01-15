/*
 Navicat Premium Data Transfer

 Source Server         : 127.0.0.1
 Source Server Type    : MySQL
 Source Server Version : 50729
 Source Host           : 127.0.0.1:3306
 Source Schema         : platform

 Target Server Type    : MySQL
 Target Server Version : 50729
 File Encoding         : 65001

 Date: 17/04/2022 13:09:55
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Create an use database
-- ----------------------------
CREATE DATABASE IF NOT EXISTS platform;
USE platform;

-- ----------------------------
-- Table structure for qrtz_blob_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_blob_triggers`;
CREATE TABLE `qrtz_blob_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `TRIGGER_NAME` varchar(190) NOT NULL,
  `TRIGGER_GROUP` varchar(190) NOT NULL,
  `BLOB_DATA` blob,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `SCHED_NAME` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `QRTZ_BLOB_TRIGGERS_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `qrtz_triggers` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for qrtz_calendars
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_calendars`;
CREATE TABLE `qrtz_calendars` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `CALENDAR_NAME` varchar(190) NOT NULL,
  `CALENDAR` blob NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`CALENDAR_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for qrtz_cron_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_cron_triggers`;
CREATE TABLE `qrtz_cron_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `TRIGGER_NAME` varchar(190) NOT NULL,
  `TRIGGER_GROUP` varchar(190) NOT NULL,
  `CRON_EXPRESSION` varchar(120) NOT NULL,
  `TIME_ZONE_ID` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `QRTZ_CRON_TRIGGERS_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `qrtz_triggers` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for qrtz_fired_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_fired_triggers`;
CREATE TABLE `qrtz_fired_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `ENTRY_ID` varchar(95) NOT NULL,
  `TRIGGER_NAME` varchar(190) NOT NULL,
  `TRIGGER_GROUP` varchar(190) NOT NULL,
  `INSTANCE_NAME` varchar(190) NOT NULL,
  `FIRED_TIME` bigint(13) NOT NULL,
  `SCHED_TIME` bigint(13) NOT NULL,
  `PRIORITY` int(11) NOT NULL,
  `STATE` varchar(16) NOT NULL,
  `JOB_NAME` varchar(190) DEFAULT NULL,
  `JOB_GROUP` varchar(190) DEFAULT NULL,
  `IS_NONCONCURRENT` varchar(1) DEFAULT NULL,
  `REQUESTS_RECOVERY` varchar(1) DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`,`ENTRY_ID`),
  KEY `IDX_QRTZ_FT_TRIG_INST_NAME` (`SCHED_NAME`,`INSTANCE_NAME`),
  KEY `IDX_QRTZ_FT_INST_JOB_REQ_RCVRY` (`SCHED_NAME`,`INSTANCE_NAME`,`REQUESTS_RECOVERY`),
  KEY `IDX_QRTZ_FT_J_G` (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_FT_JG` (`SCHED_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_FT_T_G` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_FT_TG` (`SCHED_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for qrtz_job_details
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_job_details`;
CREATE TABLE `qrtz_job_details` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `JOB_NAME` varchar(190) NOT NULL,
  `JOB_GROUP` varchar(190) NOT NULL,
  `DESCRIPTION` varchar(250) DEFAULT NULL,
  `JOB_CLASS_NAME` varchar(250) NOT NULL,
  `IS_DURABLE` varchar(1) NOT NULL,
  `IS_NONCONCURRENT` varchar(1) NOT NULL,
  `IS_UPDATE_DATA` varchar(1) NOT NULL,
  `REQUESTS_RECOVERY` varchar(1) NOT NULL,
  `JOB_DATA` blob,
  PRIMARY KEY (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_J_REQ_RECOVERY` (`SCHED_NAME`,`REQUESTS_RECOVERY`),
  KEY `IDX_QRTZ_J_GRP` (`SCHED_NAME`,`JOB_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for qrtz_locks
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_locks`;
CREATE TABLE `qrtz_locks` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `LOCK_NAME` varchar(40) NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`LOCK_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for qrtz_paused_trigger_grps
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_paused_trigger_grps`;
CREATE TABLE `qrtz_paused_trigger_grps` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `TRIGGER_GROUP` varchar(190) NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for qrtz_scheduler_state
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_scheduler_state`;
CREATE TABLE `qrtz_scheduler_state` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `INSTANCE_NAME` varchar(190) NOT NULL,
  `LAST_CHECKIN_TIME` bigint(13) NOT NULL,
  `CHECKIN_INTERVAL` bigint(13) NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`INSTANCE_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for qrtz_simple_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_simple_triggers`;
CREATE TABLE `qrtz_simple_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `TRIGGER_NAME` varchar(190) NOT NULL,
  `TRIGGER_GROUP` varchar(190) NOT NULL,
  `REPEAT_COUNT` bigint(7) NOT NULL,
  `REPEAT_INTERVAL` bigint(12) NOT NULL,
  `TIMES_TRIGGERED` bigint(10) NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `QRTZ_SIMPLE_TRIGGERS_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `qrtz_triggers` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for qrtz_simprop_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_simprop_triggers`;
CREATE TABLE `qrtz_simprop_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `TRIGGER_NAME` varchar(190) NOT NULL,
  `TRIGGER_GROUP` varchar(190) NOT NULL,
  `STR_PROP_1` varchar(512) DEFAULT NULL,
  `STR_PROP_2` varchar(512) DEFAULT NULL,
  `STR_PROP_3` varchar(512) DEFAULT NULL,
  `INT_PROP_1` int(11) DEFAULT NULL,
  `INT_PROP_2` int(11) DEFAULT NULL,
  `LONG_PROP_1` bigint(20) DEFAULT NULL,
  `LONG_PROP_2` bigint(20) DEFAULT NULL,
  `DEC_PROP_1` decimal(13,4) DEFAULT NULL,
  `DEC_PROP_2` decimal(13,4) DEFAULT NULL,
  `BOOL_PROP_1` varchar(1) DEFAULT NULL,
  `BOOL_PROP_2` varchar(1) DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `QRTZ_SIMPROP_TRIGGERS_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `qrtz_triggers` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for qrtz_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_triggers`;
CREATE TABLE `qrtz_triggers` (
  `SCHED_NAME` varchar(120) NOT NULL,
  `TRIGGER_NAME` varchar(190) NOT NULL,
  `TRIGGER_GROUP` varchar(190) NOT NULL,
  `JOB_NAME` varchar(190) NOT NULL,
  `JOB_GROUP` varchar(190) NOT NULL,
  `DESCRIPTION` varchar(250) DEFAULT NULL,
  `NEXT_FIRE_TIME` bigint(13) DEFAULT NULL,
  `PREV_FIRE_TIME` bigint(13) DEFAULT NULL,
  `PRIORITY` int(11) DEFAULT NULL,
  `TRIGGER_STATE` varchar(16) NOT NULL,
  `TRIGGER_TYPE` varchar(8) NOT NULL,
  `START_TIME` bigint(13) NOT NULL,
  `END_TIME` bigint(13) DEFAULT NULL,
  `CALENDAR_NAME` varchar(190) DEFAULT NULL,
  `MISFIRE_INSTR` smallint(2) DEFAULT NULL,
  `JOB_DATA` blob,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_T_J` (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_T_JG` (`SCHED_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_T_C` (`SCHED_NAME`,`CALENDAR_NAME`),
  KEY `IDX_QRTZ_T_G` (`SCHED_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_T_STATE` (`SCHED_NAME`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_N_STATE` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_N_G_STATE` (`SCHED_NAME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_NEXT_FIRE_TIME` (`SCHED_NAME`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_ST` (`SCHED_NAME`,`TRIGGER_STATE`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_MISFIRE` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_ST_MISFIRE` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_NFT_ST_MISFIRE_GRP` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  CONSTRAINT `QRTZ_TRIGGERS_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`) REFERENCES `qrtz_job_details` (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for t_alert
-- ----------------------------
DROP TABLE IF EXISTS `t_alert`;
CREATE TABLE `t_alert` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(64) NOT NULL COMMENT 'alert name',
  `description` varchar(255) DEFAULT NULL COMMENT 'alert desc',
  `user_id` bigint(11) NOT NULL COMMENT 'user id',
  `type` varchar(64) NOT NULL COMMENT 'alert type',
  `config` varchar(4096) NOT NULL COMMENT 'alert config',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='alert config info';

-- ----------------------------
-- Table structure for t_catalog_info
-- ----------------------------
DROP TABLE IF EXISTS `t_catalog_info`;
CREATE TABLE `t_catalog_info` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(64) NOT NULL COMMENT 'catalog name',
  `description` varchar(255) DEFAULT NULL COMMENT 'catalog desc',
  `user_id` bigint(11) NOT NULL COMMENT 'user id',
  `type` varchar(32) NOT NULL COMMENT 'catalog type',
  `default_database` varchar(64) NOT NULL COMMENT 'default database',
  `config_path` varchar(128) DEFAULT NULL COMMENT 'config dir path',
  `configs` varchar(1024) DEFAULT NULL COMMENT 'config properties',
  `create_sql` varchar(2048) DEFAULT NULL COMMENT 'catalog create sql',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='job catalog info';

-- ----------------------------
-- Table structure for t_job
-- ----------------------------
DROP TABLE IF EXISTS `t_job`;
CREATE TABLE `t_job` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(64) NOT NULL COMMENT 'job name',
  `description` varchar(255) DEFAULT NULL COMMENT 'job desc',
  `flow_id` bigint(11) DEFAULT NULL COMMENT 'job flow id',
  `user_id` bigint(11) NOT NULL COMMENT 'user id',
  `type` varchar(32) NOT NULL COMMENT 'job type',
  `version` varchar(32) DEFAULT NULL COMMENT 'job version',
  `deploy_mode` varchar(64) NOT NULL COMMENT 'deploy mode',
  `exec_mode` varchar(64) NOT NULL COMMENT 'execution mode',
  `config` varchar(4096) NOT NULL COMMENT 'job config detail',
  `variables` text COMMENT 'variables',
  `subject` text COMMENT 'main content',
  `route_url` varchar(255) DEFAULT NULL COMMENT 'route url',
  `status` varchar(32) NOT NULL COMMENT 'ONLINE, OFFLINE, etc',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='job config info';

-- ----------------------------
-- Table structure for t_job_flow
-- ----------------------------
DROP TABLE IF EXISTS `t_job_flow`;
CREATE TABLE `t_job_flow` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `code` varchar(32) NOT NULL COMMENT 'flow code',
  `name` varchar(64) NOT NULL COMMENT 'flow name',
  `user_id` int(11) NOT NULL COMMENT 'user id',
  `description` varchar(255) DEFAULT NULL COMMENT 'flow desc',
  `cron_expr` varchar(64) DEFAULT NULL COMMENT 'crontab expression',
  `flow` text COMMENT 'flow definition',
  `priority` tinyint(2) DEFAULT NULL COMMENT 'execution priority',
  `tags` VARCHAR(255) DEFAULT NULL COMMENT 'tag list',
  `alerts` varchar(255) DEFAULT NULL COMMENT 'alert strategy',
  `timeout` varchar(255) DEFAULT NULL COMMENT 'timeout',
  `status` varchar(32) NOT NULL COMMENT 'flow status',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code_idx` (`code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='job flow info';

-- ----------------------------
-- Table structure for t_job_param
-- ----------------------------
DROP TABLE IF EXISTS `t_job_param`;
CREATE TABLE `t_job_param` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `user_id` bigint(11) DEFAULT NULL COMMENT 'user id',
  `flow_id` bigint(11) DEFAULT NULL COMMENT 'job flow id',
  `description` varchar(255) DEFAULT NULL COMMENT 'description',
  `type` varchar(100) DEFAULT NULL COMMENT 'param type',
  `param_name` varchar(255) DEFAULT NULL COMMENT 'param name',
  `param_value` varchar(255) DEFAULT NULL COMMENT 'param value',
  `status` varchar(100) DEFAULT NULL COMMENT 'status',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='job parameter';

-- ----------------------------
-- Table structure for t_job_flow_run
-- ----------------------------
DROP TABLE IF EXISTS `t_job_flow_run`;
CREATE TABLE `t_job_flow_run` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `user_id` bigint(11) NOT NULL,
  `flow_id` int(11) NOT NULL,
  `cron_expr` varchar(64) DEFAULT NULL,
  `flow` text NOT NULL,
  `host` varchar(255) NOT NULL,
  `priority` tinyint(2) DEFAULT NULL,
  `config` varchar(255) DEFAULT NULL,
  `tags` VARCHAR(255) DEFAULT NULL,
  `alerts` varchar(255) DEFAULT NULL,
  `timeout` varchar(255) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `t_job_flow_run_create_time_idx` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for t_job_run
-- ----------------------------
DROP TABLE IF EXISTS `t_job_run`;
CREATE TABLE `t_job_run` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(128) DEFAULT NULL COMMENT 'instance name',
  `job_id` bigint(11) NOT NULL COMMENT 'job id',
  `flow_run_id` bigint(11) DEFAULT NULL COMMENT 'flow run id',
  `user_id` bigint(11) NOT NULL COMMENT 'user id',
  `type` varchar(32) DEFAULT NULL COMMENT 'type',
  `version` varchar(32) DEFAULT NULL COMMENT 'version',
  `deploy_mode` varchar(64) DEFAULT NULL COMMENT 'deploy mode',
  `exec_mode` varchar(64) DEFAULT NULL,
  `config` varchar(4096) DEFAULT NULL COMMENT 'config',
  `route_url` varchar(255) DEFAULT NULL COMMENT 'route url',
  `variables` varchar(1024) DEFAULT NULL COMMENT 'variables map',
  `subject` text COMMENT 'subject',
  `back_info` text COMMENT 'callback info',
  `host` varchar(255) DEFAULT NULL COMMENT 'host ip',
  `status` varchar(32) NOT NULL COMMENT 'instance status',
  `submit_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'submit time',
  `stop_time` datetime DEFAULT NULL COMMENT 'stop time',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  KEY `t_job_run_create_time_idx` (`create_time`) USING BTREE,
  KEY `t_job_run_flow_run_id_idx` (`flow_run_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='job run info';

-- ----------------------------
-- Table structure for t_resource
-- ----------------------------
DROP TABLE IF EXISTS `t_resource`;
CREATE TABLE `t_resource` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(64) NOT NULL COMMENT 'alert name',
  `full_name` varchar(255) NOT NULL COMMENT 'alert name',
  `description` varchar(255) DEFAULT NULL COMMENT 'alert desc',
  `pid` bigint(11) DEFAULT NULL COMMENT 'parent id',
  `user_id` bigint(11) NOT NULL COMMENT 'user id',
  `type` varchar(64) NOT NULL COMMENT 'alert type',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='resource info';

-- ----------------------------
-- Table structure for t_user
-- ----------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `password` varchar(255) DEFAULT NULL,
  `type` varchar(32) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `workers` varchar(255) DEFAULT NULL,
  `status` tinyint(2) DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='login user info';

-- ----------------------------
-- Table structure for t_user_session
-- ----------------------------
DROP TABLE IF EXISTS `t_user_session`;
CREATE TABLE `t_user_session` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT,
  `token` varchar(64) NOT NULL,
  `user_id` bigint(11) NOT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `last_login_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='login user session';

-- ----------------------------
-- Table structure for t_worker
-- ----------------------------
DROP TABLE IF EXISTS `t_worker`;
CREATE TABLE `t_worker` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `desc` varchar(255) DEFAULT NULL,
  `ip` varchar(64) NOT NULL,
  `port` varchar(16) DEFAULT NULL,
  `grpc_port` int(6) DEFAULT NULL,
  `status` varchar(32) DEFAULT NULL,
  `heartbeat` bigint(11) DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ip_idx` (`ip`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='worker instance info';

-- ----------------------------
-- Table structure for t_datasource
-- ----------------------------
CREATE TABLE `t_datasource` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(64) NOT NULL COMMENT 'name',
  `description` varchar(255) DEFAULT NULL COMMENT 'description',
  `user_id` bigint(11) NOT NULL COMMENT 'user id',
  `type` varchar(64) NOT NULL COMMENT 'db type',
  `params` varchar(4096) DEFAULT NULL COMMENT 'datasource properties',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='datasource info';

-- ----------------------------
-- Table structure for t_tag
-- ----------------------------
CREATE TABLE `t_tag` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `code` varchar(32) NOT NULL COMMENT 'code',
  `name` varchar(255) DEFAULT NULL COMMENT 'name',
  `user_id` bigint(11) DEFAULT NULL COMMENT 'user id',
  `type` varchar(100) DEFAULT NULL COMMENT 'type',
  `status` varchar(100) DEFAULT NULL COMMENT 'status',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `t_tag_un` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='tag info';

-- ----------------------------
-- Records of t_user
-- ----------------------------
BEGIN;
INSERT INTO `t_user` VALUES (1, 'admin', '111111', 'ADMIN', 'admin@email.com', '[]', NULL, now(), now());
COMMIT;

-- ----------------------------
-- Records of t_catalog_info
-- ----------------------------
BEGIN;
INSERT INTO `t_catalog_info` VALUES(1, 'hive', 'hive_db', 1, 'HIVE', 'profile', '/etc/hive/conf', NULL, NULL, now(), now());
INSERT INTO `t_catalog_info` VALUES (2, 'tidb', 'tidb_db', 1, 'TIDB', 'discovery', NULL, '{\"tidb.database.url\":\"jdbc:mysql://0.0.0.0:4000/discovery\",\"tidb.username\":\"admin\",\"tidb.password\":\"passwd\",\"tidb.filter-push-down\":\"true\"}', NULL, now(), now());
INSERT INTO `t_catalog_info` VALUES (3, 'clickhouse', 'user_db', 1, 'CLICKHOUSE', 'user_db', NULL, '{\"url\":\"clickhouse://0.0.0.0:8123\", \"username\":\"user\", \"password\":\"passwd\", \"sink.flush-interval\":\"60s\", \"sink.write-local\":\"true\", \"sink.batch-size\":\"500000\"}', NULL, now(), now());
INSERT INTO `t_catalog_info` VALUES (4, 'iceberg', 'iceberg_db', 1, 'ICEBERG', 'iceberg_db', NULL, NULL, 'CREATE CATALOG iceberg WITH (\n  \'type\'=\'iceberg\',\n  \'catalog-type\'=\'hive\',\n  \'hive-conf-dir\'=\'/etc/hive/conf\',\n  \'property-version\'=\'1\',\n  \'warehouse\'=\'hdfs:///iceberg/warehouse\',\n  \'default-database\'=\'iceberg_db\'\n)', now(), now());
COMMIT;

-- ----------------------------
-- Records of t_worker
-- ----------------------------
BEGIN;
INSERT INTO `t_worker` VALUES (0, 'unlimited', 'run locally', '127.0.0.1', '9104', 9898, 'FOLLOWER', NULL, now(), now());
COMMIT;
