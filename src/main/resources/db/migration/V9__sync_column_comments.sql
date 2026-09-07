-- 同步列注释，使数据库注释与代码枚举保持一致。
-- 仅修改 COMMENT，不改动字段类型、长度、默认值与约束（与 V1 定义保持一致）。

-- 单据状态：补齐代码中实际使用的 SUPPLEMENT_REQUIRED（待补充材料）。
ALTER TABLE oa_document
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    COMMENT '状态(PENDING待提交/APPROVING审批中/APPROVED已通过/REJECTED已驳回/SUPPLEMENT_REQUIRED待补充材料)';

-- 流程分类：V1 注释写作"用章"，而种子数据与权限点统一使用"用印"，此处对齐为"用印"。
ALTER TABLE flow_config
    MODIFY COLUMN category VARCHAR(50)
    COMMENT '分类(日常/业务/用印)';
