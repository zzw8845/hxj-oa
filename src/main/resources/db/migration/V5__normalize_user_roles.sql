-- 支持一名员工分配多个角色，权限仍只能由角色继承。

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user
        FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role
        FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工角色关联表';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT id, role_id FROM sys_user WHERE role_id IS NOT NULL;

ALTER TABLE sys_user DROP FOREIGN KEY fk_user_role;
ALTER TABLE sys_user DROP COLUMN role_id;