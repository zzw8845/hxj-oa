-- =====================================================================
-- V28: 表单模板化（M2）——模板分类/附件要求 + 抄送目标结构化 + 附件字段关联
-- =====================================================================

ALTER TABLE form_template
    ADD COLUMN category VARCHAR(30) NULL,
    ADD COLUMN attachment_requirements TEXT NULL;

UPDATE form_template
SET category = 'DAILY_PAYMENT',
    attachment_requirements = '["关联前置单据","业务证明资料","发票","收款信息"]'
WHERE business_type = '通用审批单';

-- 抄送目标结构化（JSON 数组 [{"type":"ROLE|DEPT|USER","value":"..."}]），替代节点名文案解析
ALTER TABLE flow_node_config ADD COLUMN cc_targets TEXT NULL;

-- 表单附件控件与附件记录的字段关联
ALTER TABLE oa_attachment ADD COLUMN field_key VARCHAR(50) NULL;
