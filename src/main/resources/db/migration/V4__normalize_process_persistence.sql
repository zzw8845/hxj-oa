-- 审批记录、抄送、归档和流程配置结构化持久化。

ALTER TABLE approval_record
    ADD COLUMN approver_id BIGINT NULL COMMENT '审批人ID' AFTER approver,
    ADD COLUMN sign_user_id BIGINT NULL COMMENT '加签人员ID' AFTER sign_user,
    ADD CONSTRAINT fk_approval_approver
        FOREIGN KEY (approver_id) REFERENCES sys_user(id),
    ADD CONSTRAINT fk_approval_sign_user
        FOREIGN KEY (sign_user_id) REFERENCES sys_user(id),
    ADD CONSTRAINT fk_approval_evidence
        FOREIGN KEY (evidence_file_id) REFERENCES oa_attachment(id);

ALTER TABLE approval_record
    MODIFY COLUMN approver VARCHAR(50) NULL COMMENT '旧审批人姓名（兼容字段）',
    MODIFY COLUMN action VARCHAR(50) NOT NULL COMMENT '审批操作';

ALTER TABLE cc_record
    ADD COLUMN target_user_id BIGINT NULL COMMENT '抄送人员ID' AFTER cc_target,
    ADD COLUMN target_role_id BIGINT NULL COMMENT '抄送角色ID' AFTER target_user_id,
    ADD CONSTRAINT fk_cc_target_user
        FOREIGN KEY (target_user_id) REFERENCES sys_user(id),
    ADD CONSTRAINT fk_cc_target_role
        FOREIGN KEY (target_role_id) REFERENCES sys_role(id);

ALTER TABLE archive_ledger
    ADD CONSTRAINT uk_archive_document UNIQUE (doc_id);

CREATE TABLE flow_node_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flow_config_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT '节点名称',
    node_type VARCHAR(30) NOT NULL COMMENT '节点类型',
    assignee_role VARCHAR(100) NULL COMMENT '审批角色',
    sort_order INT NOT NULL COMMENT '节点顺序',
    CONSTRAINT fk_flow_node_config
        FOREIGN KEY (flow_config_id) REFERENCES flow_config(id) ON DELETE CASCADE,
    CONSTRAINT uk_flow_node_order UNIQUE (flow_config_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点配置表';

CREATE TABLE flow_condition_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flow_config_id BIGINT NOT NULL,
    variable_name VARCHAR(100) NOT NULL COMMENT '流程变量名',
    operator VARCHAR(50) NOT NULL COMMENT '比较运算符',
    expected_value VARCHAR(200) NOT NULL COMMENT '期望值',
    target_node_name VARCHAR(100) NOT NULL COMMENT '命中后的目标节点',
    sort_order INT NOT NULL COMMENT '规则顺序',
    CONSTRAINT fk_flow_condition_config
        FOREIGN KEY (flow_config_id) REFERENCES flow_config(id) ON DELETE CASCADE,
    CONSTRAINT uk_flow_condition_order UNIQUE (flow_config_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程条件分支规则表';

ALTER TABLE flow_config
    MODIFY COLUMN nodes TEXT NULL COMMENT '旧节点链JSON（兼容字段）';

-- 旧文本字段将在任务 2.5 的种子数据迁移完成后清理。