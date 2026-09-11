-- =====================================================================
-- V25: 核算会计数据范围修正 + 核算分工表（幂等）
-- 依据：原型"按业务部门分配"→ CUSTOM 勾业务条线部门（DEPT 锚主部门语义错位）
--       "会计（按部门）"节点路由需要 人员×服务部门 分工映射
-- =====================================================================

-- ---------- 1. 核算会计：DEPT → CUSTOM ----------
UPDATE sys_role
SET data_scope_id = (SELECT id FROM sys_data_scope WHERE code = 'CUSTOM')
WHERE name = '核算会计'
  AND data_scope_id <> (SELECT id FROM sys_data_scope WHERE code = 'CUSTOM');

-- ---------- 2. 核算会计的自定义范围 = 业务条线部门（幂等） ----------
INSERT INTO sys_role_scope_department (role_id, department_id)
SELECT r.id, d.id
FROM sys_role r
JOIN sys_department d ON d.name IN ('业务支持中心', '运营服务部', '供应链中心', '交付部', '资管中心', '总经办')
WHERE r.name = '核算会计'
  AND NOT EXISTS (SELECT 1 FROM sys_role_scope_department x
                  WHERE x.role_id = r.id AND x.department_id = d.id);

-- ---------- 3. 核算分工表：成员 × 服务部门（"会计（按部门）"节点的路由依据） ----------
CREATE TABLE sys_user_service_dept (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_service_dept (user_id, department_id),
    CONSTRAINT fk_service_dept_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_service_dept_department FOREIGN KEY (department_id) REFERENCES sys_department(id) ON DELETE CASCADE
) COMMENT '职能服务分工：成员服务哪些部门的单据（如核算会计按部门分工）';
