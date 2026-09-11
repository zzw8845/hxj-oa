-- 多部门支持（钉钉式：主部门唯一 + 兼职部门可多个）
-- 主部门保持 sys_user.department_id（汇报线/数据范围的锚点，语义不变）；
-- 兼职部门入关联表，用于通讯录展示、部门成员呈现与删除守卫。

CREATE TABLE sys_user_department (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  is_primary TINYINT(1) NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user_department (user_id, department_id),
  CONSTRAINT fk_usd_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_usd_department FOREIGN KEY (department_id) REFERENCES sys_department (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工兼职部门关联（主部门在 sys_user.department_id）';

-- 存量回填：现有主部门注册为正式成员（is_primary=1，保证"人在哪些部门"查询完整）
INSERT INTO sys_user_department (user_id, department_id, is_primary)
SELECT id, department_id, 1 FROM sys_user WHERE department_id IS NOT NULL;
