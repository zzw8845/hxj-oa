-- =====================================================================
-- V1 基线：海峡金 OA 全量 schema + 干净种子数据（压平重建 v5，2026-09-14）
-- v5 变更：表单管理对齐钉钉生命周期——workbench_entry 加 auto_created 标记：
--   建模板自动生成同名事项入口（读时文案跟随模板名）；删模板级联清除事项；
--   模板改名入口跟随。手动事项（差旅费等事由快捷）不受影响
-- 其余决策同 v4（git 7be65d8）
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
) ENGINE=InnoDB AUTO_INCREMENT=73 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批记录表';
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='归档台账表';
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
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程条件分支规则表';
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
) ENGINE=InnoDB AUTO_INCREMENT=44 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程配置表';
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
) ENGINE=InnoDB AUTO_INCREMENT=449 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程节点配置表';
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
  `field_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `control_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `required` tinyint(1) NOT NULL DEFAULT '0',
  `options` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `reserved` tinyint(1) NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_field` (`template_id`,`field_key`),
  CONSTRAINT `fk_form_field_template` FOREIGN KEY (`template_id`) REFERENCES `form_template` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=419 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `form_template`
--

DROP TABLE IF EXISTS `form_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `form_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `business_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `doc_prefix` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `flow_config_id` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '1',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `sort_order` int NOT NULL DEFAULT '0',
  `category` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_requirements` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_form_template_name` (`name`),
  KEY `idx_form_template_business_type` (`business_type`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
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
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='附件表';
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
) ENGINE=InnoDB AUTO_INCREMENT=44 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单据表';
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

--
-- Table structure for table `workbench_entry`
--

DROP TABLE IF EXISTS `workbench_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workbench_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `zone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `hint` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `template_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `auto_created` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=53 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-14 16:39:00

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

-- Dump completed on 2026-09-14 16:39:00
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

-- Dump completed on 2026-09-14 16:39:00
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

-- Dump completed on 2026-09-14 16:39:00
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

-- Dump completed on 2026-09-14 16:39:00
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

-- Dump completed on 2026-09-14 16:39:00
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
INSERT INTO `sys_role` (`id`, `name`, `department`, `department_id`, `post`, `data_scope_id`, `data_scope`, `permissions`, `members`, `created_at`, `updated_at`) VALUES (20,'超级管理员','总经办',16,'系统管理岗',20,NULL,NULL,NULL,'2026-09-10 10:18:42','2026-09-14 11:07:11'),(24,'交付主管','交付部',30,'交付主管',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(25,'会计主管&内控','财务中心',20,'会计主管/内控',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(26,'公司领导','总经办',16,'公司领导',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(27,'内控专员','内控部',23,'内控专员',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-14 12:05:24'),(28,'内控主管','内控部',23,'内控主管',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(29,'出纳','资管中心',31,'出纳',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(30,'出纳主管','资管中心',31,'出纳主管',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(31,'商务专员','运营服务部',28,'商务专员',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(32,'执行总经理','总经办',16,'执行总经理',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-12 17:30:50'),(33,'核算会计','财务部',22,'会计',24,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-11 15:39:06'),(34,'法务','法务部',24,'法务',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-14 12:05:24'),(35,'法务主管','法务部',24,'法务主管',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-14 12:05:24'),(36,'行政专员','行政部',26,'行政专员',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-14 12:05:24'),(37,'行政主管','行政部',26,'行政主管',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-14 12:05:24'),(38,'财务经理','财务部',22,'财务经理',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(39,'运营服务主管','运营服务部',28,'运营服务主管',20,NULL,NULL,NULL,'2026-09-10 16:48:00','2026-09-10 16:48:00'),(40,'普通员工',NULL,NULL,'普通员工',21,NULL,NULL,NULL,'2026-09-11 15:47:20','2026-09-11 15:47:20');
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

-- Dump completed on 2026-09-14 16:39:00
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
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (20,1),(24,1),(25,1),(26,1),(27,1),(28,1),(29,1),(30,1),(31,1),(32,1),(33,1),(34,1),(35,1),(36,1),(37,1),(38,1),(39,1),(40,1),(20,4),(24,4),(25,4),(26,4),(27,4),(28,4),(29,4),(30,4),(31,4),(32,4),(33,4),(34,4),(35,4),(36,4),(37,4),(38,4),(39,4),(40,4),(20,9),(20,11);
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

-- Dump completed on 2026-09-14 16:39:00
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
INSERT INTO `sys_role_scope_department` (`role_id`, `department_id`) VALUES (33,16),(33,22),(33,27),(33,28),(33,29),(33,30),(33,31);
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

-- Dump completed on 2026-09-14 16:39:00
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
-- Dumping data for table `workbench_entry`
--

LOCK TABLES `workbench_entry` WRITE;
/*!40000 ALTER TABLE `workbench_entry` DISABLE KEYS */;
INSERT INTO `workbench_entry` (`id`, `zone`, `label`, `hint`, `template_id`, `sort_order`, `auto_created`) VALUES (1,'DAILY','闭店','入驻店销和代销',3,1,1),(2,'DAILY','采购申请','物资与服务采购',17,2,1),(3,'DAILY','低值易耗品领用','办公用品领用',18,3,1),(4,'DAILY','费用预算','部门费用预算申报',6,4,1),(5,'DAILY','公司发文申请','公司红头文件发布',7,5,1),(6,'DAILY','固定资产报废报损','资产报废与报损',8,6,1),(7,'DAILY','固定资产调拨','部门间资产调拨',9,7,1),(8,'DAILY','固定资产入库','新增资产登记',10,8,1),(9,'DAILY','黄金业务开户','黄金交易开户',20,9,1),(10,'DAILY','借款申请','员工与部门借款',11,10,1),(11,'DAILY','客户交易手续费调整','客户费率调整',12,11,1),(12,'DAILY','客户结算服务费','结算服务费核算',13,12,1),(13,'DAILY','提货人新增或变更','提货人信息管理',14,13,1),(14,'DAILY','业务招待申请','业务招待费',15,14,1),(15,'DAILY','银行账户管理调整','账户开立与变更',16,15,1),(16,'DAILY','差旅费','交通、住宿、餐费',1,16,0),(17,'DAILY','招待费','业务招待',15,17,0),(18,'DAILY','员工加班交通费','加班交通报销',1,18,0),(19,'DAILY','检测费证书费','检测认证费用',1,19,0),(20,'DAILY','快递费','快递物流费用',1,20,0),(21,'DAILY','水电网络费','水电与网络费用',1,21,0),(22,'DAILY','日常费用','水费等零星支出',1,22,0),(23,'DAILY','租金-物业费','房租与物业费用',1,23,0),(24,'DAILY','装修费','场地装修改造',1,24,0),(25,'DAILY','技术服务费','技术服务采购',1,25,0),(26,'DAILY','验货专项服务费','验货服务费用',1,26,0),(27,'DAILY','广告费','广告投放费用',1,27,0),(28,'DAILY','宽带费-电费','宽带与电费',1,28,0),(29,'DAILY','固定资产采购','固定资产购置',17,29,0),(30,'DAILY','税盘缴费','税控盘服务费',1,30,0),(31,'DAILY','平台保证金','平台入驻保证金',4,31,0),(32,'DAILY','电商投流推广充值','电商推广预充值',4,32,0),(33,'DAILY','合作方退款','合作方款项退回',4,33,0),(34,'DAILY','付款利息','借款利息支付',4,34,0),(35,'BUSINESS','应付款申请','供应商货款支付',4,35,1),(36,'BUSINESS','加工费','委托加工费用',4,36,0),(37,'BUSINESS','直销-代销结算服务费','渠道结算服务费',4,37,0),(38,'BUSINESS','供应商货款','采购货款支付',4,38,0),(39,'BUSINESS','员工工资-社保-公积金','薪酬社保发放',4,39,0),(40,'BUSINESS','备用金申请','部门备用金',11,40,0),(41,'BUSINESS','银行账户规费利息','账户规费与利息',4,41,0),(42,'BUSINESS','招商风险业务资质保证金','招商资质保证金',4,42,0),(43,'BUSINESS','借款利息','借款利息结算',4,43,0),(44,'SEAL','非标合同审批及用印','合同用印',19,44,1),(45,'SEAL','通用审批申请','通用盖章事项',5,45,1),(46,'SEAL','用印及证照申请','用印与证照领取',21,46,1),(51,'DAILY','费用报销','费用报销申请',1,1,1);
/*!40000 ALTER TABLE `workbench_entry` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-14 16:39:00
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
INSERT INTO `form_template` (`id`, `business_type`, `name`, `doc_prefix`, `flow_config_id`, `version`, `status`, `sort_order`, `category`, `attachment_requirements`) VALUES (1,'DAILY_PAYMENT','通用审批单','SP',1,4,'ENABLED',0,'DAILY_PAYMENT','[\"关联前置单据\",\"业务证明资料\",\"发票\",\"收款信息\"]'),(3,'DAILY_PAYMENT','闭店（入驻店销和代销）','BX',25,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(4,'BUSINESS_PAYMENT','应付款申请','FK',27,1,'ENABLED',0,'BUSINESS_PAYMENT','[]'),(5,'SEAL_APPLICATION','通用审批申请','YY',36,1,'ENABLED',0,'SEAL_APPLICATION','[]'),(6,'DAILY_PAYMENT','费用预算','BX',26,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(7,'DAILY_PAYMENT','公司发文申请','BX',28,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(8,'DAILY_PAYMENT','固定资产报废报损','BX',29,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(9,'DAILY_PAYMENT','固定资产调拨','BX',30,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(10,'DAILY_PAYMENT','固定资产入库','BX',31,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(11,'DAILY_PAYMENT','借款申请','BX',32,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(12,'DAILY_PAYMENT','客户交易手续费调整','BX',33,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(13,'DAILY_PAYMENT','客户结算服务费','BX',34,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(14,'DAILY_PAYMENT','提货人新增或变更','BX',35,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(15,'DAILY_PAYMENT','业务招待申请','BX',37,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(16,'DAILY_PAYMENT','银行账户管理调整','BX',38,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(17,'DAILY_PAYMENT','采购申请','BX',39,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(18,'DAILY_PAYMENT','低值易耗品（含办公物品）领用','BX',40,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(19,'SEAL_APPLICATION','非标合同审批及用印','YY',41,1,'ENABLED',0,'SEAL_APPLICATION','[]'),(20,'DAILY_PAYMENT','黄金业务开户','BX',42,1,'ENABLED',0,'DAILY_PAYMENT','[]'),(21,'SEAL_APPLICATION','用印及证照申请','YY',43,1,'ENABLED',0,'SEAL_APPLICATION','[]');
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

-- Dump completed on 2026-09-14 16:39:00
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
INSERT INTO `form_field` (`id`, `template_id`, `field_key`, `label`, `control_type`, `required`, `options`, `reserved`, `sort_order`, `enabled`) VALUES (72,1,'title','单据标题','TEXT',1,'[]',1,1,1),(73,1,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(74,1,'amount','金额','NUMBER',1,'[]',1,3,1),(75,1,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(76,1,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(77,1,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(78,1,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(79,1,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(80,1,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(81,1,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(82,1,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(83,1,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(84,1,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(85,1,'sealTime','用印时间','DATE',0,'[]',0,14,1),(86,1,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(87,1,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(88,3,'title','单据标题','TEXT',1,'[]',1,1,1),(89,3,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(90,3,'amount','金额','NUMBER',1,'[]',1,3,1),(91,3,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(92,3,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(93,3,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(94,3,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(95,3,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(96,3,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(97,3,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(98,3,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(99,3,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(100,3,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(101,3,'sealTime','用印时间','DATE',0,'[]',0,14,1),(102,3,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(103,3,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(104,4,'title','单据标题','TEXT',1,'[]',1,1,1),(105,4,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(106,4,'amount','金额','NUMBER',1,'[]',1,3,1),(107,4,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(108,4,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(109,4,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(110,4,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(111,4,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(112,4,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(113,4,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(114,4,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(115,4,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(116,4,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(117,4,'sealTime','用印时间','DATE',0,'[]',0,14,1),(118,4,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(119,4,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(120,5,'title','单据标题','TEXT',1,'[]',1,1,1),(121,5,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(122,5,'amount','金额','NUMBER',1,'[]',1,3,1),(123,5,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(124,5,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(125,5,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(126,5,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(127,5,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(128,5,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(129,5,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(130,5,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(131,5,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(132,5,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(133,5,'sealTime','用印时间','DATE',0,'[]',0,14,1),(134,5,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(135,5,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(136,6,'title','单据标题','TEXT',1,'[]',1,1,1),(137,6,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(138,6,'amount','金额','NUMBER',1,'[]',1,3,1),(139,6,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(140,6,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(141,6,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(142,6,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(143,6,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(144,6,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(145,6,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(146,6,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(147,6,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(148,6,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(149,6,'sealTime','用印时间','DATE',0,'[]',0,14,1),(150,6,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(151,6,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(152,7,'title','单据标题','TEXT',1,'[]',1,1,1),(153,7,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(154,7,'amount','金额','NUMBER',1,'[]',1,3,1),(155,7,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(156,7,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(157,7,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(158,7,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(159,7,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(160,7,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(161,7,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(162,7,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(163,7,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(164,7,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(165,7,'sealTime','用印时间','DATE',0,'[]',0,14,1),(166,7,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(167,7,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(168,8,'title','单据标题','TEXT',1,'[]',1,1,1),(169,8,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(170,8,'amount','金额','NUMBER',1,'[]',1,3,1),(171,8,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(172,8,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(173,8,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(174,8,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(175,8,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(176,8,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(177,8,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(178,8,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(179,8,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(180,8,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(181,8,'sealTime','用印时间','DATE',0,'[]',0,14,1),(182,8,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(183,8,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(184,9,'title','单据标题','TEXT',1,'[]',1,1,1),(185,9,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(186,9,'amount','金额','NUMBER',1,'[]',1,3,1),(187,9,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(188,9,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(189,9,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(190,9,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(191,9,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(192,9,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(193,9,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(194,9,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(195,9,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(196,9,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(197,9,'sealTime','用印时间','DATE',0,'[]',0,14,1),(198,9,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(199,9,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(200,10,'title','单据标题','TEXT',1,'[]',1,1,1),(201,10,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(202,10,'amount','金额','NUMBER',1,'[]',1,3,1),(203,10,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(204,10,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(205,10,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(206,10,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(207,10,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(208,10,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(209,10,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(210,10,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(211,10,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(212,10,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(213,10,'sealTime','用印时间','DATE',0,'[]',0,14,1),(214,10,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(215,10,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(216,11,'title','单据标题','TEXT',1,'[]',1,1,1),(217,11,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(218,11,'amount','金额','NUMBER',1,'[]',1,3,1),(219,11,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(220,11,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(221,11,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(222,11,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(223,11,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(224,11,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(225,11,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(226,11,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(227,11,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(228,11,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(229,11,'sealTime','用印时间','DATE',0,'[]',0,14,1),(230,11,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(231,11,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(232,12,'title','单据标题','TEXT',1,'[]',1,1,1),(233,12,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(234,12,'amount','金额','NUMBER',1,'[]',1,3,1),(235,12,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(236,12,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(237,12,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(238,12,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(239,12,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(240,12,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(241,12,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(242,12,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(243,12,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(244,12,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(245,12,'sealTime','用印时间','DATE',0,'[]',0,14,1),(246,12,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(247,12,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(248,13,'title','单据标题','TEXT',1,'[]',1,1,1),(249,13,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(250,13,'amount','金额','NUMBER',1,'[]',1,3,1),(251,13,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(252,13,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(253,13,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(254,13,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(255,13,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(256,13,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(257,13,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(258,13,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(259,13,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(260,13,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(261,13,'sealTime','用印时间','DATE',0,'[]',0,14,1),(262,13,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(263,13,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(264,14,'title','单据标题','TEXT',1,'[]',1,1,1),(265,14,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(266,14,'amount','金额','NUMBER',1,'[]',1,3,1),(267,14,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(268,14,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(269,14,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(270,14,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(271,14,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(272,14,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(273,14,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(274,14,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(275,14,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(276,14,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(277,14,'sealTime','用印时间','DATE',0,'[]',0,14,1),(278,14,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(279,14,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(280,15,'title','单据标题','TEXT',1,'[]',1,1,1),(281,15,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(282,15,'amount','金额','NUMBER',1,'[]',1,3,1),(283,15,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(284,15,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(285,15,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(286,15,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(287,15,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(288,15,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(289,15,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(290,15,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(291,15,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(292,15,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(293,15,'sealTime','用印时间','DATE',0,'[]',0,14,1),(294,15,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(295,15,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(296,16,'title','单据标题','TEXT',1,'[]',1,1,1),(297,16,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(298,16,'amount','金额','NUMBER',1,'[]',1,3,1),(299,16,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(300,16,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(301,16,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(302,16,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(303,16,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(304,16,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(305,16,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(306,16,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(307,16,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(308,16,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(309,16,'sealTime','用印时间','DATE',0,'[]',0,14,1),(310,16,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(311,16,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(312,17,'title','单据标题','TEXT',1,'[]',1,1,1),(313,17,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(314,17,'amount','金额','NUMBER',1,'[]',1,3,1),(315,17,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(316,17,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(317,17,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(318,17,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(319,17,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(320,17,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(321,17,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(322,17,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(323,17,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(324,17,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(325,17,'sealTime','用印时间','DATE',0,'[]',0,14,1),(326,17,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(327,17,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(328,18,'title','单据标题','TEXT',1,'[]',1,1,1),(329,18,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(330,18,'amount','金额','NUMBER',1,'[]',1,3,1),(331,18,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(332,18,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(333,18,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(334,18,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(335,18,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(336,18,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(337,18,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(338,18,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(339,18,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(340,18,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(341,18,'sealTime','用印时间','DATE',0,'[]',0,14,1),(342,18,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(343,18,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(344,19,'title','单据标题','TEXT',1,'[]',1,1,1),(345,19,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(346,19,'amount','金额','NUMBER',1,'[]',1,3,1),(347,19,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(348,19,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(349,19,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(350,19,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(351,19,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(352,19,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(353,19,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(354,19,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(355,19,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(356,19,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(357,19,'sealTime','用印时间','DATE',0,'[]',0,14,1),(358,19,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(359,19,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(360,20,'title','单据标题','TEXT',1,'[]',1,1,1),(361,20,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(362,20,'amount','金额','NUMBER',1,'[]',1,3,1),(363,20,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(364,20,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(365,20,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(366,20,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(367,20,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(368,20,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(369,20,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(370,20,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(371,20,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(372,20,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(373,20,'sealTime','用印时间','DATE',0,'[]',0,14,1),(374,20,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(375,20,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1),(376,21,'title','单据标题','TEXT',1,'[]',1,1,1),(377,21,'company','所属公司','SELECT',1,'[\"海峡金\",\"海峡金供应链\"]',0,2,1),(378,21,'amount','金额','NUMBER',1,'[]',1,3,1),(379,21,'invoiceSummary','发票摘要','TEXT',0,'[]',0,4,1),(380,21,'reason','事由明细','TEXTAREA',1,'[]',0,5,1),(381,21,'contractNo','合同编号','TEXT',0,'[]',0,6,1),(382,21,'involvesFunds','是否涉及资金','BOOLEAN',0,'[]',1,7,1),(383,21,'requiresAdminReview','是否需行政复核','BOOLEAN',0,'[]',1,8,1),(384,21,'businessMode','业务模式','SELECT',0,'[\"店销\",\"代销\"]',1,9,1),(385,21,'needPostMaterial','是否后置补材料','BOOLEAN',0,'[]',1,10,1),(386,21,'sealProject','用印项目','TEXT',0,'[]',0,11,1),(387,21,'sealType','印章类型','SELECT',0,'[\"公章\",\"合同章\",\"法人章\",\"财务专用章\",\"发票专用章\"]',0,12,1),(388,21,'sealDepartment','用印部门','TEXT',0,'[]',0,13,1),(389,21,'sealTime','用印时间','DATE',0,'[]',0,14,1),(390,21,'sealFileName','用印文件名','TEXT',0,'[]',0,15,1),(391,21,'sealReason','用印事由','TEXTAREA',0,'[]',0,16,1);
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

-- Dump completed on 2026-09-14 16:39:00
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
INSERT INTO `flow_config` (`id`, `type`, `category`, `nodes`, `created_at`, `updated_at`) VALUES (1,'费用报销','DAILY',NULL,'2026-09-12 11:37:53','2026-09-12 15:45:36'),(25,'闭店（入驻店销和代销）','DAILY',NULL,'2026-09-14 11:22:22','2026-09-14 11:22:22'),(26,'费用预算','DAILY',NULL,'2026-09-14 11:22:24','2026-09-14 11:22:24'),(27,'应付款申请','BUSINESS',NULL,'2026-09-14 11:22:24','2026-09-14 11:22:24'),(28,'公司发文申请','DAILY',NULL,'2026-09-14 11:22:25','2026-09-14 11:22:25'),(29,'固定资产报废报损','DAILY',NULL,'2026-09-14 11:22:25','2026-09-14 11:22:25'),(30,'固定资产调拨','DAILY',NULL,'2026-09-14 11:22:25','2026-09-14 11:22:25'),(31,'固定资产入库','DAILY',NULL,'2026-09-14 11:22:25','2026-09-14 11:22:25'),(32,'借款申请','DAILY',NULL,'2026-09-14 11:22:26','2026-09-14 11:22:26'),(33,'客户交易手续费调整','DAILY',NULL,'2026-09-14 11:22:26','2026-09-14 11:22:26'),(34,'客户结算服务费','DAILY',NULL,'2026-09-14 11:22:26','2026-09-14 11:22:26'),(35,'提货人新增或变更','DAILY',NULL,'2026-09-14 11:22:27','2026-09-14 11:22:27'),(36,'通用审批申请','SEAL',NULL,'2026-09-14 11:22:27','2026-09-14 11:22:27'),(37,'业务招待申请','DAILY',NULL,'2026-09-14 11:22:27','2026-09-14 11:22:27'),(38,'银行账户管理调整','DAILY',NULL,'2026-09-14 11:22:27','2026-09-14 11:22:27'),(39,'采购申请','DAILY',NULL,'2026-09-14 11:22:51','2026-09-14 11:22:51'),(40,'低值易耗品（含办公物品）领用','DAILY',NULL,'2026-09-14 11:22:52','2026-09-14 11:22:52'),(41,'非标合同审批及用印','SEAL',NULL,'2026-09-14 11:22:52','2026-09-14 11:22:52'),(42,'黄金业务开户','DAILY',NULL,'2026-09-14 11:22:52','2026-09-14 11:22:52'),(43,'用印及证照申请','SEAL',NULL,'2026-09-14 11:22:52','2026-09-14 11:22:52');
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

-- Dump completed on 2026-09-14 16:39:00
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
INSERT INTO `flow_node_config` (`id`, `flow_config_id`, `name`, `node_type`, `assignee_role`, `sort_order`, `cc_targets`) VALUES (335,1,'发起人','START',NULL,0,NULL),(336,1,'直属主管','APPROVAL','直属主管',1,NULL),(337,1,'会计（按部门）','APPROVAL','会计（按部门）',2,NULL),(338,1,'会计主管&内控','APPROVAL','会计主管&内控',3,NULL),(339,1,'公司领导（大额）','APPROVAL','公司领导',4,NULL),(340,1,'出纳','APPROVAL','出纳',5,NULL),(341,1,'抄送相关负责人','CC',NULL,6,'[{\"type\":\"ROLE\",\"value\":\"会计主管&内控\"},{\"type\":\"ROLE\",\"value\":\"出纳\"}]'),(342,25,'发起人','START',NULL,0,NULL),(343,25,'直属主管','APPROVAL','直属主管',1,NULL),(344,25,'商务专员','APPROVAL','商务专员',2,NULL),(345,25,'执行总经理','APPROVAL','执行总经理',3,NULL),(346,25,'会计主管&内控','APPROVAL','会计主管&内控',4,NULL),(347,25,'出纳','APPROVAL','出纳',5,NULL),(348,25,'抄送相关负责人','CC',NULL,6,'[{\"type\": \"ROLE\", \"value\": \"会计主管&内控\"}, {\"type\": \"ROLE\", \"value\": \"出纳\"}]'),(349,26,'发起人','START',NULL,0,NULL),(350,26,'直属主管','APPROVAL','直属主管',1,NULL),(351,26,'会计主管&内控','APPROVAL','会计主管&内控',2,NULL),(352,26,'发起人自选抄送','CC',NULL,3,'[]'),(353,27,'发起人','START',NULL,0,NULL),(354,27,'直属主管','APPROVAL','直属主管',1,NULL),(355,27,'会计（按部门）','APPROVAL','会计（按部门）',2,NULL),(356,27,'会计主管&内控','APPROVAL','会计主管&内控',3,NULL),(357,27,'公司领导（大额）','APPROVAL','公司领导',4,NULL),(358,27,'出纳','APPROVAL','出纳',5,NULL),(359,27,'抄送相关负责人','CC',NULL,6,'[{\"type\": \"ROLE\", \"value\": \"会计主管&内控\"}, {\"type\": \"ROLE\", \"value\": \"出纳\"}]'),(360,28,'发起人','START',NULL,0,NULL),(361,28,'逐级主管','APPROVAL','直属主管',1,NULL),(362,28,'法务','APPROVAL','法务',2,NULL),(363,28,'内控主管','APPROVAL','内控主管',3,NULL),(364,28,'执行总经理','APPROVAL','执行总经理',4,NULL),(365,28,'行政专员编号发文','APPROVAL','行政专员',5,NULL),(366,28,'发起人自选抄送','CC',NULL,6,'[]'),(367,29,'发起人','START',NULL,0,NULL),(368,29,'资产归属部门确认','APPROVAL','直属主管',1,NULL),(369,29,'财务经理','APPROVAL','财务经理',2,NULL),(370,29,'执行总经理','APPROVAL','执行总经理',3,NULL),(371,29,'资产管理员办理','APPROVAL','行政专员',4,NULL),(372,29,'抄送管理人员','CC',NULL,5,'[{\"type\": \"ROLE\", \"value\": \"行政专员\"}, {\"type\": \"ROLE\", \"value\": \"财务经理\"}]'),(373,30,'发起人','START',NULL,0,NULL),(374,30,'调出部门确认','APPROVAL','直属主管',1,NULL),(375,30,'调入部门确认','APPROVAL','直属主管',2,NULL),(376,30,'资产管理员办理','APPROVAL','行政专员',3,NULL),(377,30,'抄送财务及内控','CC',NULL,4,'[{\"type\": \"ROLE\", \"value\": \"财务经理\"}, {\"type\": \"ROLE\", \"value\": \"内控专员\"}]'),(378,31,'发起人','START',NULL,0,NULL),(379,31,'直属主管','APPROVAL','直属主管',1,NULL),(380,31,'行政专员入库登记','APPROVAL','行政专员',2,NULL),(381,31,'抄送财务经理','CC',NULL,3,'[{\"type\": \"ROLE\", \"value\": \"财务经理\"}]'),(382,32,'发起人','START',NULL,0,NULL),(383,32,'直属主管','APPROVAL','直属主管',1,NULL),(384,32,'会计主管&内控','APPROVAL','会计主管&内控',2,NULL),(385,32,'会计处理','APPROVAL','核算会计',3,NULL),(386,32,'出纳付款并上传回单','APPROVAL','出纳',4,NULL),(387,32,'抄送财务及公司领导','CC',NULL,5,'[{\"type\": \"ROLE\", \"value\": \"财务经理\"}, {\"type\": \"ROLE\", \"value\": \"公司领导\"}]'),(388,33,'发起人','START',NULL,0,NULL),(389,33,'执行总经理','APPROVAL','执行总经理',1,NULL),(390,33,'抄送财务内控法务运营','CC',NULL,2,'[{\"type\": \"ROLE\", \"value\": \"财务经理\"}, {\"type\": \"ROLE\", \"value\": \"内控主管\"}, {\"type\": \"ROLE\", \"value\": \"法务\"}, {\"type\": \"ROLE\", \"value\": \"运营服务主管\"}]'),(391,34,'发起人','START',NULL,0,NULL),(392,34,'会计（按业务类型）','APPROVAL','会计（按部门）',1,NULL),(393,34,'财务经理','APPROVAL','财务经理',2,NULL),(394,34,'会计主管&内控','APPROVAL','会计主管&内控',3,NULL),(395,34,'出纳','APPROVAL','出纳',4,NULL),(396,34,'抄送相关负责人','CC',NULL,5,'[{\"type\": \"ROLE\", \"value\": \"财务经理\"}, {\"type\": \"ROLE\", \"value\": \"商务专员\"}]'),(397,35,'发起人','START',NULL,0,NULL),(398,35,'交付主管','APPROVAL','交付主管',1,NULL),(399,35,'抄送内控主管','CC',NULL,2,'[{\"type\": \"ROLE\", \"value\": \"内控主管\"}]'),(400,36,'发起人','START',NULL,0,NULL),(401,36,'逐级主管','APPROVAL','直属主管',1,NULL),(402,36,'会计主管&内控（涉及资金）','APPROVAL','会计主管&内控',2,NULL),(403,36,'执行总经理','APPROVAL','执行总经理',3,NULL),(404,36,'抄送行政','CC',NULL,4,'[{\"type\": \"ROLE\", \"value\": \"行政专员\"}]'),(405,37,'发起人','START',NULL,0,NULL),(406,37,'直属主管','APPROVAL','直属主管',1,NULL),(407,37,'会计主管&内控','APPROVAL','会计主管&内控',2,NULL),(408,37,'执行总经理','APPROVAL','执行总经理',3,NULL),(409,37,'抄送行政','CC',NULL,4,'[{\"type\": \"ROLE\", \"value\": \"行政专员\"}]'),(410,38,'发起人','START',NULL,0,NULL),(411,38,'公司领导','APPROVAL','公司领导',1,NULL),(412,38,'出纳确认','APPROVAL','出纳',2,NULL),(413,38,'内控主管','APPROVAL','内控主管',3,NULL),(414,38,'抄送财务法务商务','CC',NULL,4,'[{\"type\": \"ROLE\", \"value\": \"财务经理\"}, {\"type\": \"ROLE\", \"value\": \"法务\"}, {\"type\": \"ROLE\", \"value\": \"商务专员\"}]'),(415,39,'发起人','START',NULL,0,NULL),(416,39,'直属主管','APPROVAL','直属主管',1,NULL),(417,39,'采购询价','APPROVAL','商务专员',2,NULL),(418,39,'行政主管（按需）','APPROVAL','行政主管',3,NULL),(419,39,'会计主管&内控','APPROVAL','会计主管&内控',4,NULL),(420,39,'执行总经理（≥2万元）','APPROVAL','执行总经理',5,NULL),(421,39,'采购办理','APPROVAL','行政专员',6,NULL),(422,39,'发起人签收','APPROVAL','直属主管',7,NULL),(423,40,'发起人','START',NULL,0,NULL),(424,40,'直属主管','APPROVAL','直属主管',1,NULL),(425,40,'行政专员发放','APPROVAL','行政专员',2,NULL),(426,40,'发起人签收','APPROVAL','直属主管',3,NULL),(427,41,'发起人','START',NULL,0,NULL),(428,41,'直属主管','APPROVAL','直属主管',1,NULL),(429,41,'会计主管&内控（涉及资金）','APPROVAL','会计主管&内控',2,NULL),(430,41,'法务','APPROVAL','法务',3,NULL),(431,41,'法务主管','APPROVAL','法务主管',4,NULL),(432,41,'公司领导（涉及资金）','APPROVAL','公司领导',5,NULL),(433,41,'内控专员用印','APPROVAL','内控专员',6,NULL),(434,41,'发起人上传归档附件','APPROVAL','直属主管',7,NULL),(435,42,'发起人','START',NULL,0,NULL),(436,42,'公司领导','APPROVAL','公司领导',1,NULL),(437,42,'抄送内控及内控主管','CC',NULL,2,'[{\"type\": \"ROLE\", \"value\": \"内控专员\"}, {\"type\": \"ROLE\", \"value\": \"内控主管\"}]'),(438,42,'发起人办理','APPROVAL','直属主管',3,NULL),(439,42,'抄送法务及商务','CC',NULL,4,'[{\"type\": \"ROLE\", \"value\": \"法务\"}, {\"type\": \"ROLE\", \"value\": \"商务专员\"}]'),(440,43,'发起人','START',NULL,0,NULL),(441,43,'直属主管','APPROVAL','直属主管',1,NULL),(442,43,'运营服务主管','APPROVAL','运营服务主管',2,NULL),(443,43,'会计主管&内控','APPROVAL','会计主管&内控',3,NULL),(444,43,'财务经理','APPROVAL','财务经理',4,NULL),(445,43,'公司领导','APPROVAL','公司领导',5,NULL),(446,43,'内控专员用印','APPROVAL','内控专员',6,NULL),(447,43,'发起人归还','APPROVAL','直属主管',7,NULL),(448,43,'抄送内控主管','CC',NULL,8,'[{\"type\": \"ROLE\", \"value\": \"内控主管\"}]');
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

-- Dump completed on 2026-09-14 16:39:00
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
INSERT INTO `flow_condition_rule` (`id`, `flow_config_id`, `variable_name`, `operator`, `expected_value`, `target_node_name`, `sort_order`) VALUES (44,1,'amount','GREATER_THAN_OR_EQUAL','20000','公司领导（大额）',0),(45,27,'amount','GREATER_THAN_OR_EQUAL','20000','公司领导（大额）',0),(46,36,'involvesFunds','EQUAL','true','会计主管&内控（涉及资金）',0),(47,39,'amount','GREATER_THAN_OR_EQUAL','20000','执行总经理（≥2万元）',0),(48,41,'involvesFunds','EQUAL','true','会计主管&内控（涉及资金）',0),(49,41,'involvesFunds','EQUAL','true','公司领导（涉及资金）',1);
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

-- Dump completed on 2026-09-14 16:39:00
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

-- Dump completed on 2026-09-14 16:39:00
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

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` (`id`, `name`, `job_no`, `account`, `password`, `department`, `department_id`, `post`, `post_id`, `status`, `manager_id`, `created_at`, `updated_at`) VALUES (42,'超级管理员','HXJ000','admin','$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW','总经办',16,'系统管理岗',22,'ACTIVE',NULL,'2026-09-10 10:18:42','2026-09-10 14:44:37'),(44,'林安然','HXJ001','linanran','$2a$10$GNjz64rBH0.wWKzY1YBWN.BS19WmRKzIW8D34cWxmCjrJ49I2pUTW','总经办',16,'系统管理员',50,'ACTIVE',NULL,'2026-09-10 16:48:50','2026-09-10 16:48:50'),(45,'连力','HXJ002','lianli','$2a$10$.vW4ukt3wGxubcKasIAiAe3Xwchbc3RzsKQLC0I.bLFgwszb4REyW','总经办',16,'公司领导',30,'ACTIVE',NULL,'2026-09-10 16:48:50','2026-09-10 16:48:50'),(46,'王强','HXJ003','wangqiang','$2a$10$SHfmYgLsXWS3x14jzcGpn.Fe0Tnft7IRWmqpv6sCv8w3JKH2LH0KC','总经办',16,'公司领导',30,'ACTIVE',45,'2026-09-10 16:48:50','2026-09-12 15:45:36'),(49,'刘婷','HXJ006','liuting','$2a$10$AP7j2D5p6y0/chZeArWei.mJqVVUzRSSE12ikNnAXka.pIu.Z7zFa','资管中心',31,'出纳主管',48,'ACTIVE',46,'2026-09-10 16:48:50','2026-09-12 15:45:36'),(51,'张龙','HXJ008','zhanglong','$2a$10$Oww4x4g9zIKupEQBWpR/6e6wGrdCFMRwshln70/rBKe.Xk7ZSustW','交付部',30,'交付主管',46,'ACTIVE',46,'2026-09-10 16:50:55','2026-09-14 12:05:24'),(52,'刘佳慧','HXJ009','liujiahui','$2a$10$PHOrkVF16mgFvhAnLDF9C.bnyUm5QFzzFJjSFhld07G29EQj0GtiC','行政部',26,'行政主管',43,'ACTIVE',46,'2026-09-10 16:53:09','2026-09-14 12:05:24'),(55,'吴荷珍','HXJ012','wuhezhen','$2a$10$wpGeuWUZMbZCMBv61lTpcub6M2bA9FePtm5.Q41vXul9onCWSqXou','财务部',22,'财务经理',47,'ACTIVE',46,'2026-09-10 16:53:10','2026-09-12 15:45:36'),(57,'黄政哲','HXJ014','huangzhengzhe','$2a$10$nvF4IzEsyrecTtGwLY7/YOeMuKjnrqvlccC72vay6.keJ4YUjTZb2','运营服务部',28,'运营服务主管',45,'ACTIVE',46,'2026-09-10 16:53:10','2026-09-14 12:05:24'),(61,'汪洋','HXJ018','wangyang','$2a$10$Nara0EGzEEJi0eHp5sEqMe1mdlXcN6yjzQAL3QdulVTBvBdNS4vLS','财务中心',20,'会计主管/内控',36,'ACTIVE',46,'2026-09-10 16:53:10','2026-09-12 15:45:36'),(62,'姚志豪','HXJ019','yaozhihao','$2a$10$nEEO/OJ9PNyQM6TDbjTrPepwk58q6WOulStmXW542IoU546Q7lavi','财务中心',20,'会计主管/内控',36,'ACTIVE',46,'2026-09-10 16:53:11','2026-09-12 15:45:36'),(63,'翁婷婷','HXJ020','wengtingting','$2a$10$0AGRtjcHmlmbZQ3Z5jTq5.RX1ej76KF/mXTZXHl2hqy7yySuSvPce','资管中心',31,'出纳',49,'ACTIVE',49,'2026-09-10 16:53:11','2026-09-12 16:11:01'),(64,'冯训漪','HXJ021','fengxunyi','$2a$10$wefnmlxdt1mndiNnDLhZSeQqWZIQLb4WA00Bd9SKZ5fkA3EPVFbiG','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:11','2026-09-12 15:45:36'),(65,'朱昀怡','HXJ022','zhuyunyi','$2a$10$BQFg7uyij8qPEyrWXtjjDee4tKcTc9F3G9GvmV4tH2hjUq8i9tJ7O','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:11','2026-09-12 15:45:36'),(66,'施惠君','HXJ023','shihuijun','$2a$10$uqw6b8lB/YYvsL31iy18eeSJr2iWCO.DYAret1WjtClkynQnbh0fq','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:11','2026-09-12 15:45:36'),(67,'蔡赐绵','HXJ024','caicimian','$2a$10$Bqe3VTfGMl8G5aU3VBHKw.4YMmaQUGkLC3mi4aKA.b7I6/29wJO6S','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:11','2026-09-12 15:45:36'),(68,'李小妹','HXJ025','lixiaomei','$2a$10$jBg.wxxKyyp5Mjv4AtA/YeE61507czkIPn96sKT2GTRah//46QBSW','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:11','2026-09-12 15:45:36'),(69,'石瀚文','HXJ026','shihanwen','$2a$10$.ZXxZX0jXatDLq0Z0vj/PeqxoBLKZpVKsTM8nake8hJAm794rUBQi','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:11','2026-09-12 15:45:36'),(70,'张欣怡','HXJ027','zhangxinyi','$2a$10$FSg4/kjJ2Illb8uNSm60GedPWyWahsC/0Amo/8qxHT.eLb/TKE9wy','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:12','2026-09-12 15:45:36'),(71,'温超群','HXJ028','wenchaoqun','$2a$10$vGd8DOw3SeirttciNEpfH.unDAZtLhFzVcUEybMdcAhyaBvHyXcQS','财务部',22,'会计',37,'ACTIVE',55,'2026-09-10 16:53:12','2026-09-12 15:45:36'),(72,'孙倩倩','HXJ029','sunqianqian','$2a$10$RD3H0MCaHTG9SRWCzIzPoO9ZVi8oweRudXi2duCZZ.ufH24OLewKS','内控部',23,'内控主管',38,'ACTIVE',46,'2026-09-10 16:53:12','2026-09-14 12:05:24'),(73,'郑宁静','HXJ030','zhengningjing','$2a$10$SOw/ZFwXzfK7iS8teVYWfe06k4eVNvUoUpuSFOOZ0F9jWbGwdoQ2S','内控部',23,'内控专员',39,'ACTIVE',72,'2026-09-10 16:53:12','2026-09-14 12:05:24'),(74,'唐菁蔚','HXJ031','tangjingwei','$2a$10$n/7DYhCtWOXmBw.o2oG6L.ULy/N5WkZ.evzhlXPAJWkQUbua3lpLS','法务部',24,'法务',40,'ACTIVE',75,'2026-09-10 16:53:12','2026-09-14 12:05:24'),(75,'陈楠','HXJ032','chennan','$2a$10$KacDozfbOhMn5kmscBFmCu9pFkCaCmLgGbtYL9BioXuvrsj6StaBa','法务部',24,'法务主管',41,'ACTIVE',46,'2026-09-10 16:53:12','2026-09-14 12:05:24'),(76,'朱铭骏','HXJ033','zhumingjun','$2a$10$ZsRipWdrw20upluSMr6mn.H9yjHax3XHvVzd.bjLUepD91BrIs00K','行政部',26,'行政专员',42,'ACTIVE',52,'2026-09-10 16:53:12','2026-09-12 15:45:36'),(77,'刘颖宁','HXJ034','liuyingning','$2a$10$r7JnrFXt6XcuiSRynMiMwOTLzfmJJi7U5Lit4xuOwoRgoS6/GP9ne','运营服务部',28,'商务专员',44,'ACTIVE',57,'2026-09-10 16:53:12','2026-09-12 15:45:36'),(78,'杨淑欢','HXJ035','yangshuhuan','$2a$10$nc99of3Avthj92dKwUrlJuY0mKQC/VjpB7U6.dXbFE90jgM8YiOKW','运营服务部',28,'商务专员',44,'ACTIVE',57,'2026-09-10 16:53:12','2026-09-12 15:45:36'),(79,'龚蓉','HXJ036','gongrong','$2a$10$C9PPgQC4sw4gyxHIP4NKmeT17UlpdkL32GhmIdokq7a.oMwckK4vm','运营服务部',28,'商务专员',44,'ACTIVE',57,'2026-09-10 16:53:12','2026-09-12 15:45:36'),(80,'林丽婷','HXJ037','linliting','$2a$10$BWizc02WlGfxGNLqiNxRkuw1cWatbQwHTSBuApDDhA/0UqPR9Jh3.','资管中心',31,'出纳',49,'ACTIVE',49,'2026-09-10 16:53:12','2026-09-12 15:45:36'),(81,'陈伟璇','HXJ038','chenweixuan','$2a$10$q9Nobgi3SnC0JVPVudwbUeSX5ff98XIHoVeK7JXwRu3/9bJUiAyHi','资管中心',31,'出纳',49,'ACTIVE',49,'2026-09-10 16:53:13','2026-09-12 15:45:36'),(82,'沈盼静','HXJ039','shenpanjing','$2a$10$YWRCHM/zc78z8jp50FTdSuVer/zEORkNtVhq0koaHMyA4qYY1.ncy','资管中心',31,'出纳',49,'ACTIVE',49,'2026-09-10 16:53:13','2026-09-12 15:45:36'),(83,'李展仪','HXJ040','lizhanyi','$2a$10$RY5FzoU3bRKVu9wghgOnielLLvmU8kyKQVhWM95XnXiNPlQC2Yl6e','资管中心',31,'出纳',49,'ACTIVE',49,'2026-09-10 16:53:13','2026-09-12 15:45:36');
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

-- Dump completed on 2026-09-14 16:39:00
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

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (42,20),(44,20),(51,24),(61,25),(62,25),(63,25),(45,26),(46,26),(73,27),(72,28),(63,29),(80,29),(81,29),(82,29),(83,29),(49,30),(77,31),(78,31),(79,31),(46,32),(61,33),(62,33),(64,33),(65,33),(66,33),(67,33),(68,33),(69,33),(70,33),(71,33),(74,34),(75,35),(52,36),(76,36),(52,37),(55,38),(57,39);
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

-- Dump completed on 2026-09-14 16:39:00


SET FOREIGN_KEY_CHECKS = 1;
