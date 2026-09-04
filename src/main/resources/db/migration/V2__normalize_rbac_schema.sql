-- 将 V1 中以文本保存的角色权限配置规范化为可查询、可关联的 RBAC 模型。

CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限点编码',
    name VARCHAR(100) NOT NULL COMMENT '权限点名称',
    description VARCHAR(500) COMMENT '权限点说明',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点表';

CREATE TABLE sys_data_scope (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE COMMENT '数据范围编码',
    name VARCHAR(100) NOT NULL COMMENT '数据范围名称',
    description VARCHAR(500) COMMENT '数据范围说明',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据范围表';

ALTER TABLE sys_role
    ADD COLUMN data_scope_id BIGINT NULL COMMENT '数据范围ID' AFTER post,
    ADD CONSTRAINT fk_role_data_scope
        FOREIGN KEY (data_scope_id) REFERENCES sys_data_scope(id);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role
        FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission
        FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

ALTER TABLE sys_user
    ADD CONSTRAINT uk_user_job_no UNIQUE (job_no);

-- V1 的 data_scope、permissions、members 列暂时保留，待任务 2.5
-- 写入预置数据并完成旧数据映射后再执行清理，避免破坏已存在的开发数据。