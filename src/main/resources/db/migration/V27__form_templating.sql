-- =====================================================================
-- V27: 表单模板化（M1）——模板/字段两表 + 单据动态值列 + 预置通用模板
-- 依据：openspec/changes/form-templating/design.md
-- 策略：旧散列列本迁移不删（实体枚举耦合），M5 清理迁移统一退役
-- =====================================================================

CREATE TABLE form_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_type VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    doc_prefix VARCHAR(10) NULL,
    flow_config_id BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_form_template_business_type UNIQUE (business_type)
);

CREATE TABLE form_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    field_key VARCHAR(50) NOT NULL,
    label VARCHAR(100) NOT NULL,
    control_type VARCHAR(20) NOT NULL,
    required TINYINT(1) NOT NULL DEFAULT 0,
    options TEXT NULL,
    reserved TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    UNIQUE KEY uk_template_field (template_id, field_key),
    CONSTRAINT fk_form_field_template FOREIGN KEY (template_id)
        REFERENCES form_template(id) ON DELETE CASCADE
);

ALTER TABLE oa_document
    ADD COLUMN form_template_id BIGINT NULL,
    ADD COLUMN form_version INT NULL,
    ADD COLUMN form_snapshot TEXT NULL,
    ADD COLUMN field_values TEXT NULL;

-- ---------- 预置通用模板（现有全部字段的等价定义） ----------
INSERT INTO form_template (business_type, name, doc_prefix, version, status, sort_order)
SELECT '通用审批单', '通用审批单', 'SP', 1, 'ENABLED', 0
WHERE NOT EXISTS (SELECT 1 FROM form_template WHERE business_type = '通用审批单');

SET @tpl := (SELECT id FROM form_template WHERE business_type = '通用审批单');

INSERT INTO form_field (template_id, field_key, label, control_type, required, options, reserved, sort_order)
SELECT @tpl, f.field_key, f.label, f.ctype, f.req, f.opts, f.rsv, f.ord FROM (
    SELECT 'title' AS field_key, '单据标题' AS label, 'TEXT' AS ctype, 1 AS req, NULL AS opts, 1 AS rsv, 1 AS ord UNION ALL
    SELECT 'company', '所属公司', 'SELECT', 1, '["海峡金","海峡金供应链"]', 0, 2 UNION ALL
    SELECT 'amount', '金额', 'NUMBER', 1, NULL, 1, 3 UNION ALL
    SELECT 'invoiceSummary', '发票摘要', 'TEXT', 0, NULL, 0, 4 UNION ALL
    SELECT 'reason', '事由明细', 'TEXTAREA', 1, NULL, 0, 5 UNION ALL
    SELECT 'contractNo', '合同编号', 'TEXT', 0, NULL, 0, 6 UNION ALL
    SELECT 'involvesFunds', '是否涉及资金', 'BOOLEAN', 0, NULL, 1, 7 UNION ALL
    SELECT 'requiresAdminReview', '是否需行政复核', 'BOOLEAN', 0, NULL, 1, 8 UNION ALL
    SELECT 'businessMode', '业务模式', 'SELECT', 0, '["标准","非标"]', 1, 9 UNION ALL
    SELECT 'needPostMaterial', '是否后置补材料', 'BOOLEAN', 0, NULL, 1, 10 UNION ALL
    SELECT 'sealProject', '用印项目', 'TEXT', 0, NULL, 0, 11 UNION ALL
    SELECT 'sealType', '印章类型', 'SELECT', 0, '["公章","合同章","法人章","财务章"]', 0, 12 UNION ALL
    SELECT 'sealDepartment', '用印部门', 'TEXT', 0, NULL, 0, 13 UNION ALL
    SELECT 'sealTime', '用印时间', 'DATE', 0, NULL, 0, 14 UNION ALL
    SELECT 'sealFileName', '用印文件名', 'TEXT', 0, NULL, 0, 15 UNION ALL
    SELECT 'sealReason', '用印事由', 'TEXTAREA', 0, NULL, 0, 16
) f
WHERE NOT EXISTS (SELECT 1 FROM form_field x WHERE x.template_id = @tpl AND x.field_key = f.field_key);
