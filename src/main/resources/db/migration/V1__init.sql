-- =====================================================================
-- V1 基线：海峡金 OA 全量 schema + 干净种子数据（压平重建，2026-09-12）
-- 取代原 V1~V32 迁移链。设计决策：
--   * 角色：17 个具体角色 + 「部门负责人」（承接原型两条泛化负责人条目，不挂部门）+ 兜底「普通员工」 = 19 个
--   * 兼职：仅真实任职 3 条（翁婷婷→财务中心、汪洋/姚志豪→财务部）；总经办不接受兼职挂靠
--   * 汇报线：按规则重建（成员→部门主管→中心负责人→王强），不再使用 V6 推断数据
--   * 事务数据（单据/审批/附件/抄送）不入基线；Flowable 引擎表由引擎自建
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `approval_record`
--

DROP TABLE IF EXISTS `approval_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approval_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doc_id` bigint NOT NULL COMMENT '单据ID',
  `node_name` varchar(100) DEFAULT NULL COMMENT '节点名称',
  `approver` varchar(50) DEFAULT NULL COMMENT '旧审批人姓名（兼容字段）',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `action` varchar(50) NOT NULL COMMENT '审批操作',
  `comment` text COMMENT '审批意见',
  `reject_target` varchar(100) DEFAULT NULL COMMENT '驳回目标层级',
  `reject_materials` varchar(500) DEFAULT NULL COMMENT '驳回需补充材料',
  `supplement_mode` varchar(20) DEFAULT NULL COMMENT '补材料模式(BEFORE_PAY/AFTER_PAY)',
  `supplement_target` varchar(100) DEFAULT NULL COMMENT '补材料指定对象',
  `supplement_materials` varchar(500) DEFAULT NULL COMMENT '需补充材料清单',
  `sign_user` varchar(50) DEFAULT NULL COMMENT '加签人员',
  `sign_user_id` bigint DEFAULT NULL COMMENT '加签人员ID',
  `sign_reason` varchar(500) DEFAULT NULL COMMENT '加签说明',
  `evidence_file_id` bigint DEFAULT NULL COMMENT '审批凭证附件ID',
  `resolved` tinyint(1) NOT NULL DEFAULT '1' COMMENT '补充材料要求是否已解决',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_record_doc` (`doc_id`),
  KEY `fk_approval_approver` (`approver_id`),
  KEY `fk_approval_sign_user` (`sign_user_id`),
  KEY `fk_approval_evidence` (`evidence_file_id`),
  CONSTRAINT `fk_approval_approver` FOREIGN KEY (`approver_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_approval_evidence` FOREIGN KEY (`evidence_file_id`) REFERENCES `oa_attachment` (`id`),
  CONSTRAINT `fk_approval_sign_user` FOREIGN KEY (`sign_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_record_doc` FOREIGN KEY (`doc_id`) REFERENCES `oa_document` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=67 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `archive_ledger`
--

DROP TABLE IF EXISTS `archive_ledger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `archive_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doc_id` bigint NOT NULL COMMENT '单据ID',
  `doc_code` varchar(50) NOT NULL COMMENT '单据编号',
  `project_name` varchar(200) DEFAULT NULL COMMENT '申请事项',
  `business_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `doc_type` varchar(50) DEFAULT NULL COMMENT '单据类型',
  `company` varchar(100) DEFAULT NULL COMMENT '所属公司',
  `applicant` varchar(50) DEFAULT NULL COMMENT '申请人',
  `department` varchar(100) DEFAULT NULL COMMENT '申请部门',
  `amount` decimal(15,2) DEFAULT '0.00' COMMENT '金额',
  `archived_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_archive_document` (`doc_id`),
  CONSTRAINT `fk_archive_doc` FOREIGN KEY (`doc_id`) REFERENCES `oa_document` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='归档台账表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cc_record`
--

DROP TABLE IF EXISTS `cc_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cc_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doc_id` bigint NOT NULL COMMENT '单据ID',
  `cc_target` varchar(200) DEFAULT NULL COMMENT '抄送对象',
  `target_user_id` bigint DEFAULT NULL COMMENT '抄送人员ID',
  `target_role_id` bigint DEFAULT NULL COMMENT '抄送角色ID',
  `cc_source` varchar(20) DEFAULT 'FLOW' COMMENT '抄送来源(FLOW流程配置/SELF发起人自选)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_cc_doc` (`doc_id`),
  KEY `fk_cc_target_user` (`target_user_id`),
  KEY `fk_cc_target_role` (`target_role_id`),
  CONSTRAINT `fk_cc_doc` FOREIGN KEY (`doc_id`) REFERENCES `oa_document` (`id`),
  CONSTRAINT `fk_cc_target_role` FOREIGN KEY (`target_role_id`) REFERENCES `sys_role` (`id`),
  CONSTRAINT `fk_cc_target_user` FOREIGN KEY (`target_user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抄送记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `doc_seq`
--

DROP TABLE IF EXISTS `doc_seq`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `doc_seq` (
  `seq_date` varchar(8) NOT NULL COMMENT '日期yyyyMMdd',
  `seq_value` bigint NOT NULL DEFAULT '0' COMMENT '当日流水号',
  PRIMARY KEY (`seq_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单据编号序列表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flow_condition_rule`
--

DROP TABLE IF EXISTS `flow_condition_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_condition_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_config_id` bigint NOT NULL,
  `variable_name` varchar(100) NOT NULL COMMENT '流程变量名',
  `operator` varchar(50) NOT NULL COMMENT '比较运算符',
  `expected_value` varchar(200) NOT NULL COMMENT '期望值',
  `target_node_name` varchar(100) NOT NULL COMMENT '命中后的目标节点',
  `sort_order` int NOT NULL COMMENT '规则顺序',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_condition_order` (`flow_config_id`,`sort_order`),
  CONSTRAINT `fk_flow_condition_config` FOREIGN KEY (`flow_config_id`) REFERENCES `flow_config` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程条件分支规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flow_config`
--

DROP TABLE IF EXISTS `flow_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` varchar(200) NOT NULL COMMENT '业务单据类型',
  `category` varchar(50) NOT NULL COMMENT '分类(日常/业务/用印)',
  `nodes` text COMMENT '旧节点链JSON（兼容字段）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `type` (`type`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flow_node_config`
--

DROP TABLE IF EXISTS `flow_node_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_node_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_config_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL COMMENT '节点名称',
  `node_type` varchar(30) NOT NULL COMMENT '节点类型',
  `assignee_role` varchar(100) DEFAULT NULL COMMENT '审批角色',
  `sort_order` int NOT NULL COMMENT '节点顺序',
  `cc_targets` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_node_order` (`flow_config_id`,`sort_order`),
  CONSTRAINT `fk_flow_node_config` FOREIGN KEY (`flow_config_id`) REFERENCES `flow_config` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=293 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程节点配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `form_field`
--

DROP TABLE IF EXISTS `form_field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `form_field` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_id` bigint NOT NULL,
  `field_key` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `control_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `required` tinyint(1) NOT NULL DEFAULT '0',
  `options` text COLLATE utf8mb4_unicode_ci,
  `reserved` tinyint(1) NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_field` (`template_id`,`field_key`),
  CONSTRAINT `fk_form_field_template` FOREIGN KEY (`template_id`) REFERENCES `form_template` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `form_template`
--

DROP TABLE IF EXISTS `form_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `form_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `business_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `doc_prefix` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `flow_config_id` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '1',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `sort_order` int NOT NULL DEFAULT '0',
  `category` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_requirements` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_form_template_business_type` (`business_type`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `oa_attachment`
--

DROP TABLE IF EXISTS `oa_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `oa_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doc_id` bigint NOT NULL COMMENT '单据ID',
  `node_name` varchar(100) DEFAULT NULL COMMENT '所属节点',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_path` varchar(500) NOT NULL COMMENT '存储路径',
  `content_type` varchar(100) DEFAULT NULL COMMENT '文件MIME类型',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `uploader_id` bigint DEFAULT NULL COMMENT '上传人ID',
  `uploader` varchar(50) DEFAULT NULL COMMENT '上传人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `field_key` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_attach_doc` (`doc_id`),
  KEY `fk_attachment_uploader` (`uploader_id`),
  CONSTRAINT `fk_attach_doc` FOREIGN KEY (`doc_id`) REFERENCES `oa_document` (`id`),
  CONSTRAINT `fk_attachment_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='附件表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `oa_document`
--

DROP TABLE IF EXISTS `oa_document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `oa_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doc_code` varchar(50) NOT NULL COMMENT '单据编号',
  `business_type` varchar(50) NOT NULL COMMENT '业务类型(日常付款/业务付款/用印申请)',
  `project_name` varchar(200) DEFAULT NULL COMMENT '对应项目(申请事项)',
  `doc_type` varchar(50) NOT NULL COMMENT '单据类型(日常申请单/付款申请单/用印申请单)',
  `contract_no` varchar(100) DEFAULT NULL COMMENT '关联合同编号(业务付款类)',
  `applicant_id` bigint DEFAULT NULL COMMENT '申请人ID',
  `applicant` varchar(50) DEFAULT NULL COMMENT '旧申请人姓名（兼容字段）',
  `company` varchar(100) DEFAULT NULL COMMENT '所属公司',
  `department` varchar(100) DEFAULT NULL COMMENT '所属部门',
  `amount` decimal(15,2) DEFAULT '0.00' COMMENT '申请金额',
  `invoice_summary` varchar(500) DEFAULT NULL COMMENT '发票明细',
  `reason` text COMMENT '申请事由',
  `need_post_material` tinyint(1) DEFAULT '0' COMMENT '付款后需补材料',
  `seal_project` varchar(200) DEFAULT NULL COMMENT '用印项目',
  `seal_department` varchar(100) DEFAULT NULL COMMENT '用印部门',
  `seal_time` datetime DEFAULT NULL COMMENT '用印时间',
  `file_name` varchar(200) DEFAULT NULL COMMENT '用印文件名称',
  `seal_type` varchar(50) DEFAULT NULL COMMENT '用章类型',
  `seal_reason` text COMMENT '用印原因',
  `linked_doc_id` bigint DEFAULT NULL COMMENT '前置关联单据ID',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING待提交/APPROVING审批中/APPROVED已通过/REJECTED已驳回/SUPPLEMENT_REQUIRED待补充材料)',
  `current_node` varchar(100) DEFAULT NULL COMMENT '当前节点',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT 'Flowable流程实例ID',
  `flow_config_id` bigint DEFAULT NULL COMMENT '提交时的流程配置ID',
  `risk_flag` tinyint(1) DEFAULT '0' COMMENT '风险标记',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `form_template_id` bigint DEFAULT NULL,
  `form_version` int DEFAULT NULL,
  `form_snapshot` text,
  `field_values` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `doc_code` (`doc_code`),
  KEY `fk_doc_link` (`linked_doc_id`),
  KEY `fk_document_applicant` (`applicant_id`),
  KEY `fk_doc_flow_config` (`flow_config_id`),
  CONSTRAINT `fk_doc_flow_config` FOREIGN KEY (`flow_config_id`) REFERENCES `flow_config` (`id`),
  CONSTRAINT `fk_doc_link` FOREIGN KEY (`linked_doc_id`) REFERENCES `oa_document` (`id`),
  CONSTRAINT `fk_document_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_data_scope`
--

DROP TABLE IF EXISTS `sys_data_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_data_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(100) NOT NULL COMMENT '数据范围编码',
  `name` varchar(100) NOT NULL COMMENT '数据范围名称',
  `description` varchar(500) DEFAULT NULL COMMENT '数据范围说明',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据范围表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_department`
--

DROP TABLE IF EXISTS `sys_department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '部门名称（全公司唯一）',
  `parent_id` bigint DEFAULT NULL COMMENT '上级部门ID，根部门为空',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '同级排序号',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_department_name` (`name`),
  KEY `fk_department_parent` (`parent_id`),
  CONSTRAINT `fk_department_parent` FOREIGN KEY (`parent_id`) REFERENCES `sys_department` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表（闭包表模型，支持任意层级）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_department_closure`
--

DROP TABLE IF EXISTS `sys_department_closure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_department_closure` (
  `ancestor_id` bigint NOT NULL,
  `descendant_id` bigint NOT NULL,
  `depth` int NOT NULL COMMENT '祖先到后代的距离，自身为0',
  PRIMARY KEY (`ancestor_id`,`descendant_id`),
  KEY `fk_closure_descendant` (`descendant_id`),
  CONSTRAINT `fk_closure_ancestor` FOREIGN KEY (`ancestor_id`) REFERENCES `sys_department` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_closure_descendant` FOREIGN KEY (`descendant_id`) REFERENCES `sys_department` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门闭包表：祖先-后代路径';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_permission`
--

DROP TABLE IF EXISTS `sys_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(100) NOT NULL COMMENT '权限点编码',
  `name` varchar(100) NOT NULL COMMENT '权限点名称',
  `description` varchar(500) DEFAULT NULL COMMENT '权限点说明',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限点表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_post`
--

DROP TABLE IF EXISTS `sys_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_post` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '岗位名称（唯一）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位字典表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '角色名称',
  `department` varchar(100) DEFAULT NULL COMMENT '对应部门',
  `department_id` bigint DEFAULT NULL COMMENT '归属部门ID',
  `post` varchar(100) DEFAULT NULL COMMENT '对应岗位',
  `data_scope_id` bigint NOT NULL,
  `data_scope` varchar(100) DEFAULT NULL COMMENT '数据范围',
  `permissions` text COMMENT '权限点集合(JSON)',
  `members` text COMMENT '成员列表(JSON)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  KEY `fk_role_data_scope` (`data_scope_id`),
  KEY `fk_role_department` (`department_id`),
  CONSTRAINT `fk_role_data_scope` FOREIGN KEY (`data_scope_id`) REFERENCES `sys_data_scope` (`id`),
  CONSTRAINT `fk_role_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_permission`
--

DROP TABLE IF EXISTS `sys_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `fk_role_permission_permission` (`permission_id`),
  CONSTRAINT `fk_role_permission_permission` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_role_permission_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_scope_department`
--

DROP TABLE IF EXISTS `sys_role_scope_department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_scope_department` (
  `role_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`,`department_id`),
  KEY `fk_role_scope_dept_department` (`department_id`),
  CONSTRAINT `fk_role_scope_dept_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_role_scope_dept_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色自定义数据范围部门集合';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `job_no` varchar(50) DEFAULT NULL COMMENT '工号',
  `account` varchar(50) NOT NULL COMMENT '登录账号',
  `password` varchar(255) NOT NULL COMMENT '登录密码(BCrypt)',
  `department` varchar(100) DEFAULT NULL COMMENT '所属部门',
  `department_id` bigint NOT NULL COMMENT '部门ID',
  `post` varchar(100) DEFAULT NULL COMMENT '岗位',
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态',
  `manager_id` bigint DEFAULT NULL COMMENT '直属主管ID（汇报线）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `account` (`account`),
  UNIQUE KEY `uk_user_job_no` (`job_no`),
  KEY `fk_user_department` (`department_id`),
  KEY `fk_user_post` (`post_id`),
  KEY `fk_user_manager` (`manager_id`),
  CONSTRAINT `fk_user_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`),
  CONSTRAINT `fk_user_manager` FOREIGN KEY (`manager_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_user_post` FOREIGN KEY (`post_id`) REFERENCES `sys_post` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=86 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_department`
--

DROP TABLE IF EXISTS `sys_user_department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  `primary_department` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_department` (`user_id`,`department_id`),
  KEY `fk_usd_department` (`department_id`),
  CONSTRAINT `fk_usd_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_usd_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=99 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工兼职部门关联（主部门在 sys_user.department_id）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `fk_user_role_role` (`role_id`),
  CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_service_dept`
--

DROP TABLE IF EXISTS `sys_user_service_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_service_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_service_dept` (`user_id`,`department_id`),
  KEY `fk_service_dept_department` (`department_id`),
  CONSTRAINT `fk_service_dept_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_service_dept_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='职能服务分工：成员服务哪些部门的单据（如核算会计按部门分工）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:11:51


-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_data_scope`
--

LOCK TABLES `sys_data_scope` WRITE;
/*!40000 ALTER TABLE `sys_data_scope` DISABLE KEYS */;
INSERT INTO `sys_data_scope` (`id`, `code`, `name`, `description`, `created_at`) VALUES (20,'ALL','全部单据','可查看全部单据','2026-09-10 09:12:48'),(21,'OWN','仅本人单据','仅可查看本人提交的单据','2026-09-10 09:12:48'),(22,'DEPT','本部门','可查看本部门提交的单据','2026-09-10 09:12:48'),(23,'DEPT_AND_CHILD','本部门及以下','可查看本部门及其下级部门提交的单据','2026-09-10 09:12:48'),(24,'CUSTOM','自定义部门集合','可查看指定部门集合提交的单据','2026-09-10 09:12:48');
/*!40000 ALTER TABLE `sys_data_scope` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_department`
--

LOCK TABLES `sys_department` WRITE;
/*!40000 ALTER TABLE `sys_department` DISABLE KEYS */;
INSERT INTO `sys_department` (`id`, `name`, `parent_id`, `sort_order`, `created_at`, `updated_at`) VALUES (16,'总经办',NULL,1,'2026-09-10 10:18:42','2026-09-10 10:18:42'),(20,'财务中心',NULL,2,'2026-09-10 10:44:09','2026-09-10 10:44:17'),(22,'财务部',20,0,'2026-09-10 10:44:28','2026-09-10 10:44:28'),(23,'内控部',16,0,'2026-09-10 10:44:49','2026-09-10 10:44:49'),(24,'法务部',16,0,'2026-09-10 10:44:59','2026-09-10 10:44:59'),(25,'人力行政中心',NULL,3,'2026-09-10 10:45:18','2026-09-10 10:45:18'),(26,'行政部',25,0,'2026-09-10 10:45:31','2026-09-10 10:45:31'),(27,'业务支持中心',NULL,4,'2026-09-10 10:45:45','2026-09-10 10:45:57'),(28,'运营服务部',27,0,'2026-09-10 11:05:55','2026-09-10 11:05:55'),(29,'供应链中心',NULL,5,'2026-09-10 11:06:27','2026-09-10 11:06:27'),(30,'交付部',29,0,'2026-09-10 11:06:42','2026-09-10 11:06:42'),(31,'资管中心',NULL,6,'2026-09-10 11:06:53','2026-09-10 11:07:14');
/*!40000 ALTER TABLE `sys_department` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_department_closure`
--

LOCK TABLES `sys_department_closure` WRITE;
/*!40000 ALTER TABLE `sys_department_closure` DISABLE KEYS */;
INSERT INTO `sys_department_closure` (`ancestor_id`, `descendant_id`, `depth`) VALUES (16,16,0),(16,23,1),(16,24,1),(20,20,0),(20,22,1),(22,22,0),(23,23,0),(24,24,0),(25,25,0),(25,26,1),(26,26,0),(27,27,0),(27,28,1),(28,28,0),(29,29,0),(29,30,1),(30,30,0),(31,31,0);
/*!40000 ALTER TABLE `sys_department_closure` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_post`
--

LOCK TABLES `sys_post` WRITE;
/*!40000 ALTER TABLE `sys_post` DISABLE KEYS */;
INSERT INTO `sys_post` (`id`, `name`, `created_at`, `updated_at`) VALUES (22,'系统管理岗','2026-09-10 10:18:42','2026-09-10 13:35:30'),(28,'业务经理','2026-09-10 13:35:14','2026-09-10 13:35:14'),(29,'核算岗','2026-09-10 13:35:34','2026-09-10 13:35:34'),(30,'公司领导','2026-09-10 13:54:27','2026-09-10 13:54:27'),(31,'执行总经理','2026-09-10 13:54:37','2026-09-10 13:54:37'),(32,'中心负责人','2026-09-10 13:54:43','2026-09-10 13:54:43'),(34,'部门负责人','2026-09-10 13:54:52','2026-09-10 13:54:52'),(36,'会计主管/内控','2026-09-10 16:20:38','2026-09-10 16:22:01'),(37,'会计','2026-09-10 16:20:48','2026-09-10 16:20:48'),(38,'内控主管','2026-09-10 16:20:59','2026-09-10 16:20:59'),(39,'内控专员','2026-09-10 16:21:13','2026-09-10 16:21:13'),(40,'法务','2026-09-10 16:22:18','2026-09-10 16:22:18'),(41,'法务主管','2026-09-10 16:22:28','2026-09-10 16:22:28'),(42,'行政专员','2026-09-10 16:22:34','2026-09-10 16:22:34'),(43,'行政主管','2026-09-10 16:22:40','2026-09-10 16:22:40'),(44,'商务专员','2026-09-10 16:22:52','2026-09-10 16:22:52'),(45,'运营服务主管','2026-09-10 16:23:14','2026-09-10 16:23:14'),(46,'交付主管','2026-09-10 16:23:22','2026-09-10 16:23:22'),(47,'财务经理','2026-09-10 16:23:28','2026-09-10 16:23:28'),(48,'出纳主管','2026-09-10 16:23:33','2026-09-10 16:23:33'),(49,'出纳','2026-09-10 16:23:39','2026-09-10 16:23:39'),(50,'系统管理员','2026-09-10 16:48:00','2026-09-10 16:48:00');
/*!40000 ALTER TABLE `sys_post` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_permission`
--

LOCK TABLES `sys_permission` WRITE;
/*!40000 ALTER TABLE `sys_permission` DISABLE KEYS */;
INSERT INTO `sys_permission` (`id`, `code`, `name`, `description`, `created_at`) VALUES (1,'VIEW_OWN_FORMS','可访问单据','查看本人提交的表单','2026-09-02 16:41:02'),(4,'SUBMIT_ALL_FORMS','可提交单据','提交全部业务类型表单','2026-09-02 16:41:02'),(9,'APPROVE_ALL_NODES','超级审批（代审任意节点）','超级管理员审批任意节点','2026-09-02 16:41:02'),(11,'CONFIGURE_FLOW_PERMISSION','配置流程与权限','维护流程、角色和权限','2026-09-02 16:41:02');
/*!40000 ALTER TABLE `sys_permission` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` (`id`, `name`, `department`, `department_id`, `post`, `data_scope_id`, `data_scope`, `permissions`, `members`, `created_at`, `updated_at`) VALUES (20,'超级管理员','总经办',16,'系统管理岗',20,NULL,NULL,NULL,'2026-09-10 10:18:42','2026-09-10 13:35:31'),(22,'部门负责人','总经办',NULL,'部门负责人',23,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-12 14:35:34'),(24,'交付主管','交付部',30,'交付主管',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(25,'会计主管&内控','财务中心',20,'会计主管/内控',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(26,'公司领导','总经办',16,'公司领导',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(27,'内控专员','内控部',23,'内控专员',21,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(28,'内控主管','内控部',23,'内控主管',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(29,'出纳','资管中心',31,'出纳',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(30,'出纳主管','资管中心',31,'出纳主管',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(31,'商务专员','运营服务部',28,'商务专员',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(32,'执行总经理','总经办',16,'执行总经理',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(33,'核算会计','财务部',22,'会计',24,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-11 15:39:06'),(34,'法务','法务部',24,'法务',21,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(35,'法务主管','法务部',24,'法务主管',22,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-12 14:36:38'),(36,'行政专员','行政部',26,'行政专员',21,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(37,'行政主管','行政部',26,'行政主管',21,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(38,'财务经理','财务部',22,'财务经理',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(39,'运营服务主管','运营服务部',28,'运营服务主管',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(40,'普通员工',NULL,NULL,'普通员工',21,NULL,NULL,NULL,'2026-09-11 15:47:20','2026-09-11 15:47:20');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_role_permission`
--

LOCK TABLES `sys_role_permission` WRITE;
/*!40000 ALTER TABLE `sys_role_permission` DISABLE KEYS */;
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (20,1),(22,1),(24,1),(25,1),(26,1),(27,1),(28,1),(29,1),(30,1),(31,1),(32,1),(33,1),(34,1),(35,1),(36,1),(37,1),(38,1),(39,1),(40,1),(20,4),(22,4),(24,4),(25,4),(26,4),(27,4),(28,4),(29,4),(30,4),(31,4),(32,4),(33,4),(34,4),(35,4),(36,4),(37,4),(38,4),(39,4),(40,4),(20,9),(20,11);
/*!40000 ALTER TABLE `sys_role_permission` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_role_scope_department`
--

LOCK TABLES `sys_role_scope_department` WRITE;
/*!40000 ALTER TABLE `sys_role_scope_department` DISABLE KEYS */;
INSERT INTO `sys_role_scope_department` (`role_id`, `department_id`) VALUES (33,16),(33,27),(33,28),(33,29),(33,30),(33,31);
/*!40000 ALTER TABLE `sys_role_scope_department` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `form_template`
--

LOCK TABLES `form_template` WRITE;
/*!40000 ALTER TABLE `form_template` DISABLE KEYS */;
INSERT INTO `form_template` (`id`, `business_type`, `name`, `doc_prefix`, `flow_config_id`, `version`, `status`, `sort_order`, `category`, `attachment_requirements`) VALUES (1,'通用审批单','通用审批单','SP',24,2,'ENABLED',0,'DAILY_PAYMENT','[\"关联前置单据\",\"业务证明资料\",\"发票\",\"收款信息\"]');
/*!40000 ALTER TABLE `form_template` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `form_field`
--

LOCK TABLES `form_field` WRITE;
/*!40000 ALTER TABLE `form_field` DISABLE KEYS */;
INSERT INTO `form_field` (`id`, `template_id`, `field_key`, `label`, `control_type`, `required`, `options`, `reserved`, `sort_order`, `enabled`) VALUES (40,1,'title','单据标题','TEXT',1,'[]',1,1,1),(41,1,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(42,1,'amount','金额','NUMBER',1,'[]',1,3,1),(43,1,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(44,1,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(45,1,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(46,1,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(47,1,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(48,1,'businessMode','业务模式','SELECT',0,'[\"标准\",\"非标\"]',1,9,1),(49,1,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(50,1,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(51,1,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务章\"]',0,12,1),(52,1,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(53,1,'sealTime','用印时间','DATE',0,'[]',0,14,1),(54,1,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(55,1,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1);
/*!40000 ALTER TABLE `form_field` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `flow_config`
--

LOCK TABLES `flow_config` WRITE;
/*!40000 ALTER TABLE `flow_config` DISABLE KEYS */;
INSERT INTO `flow_config` (`id`, `type`, `category`, `nodes`, `created_at`, `updated_at`) VALUES (24,'费用报销','DAILY',NULL,'2026-09-12 11:37:53','2026-09-12 11:37:53');
/*!40000 ALTER TABLE `flow_config` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `flow_node_config`
--

LOCK TABLES `flow_node_config` WRITE;
/*!40000 ALTER TABLE `flow_node_config` DISABLE KEYS */;
INSERT INTO `flow_node_config` (`id`, `flow_config_id`, `name`, `node_type`, `assignee_role`, `sort_order`, `cc_targets`) VALUES (286,24,'发起人','START',NULL,0,NULL),(287,24,'直属主管','APPROVAL','直属主管',1,NULL),(288,24,'会计（按部门）','APPROVAL','会计（按部门）',2,NULL),(289,24,'会计主管&内控','APPROVAL','会计主管&内控',3,NULL),(290,24,'公司领导（大额）','APPROVAL','公司领导',4,NULL),(291,24,'出纳','APPROVAL','出纳',5,NULL),(292,24,'抄送相关负责人','CC',NULL,6,'[{\"type\": \"ROLE\", \"value\": \"会计主管&内控\"}, {\"type\": \"ROLE\", \"value\": \"出纳\"}]');
/*!40000 ALTER TABLE `flow_node_config` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `flow_condition_rule`
--

LOCK TABLES `flow_condition_rule` WRITE;
/*!40000 ALTER TABLE `flow_condition_rule` DISABLE KEYS */;
INSERT INTO `flow_condition_rule` (`id`, `flow_config_id`, `variable_name`, `operator`, `expected_value`, `target_node_name`, `sort_order`) VALUES (37,24,'amount','GREATER_THAN_OR_EQUAL','20000','公司领导（大额）',0);
/*!40000 ALTER TABLE `flow_condition_rule` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_user_service_dept`
--

LOCK TABLES `sys_user_service_dept` WRITE;
/*!40000 ALTER TABLE `sys_user_service_dept` DISABLE KEYS */;
INSERT INTO `sys_user_service_dept` (`id`, `user_id`, `department_id`) VALUES (2,64,22);
/*!40000 ALTER TABLE `sys_user_service_dept` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `doc_seq`
--

LOCK TABLES `doc_seq` WRITE;
/*!40000 ALTER TABLE `doc_seq` DISABLE KEYS */;
INSERT INTO `doc_seq` (`seq_date`, `seq_value`) VALUES ('20260902',2),('20260907',1),('20260908',5),('20260909',13),('20260912',9);
/*!40000 ALTER TABLE `doc_seq` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:57
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_user_department`
--

LOCK TABLES `sys_user_department` WRITE;
/*!40000 ALTER TABLE `sys_user_department` DISABLE KEYS */;
INSERT INTO `sys_user_department` (`id`, `user_id`, `department_id`, `primary_department`) VALUES (86,62,22,0),(89,61,22,0),(92,63,20,0);
/*!40000 ALTER TABLE `sys_user_department` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:58
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_user`
--
-- WHERE:  account <> 'test001'

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` (`id`, `name`, `job_no`, `account`, `password`, `department`, `department_id`, `post`, `post_id`, `status`, `manager_id`, `created_at`, `updated_at`) VALUES (42,'超级管理员','HXJ000','admin','$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW','总经办',16,'系统管理岗',22,'ACTIVE',NULL,'2026-09-10 10:18:42','2026-09-10 14:44:37'),(44,'林安然','HXJ001','linanran','$2a$10$GNjz64rBH0.wWKzY1YBWN.BS19WmRKzIW8D34cWxmCjrJ49I2pUTW','总经办',16,'系统管理员',50,'ACTIVE',NULL,'2026-09-10 16:48:50','2026-09-10 16:48:50'),(45,'连力','HXJ002','lianli','$2a$10$.vW4ukt3wGxubcKasIAiAe3Xwchbc3RzsKQLC0I.bLFgwszb4REyW','总经办',16,'公司领导',30,'ACTIVE',NULL,'2026-09-10 16:48:50','2026-09-10 16:48:50'),(46,'王强','HXJ003','wangqiang','$2a$10$SHfmYgLsXWS3x14jzcGpn.Fe0Tnft7IRWmqpv6sCv8w3JKH2LH0KC','总经办',16,'公司领导',30,'ACTIVE',45,'2026-09-10 16:48:50','2026-09-10 16:48:50'),(47,'舒飞','HXJ004','shufei','$2a$10$bi1A.G6plMFdwrKAGlpjzew4DwgFAJaWfRkeeoP/F8LmgTJZQ3K8m','业务支持中心',27,'中心负责人',32,'ACTIVE',46,'2026-09-10 16:48:50','2026-09-10 16:48:50'),(48,'卢乙芬','HXJ005','luyifen','$2a$10$DGhQOQg7mkM4eE43ixv5c.bDgRJAhvl7cPmr7g/zWLL4Qr/CUlcuq','人力行政中心',25,'中心负责人',32,'ACTIVE',46,'2026-09-10 16:48:50','2026-09-10 16:48:50'),(49,'刘婷','HXJ006','liuting','$2a$10$AP7j2D5p6y0/chZeArWei.mJqVVUzRSSE12ikNnAXka.pIu.Z7zFa','资管中心',31,'出纳主管',48,'ACTIVE',46,'2026-09-10 16:48:50','2026-09-10 16:48:50'),(50,'王海亮','HXJ007','wanghailiang','$2a$10$yD7RcTXEeMUPc.JRJ.8cee3VADlRMbIuI3P7cqqbOxb5KZcnFYwW2','供应链中心',29,'中心负责人',32,'ACTIVE',46,'2026-09-10 16:50:55','2026-09-10 16:50:55'),(51,'张龙','HXJ008','zhanglong','$2a$10$Oww4x4g9zIKupEQBWpR/6e6wGrdCFMRwshln70/rBKe.Xk7ZSustW','交付部',30,'交付主管',46,'ACTIVE',46,'2026-09-10 16:50:55','2026-09-10 16:50:55'),(52,'刘佳慧','HXJ009','liujiahui','$2a$10$PHOrkVF16mgFvhAnLDF9C.bnyUm5QFzzFJjSFhld07G29EQj0GtiC','行政部',26,'行政主管',43,'ACTIVE',54,'2026-09-10 16:53:09','2026-09-10 16:53:13'),(53,'陈成辉','HXJ010','chenchenghui','$2a$10$SeJBhMc6hqTow5Zqfmmx7.OiJdfEFAJX2aKLK1vMW7OR/DlpKFIT2','财务部',22,'部门负责人',34,'ACTIVE',46,'2026-09-10 16:53:10','2026-09-10 16:53:13'),(54,'郭静宇','HXJ011','guojingyu','$2a$10$ZnM6Rn8D8i9ebiBFuWaEJu1zEeVQiYswZN49Bb8HqdlfVl.SKTLM6','行政部',26,'部门负责人',34,'ACTIVE',48,'2026-09-10 16:53:10','2026-09-10 16:53:13'),(55,'吴荷珍','HXJ012','wuhezhen','$2a$10$wpGeuWUZMbZCMBv61lTpcub6M2bA9FePtm5.Q41vXul9onCWSqXou','财务部',22,'财务经理',47,'ACTIVE',53,'2026-09-10 16:53:10','2026-09-10 16:53:13'),(56,'王莹','HXJ013','wangying','$2a$10$LNIjPo42uDJIIDtdRLkXfO6sYJ9LgB8Id1dAFWHbkgF0RXE9lz.lu','运营服务部',28,'部门负责人',34,'ACTIVE',47,'2026-09-10 16:53:10','2026-09-10 16:53:13'),(57,'黄政哲','HXJ014','huangzhengzhe','$2a$10$nvF4IzEsyrecTtGwLY7/YOeMuKjnrqvlccC72vay6.keJ4YUjTZb2','运营服务部',28,'运营服务主管',45,'ACTIVE',56,'2026-09-10 16:53:10','2026-09-10 16:53:13'),(58,'李林华','HXJ015','lilinhua','$2a$10$7NlZW0o9M9laZgU8r40v4e1eLUD9PzZ8FfmsUAWFOEDOHHS8JwuPC','交付部',30,'部门负责人',34,'ACTIVE',50,'2026-09-10 16:53:10','2026-09-10 16:53:13'),(59,'苗福鑫','HXJ016','miaofuxin','$2a$10$ool/3vo8Sxg85qFErCG3YOhfD96Q993whH731NhBCf6lsTRP.w4H6','内控部',23,'部门负责人',34,'ACTIVE',46,'2026-09-10 16:53:10','2026-09-10 16:53:13'),(60,'翁建发','HXJ017','wengjianfa','$2a$10$n7ke0IUGYfeVbte40GyTLuxueKGm9QI2UMilNe8Vc0k0BGLnRh1BC','法务部',24,'部门负责人',34,'ACTIVE',46,'2026-09-10 16:53:10','2026-09-10 16:53:13'),(61,'汪洋','HXJ018','wangyang','$2a$10$Nara0EGzEEJi0eHp5sEqMe1mdlXcN6yjzQAL3QdulVTBvBdNS4vLS','财务中心',20,'会计主管/内控',36,'ACTIVE',46,'2026-09-10 16:53:10','2026-09-10 16:53:13'),(62,'姚志豪','HXJ019','yaozhihao','$2a$10$nEEO/OJ9PNyQM6TDbjTrPepwk58q6WOulStmXW542IoU546Q7lavi','财务中心',20,'会计主管/内控',36,'ACTIVE',46,'2026-09-10 16:53:11','2026-09-10 16:53:13'),(63,'翁婷婷','HXJ020','wengtingting','$2a$10$0AGRtjcHmlmbZQ3Z5jTq5.RX1ej76KF/mXTZXHl2hqy7yySuSvPce','财务中心',31,'会计主管/内控',49,'ACTIVE',46,'2026-09-10 16:53:11','2026-09-11 13:46:02'),(64,'冯训漪','HXJ021','fengxunyi','$2a$10$wefnmlxdt1mndiNnDLhZSeQqWZIQLb4WA00Bd9SKZ5fkA3EPVFbiG','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:11','2026-09-10 16:53:13'),(65,'朱昀怡','HXJ022','zhuyunyi','$2a$10$BQFg7uyij8qPEyrWXtjjDee4tKcTc9F3G9GvmV4tH2hjUq8i9tJ7O','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:11','2026-09-10 16:53:13'),(66,'施惠君','HXJ023','shihuijun','$2a$10$uqw6b8lB/YYvsL31iy18eeSJr2iWCO.DYAret1WjtClkynQnbh0fq','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:11','2026-09-10 16:53:13'),(67,'蔡赐绵','HXJ024','caicimian','$2a$10$Bqe3VTfGMl8G5aU3VBHKw.4YMmaQUGkLC3mi4aKA.b7I6/29wJO6S','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:11','2026-09-10 16:53:13'),(68,'李小妹','HXJ025','lixiaomei','$2a$10$jBg.wxxKyyp5Mjv4AtA/YeE61507czkIPn96sKT2GTRah//46QBSW','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:11','2026-09-10 16:53:13'),(69,'石瀚文','HXJ026','shihanwen','$2a$10$.ZXxZX0jXatDLq0Z0vj/PeqxoBLKZpVKsTM8nake8hJAm794rUBQi','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:11','2026-09-10 16:53:13'),(70,'张欣怡','HXJ027','zhangxinyi','$2a$10$FSg4/kjJ2Illb8uNSm60GedPWyWahsC/0Amo/8qxHT.eLb/TKE9wy','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:12','2026-09-10 16:53:13'),(71,'温超群','HXJ028','wenchaoqun','$2a$10$vGd8DOw3SeirttciNEpfH.unDAZtLhFzVcUEybMdcAhyaBvHyXcQS','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:12','2026-09-10 16:53:13'),(72,'孙倩倩','HXJ029','sunqianqian','$2a$10$RD3H0MCaHTG9SRWCzIzPoO9ZVi8oweRudXi2duCZZ.ufH24OLewKS','内控部',23,'内控主管',38,'ACTIVE',59,'2026-09-10 16:53:12','2026-09-10 16:53:13'),(73,'郑宁静','HXJ030','zhengningjing','$2a$10$SOw/ZFwXzfK7iS8teVYWfe06k4eVNvUoUpuSFOOZ0F9jWbGwdoQ2S','内控部',23,'内控专员',39,'ACTIVE',72,'2026-09-10 16:53:12','2026-09-10 16:53:13'),(74,'唐菁蔚','HXJ031','tangjingwei','$2a$10$n/7DYhCtWOXmBw.o2oG6L.ULy/N5WkZ.evzhlXPAJWkQUbua3lpLS','法务部',24,'法务',40,'ACTIVE',75,'2026-09-10 16:53:12','2026-09-10 16:53:13'),(75,'陈楠','HXJ032','chennan','$2a$10$KacDozfbOhMn5kmscBFmCu9pFkCaCmLgGbtYL9BioXuvrsj6StaBa','法务部',24,'法务主管',41,'ACTIVE',60,'2026-09-10 16:53:12','2026-09-10 16:53:13'),(76,'朱铭骏','HXJ033','zhumingjun','$2a$10$ZsRipWdrw20upluSMr6mn.H9yjHax3XHvVzd.bjLUepD91BrIs00K','行政部',26,'行政专员',42,'ACTIVE',52,'2026-09-10 16:53:12','2026-09-10 16:53:13'),(77,'刘颖宁','HXJ034','liuyingning','$2a$10$r7JnrFXt6XcuiSRynMiMwOTLzfmJJi7U5Lit4xuOwoRgoS6/GP9ne','运营服务部',28,'商务专员',44,'ACTIVE',57,'2026-09-10 16:53:12','2026-09-10 16:53:13'),(78,'杨淑欢','HXJ035','yangshuhuan','$2a$10$nc99of3Avthj92dKwUrlJuY0mKQC/VjpB7U6.dXbFE90jgM8YiOKW','运营服务部',28,'商务专员',44,'ACTIVE',57,'2026-09-10 16:53:12','2026-09-10 16:53:13'),(79,'龚蓉','HXJ036','gongrong','$2a$10$C9PPgQC4sw4gyxHIP4NKmeT17UlpdkL32GhmIdokq7a.oMwckK4vm','运营服务部',28,'商务专员',44,'ACTIVE',57,'2026-09-10 16:53:12','2026-09-10 16:53:13'),(80,'林丽婷','HXJ037','linliting','$2a$10$BWizc02WlGfxGNLqiNxRkuw1cWatbQwHTSBuApDDhA/0UqPR9Jh3.','资管中心',31,'出纳',49,'ACTIVE',49,'2026-09-10 16:53:12','2026-09-10 16:53:13'),(81,'陈伟璇','HXJ038','chenweixuan','$2a$10$q9Nobgi3SnC0JVPVudwbUeSX5ff98XIHoVeK7JXwRu3/9bJUiAyHi','资管中心',31,'出纳',49,'ACTIVE',49,'2026-09-10 16:53:13','2026-09-10 16:53:13'),(82,'沈盼静','HXJ039','shenpanjing','$2a$10$YWRCHM/zc78z8jp50FTdSuVer/zEORkNtVhq0koaHMyA4qYY1.ncy','资管中心',31,'出纳',49,'ACTIVE',49,'2026-09-10 16:53:13','2026-09-10 16:53:13'),(83,'李展仪','HXJ040','lizhanyi','$2a$10$RY5FzoU3bRKVu9wghgOnielLLvmU8kyKQVhWM95XnXiNPlQC2Yl6e','资管中心',31,'出纳',49,'ACTIVE',49,'2026-09-10 16:53:13','2026-09-10 16:53:13');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:58
-- MySQL dump 10.13  Distrib 8.0.46, for macos26.4 (arm64)
--
-- Host: localhost    Database: oa
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_user_role`
--
-- WHERE:  user_id <> 84

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (42,20),(44,20),(46,22),(47,22),(48,22),(49,22),(50,22),(51,22),(52,22),(53,22),(54,22),(55,22),(56,22),(57,22),(58,22),(59,22),(60,22),(51,24),(61,25),(62,25),(63,25),(45,26),(46,26),(73,27),(72,28),(63,29),(80,29),(81,29),(82,29),(83,29),(49,30),(77,31),(78,31),(79,31),(46,32),(61,33),(62,33),(64,33),(65,33),(66,33),(67,33),(68,33),(69,33),(70,33),(71,33),(74,34),(75,35),(52,36),(76,36),(52,37),(55,38),(57,39);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 15:13:58


-- ============ 流程配置 id 归一（基线仅一条流程） ============
UPDATE flow_config SET id = 1;
UPDATE flow_node_config SET flow_config_id = 1;
UPDATE flow_condition_rule SET flow_config_id = 1;
UPDATE form_template SET flow_config_id = 1 WHERE flow_config_id IS NOT NULL;

-- ============ 汇报线重建（规则：成员→部门主管负责人→中心负责人→执行总经理） ============
UPDATE sys_user SET manager_id = NULL;
UPDATE sys_user u JOIN sys_user m ON m.account = 'lianli' SET u.manager_id = m.id WHERE u.account = 'wangqiang';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wangqiang' SET u.manager_id = m.id WHERE u.account = 'shufei';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wangqiang' SET u.manager_id = m.id WHERE u.account = 'luyifen';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wangqiang' SET u.manager_id = m.id WHERE u.account = 'liuting';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wangqiang' SET u.manager_id = m.id WHERE u.account = 'wanghailiang';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wangqiang' SET u.manager_id = m.id WHERE u.account = 'miaofuxin';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wangqiang' SET u.manager_id = m.id WHERE u.account = 'wengjianfa';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wangqiang' SET u.manager_id = m.id WHERE u.account = 'chenchenghui';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wangqiang' SET u.manager_id = m.id WHERE u.account = 'wuhezhen';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wangqiang' SET u.manager_id = m.id WHERE u.account = 'wangyang';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wangqiang' SET u.manager_id = m.id WHERE u.account = 'yaozhihao';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wanghailiang' SET u.manager_id = m.id WHERE u.account = 'zhanglong';
UPDATE sys_user u JOIN sys_user m ON m.account = 'zhanglong' SET u.manager_id = m.id WHERE u.account = 'lilinhua';
UPDATE sys_user u JOIN sys_user m ON m.account = 'shufei' SET u.manager_id = m.id WHERE u.account = 'huangzhengzhe';
UPDATE sys_user u JOIN sys_user m ON m.account = 'shufei' SET u.manager_id = m.id WHERE u.account = 'wangying';
UPDATE sys_user u JOIN sys_user m ON m.account = 'luyifen' SET u.manager_id = m.id WHERE u.account = 'liujiahui';
UPDATE sys_user u JOIN sys_user m ON m.account = 'luyifen' SET u.manager_id = m.id WHERE u.account = 'guojingyu';
UPDATE sys_user u JOIN sys_user m ON m.account = 'liujiahui' SET u.manager_id = m.id WHERE u.account = 'zhumingjun';
UPDATE sys_user u JOIN sys_user m ON m.account = 'huangzhengzhe' SET u.manager_id = m.id WHERE u.account = 'liuyingning';
UPDATE sys_user u JOIN sys_user m ON m.account = 'huangzhengzhe' SET u.manager_id = m.id WHERE u.account = 'yangshuhuan';
UPDATE sys_user u JOIN sys_user m ON m.account = 'huangzhengzhe' SET u.manager_id = m.id WHERE u.account = 'gongrong';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wuhezhen' SET u.manager_id = m.id WHERE u.account = 'fengxunyi';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wuhezhen' SET u.manager_id = m.id WHERE u.account = 'zhuyunyi';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wuhezhen' SET u.manager_id = m.id WHERE u.account = 'shihuijun';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wuhezhen' SET u.manager_id = m.id WHERE u.account = 'caicimian';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wuhezhen' SET u.manager_id = m.id WHERE u.account = 'lixiaomei';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wuhezhen' SET u.manager_id = m.id WHERE u.account = 'shihanwen';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wuhezhen' SET u.manager_id = m.id WHERE u.account = 'zhangxinyi';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wuhezhen' SET u.manager_id = m.id WHERE u.account = 'wenchaoqun';
UPDATE sys_user u JOIN sys_user m ON m.account = 'miaofuxin' SET u.manager_id = m.id WHERE u.account = 'sunqianqian';
UPDATE sys_user u JOIN sys_user m ON m.account = 'miaofuxin' SET u.manager_id = m.id WHERE u.account = 'zhengningjing';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wengjianfa' SET u.manager_id = m.id WHERE u.account = 'tangjingwei';
UPDATE sys_user u JOIN sys_user m ON m.account = 'wengjianfa' SET u.manager_id = m.id WHERE u.account = 'chennan';
UPDATE sys_user u JOIN sys_user m ON m.account = 'liuting' SET u.manager_id = m.id WHERE u.account = 'linliting';
UPDATE sys_user u JOIN sys_user m ON m.account = 'liuting' SET u.manager_id = m.id WHERE u.account = 'chenweixuan';
UPDATE sys_user u JOIN sys_user m ON m.account = 'liuting' SET u.manager_id = m.id WHERE u.account = 'shenpanjing';
UPDATE sys_user u JOIN sys_user m ON m.account = 'liuting' SET u.manager_id = m.id WHERE u.account = 'lizhanyi';
UPDATE sys_user u JOIN sys_user m ON m.account = 'liuting' SET u.manager_id = m.id WHERE u.account = 'wengtingting';

SET FOREIGN_KEY_CHECKS = 1;
