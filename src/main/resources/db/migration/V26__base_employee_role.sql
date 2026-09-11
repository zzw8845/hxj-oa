-- =====================================================================
-- V26: 普通员工基础角色（幂等）
-- 依据：原型 pageNames.roles 演示数组含"普通员工"；newPerson 默认 position='普通员工'
-- 作用：无职能角色的人员（如新入职未定岗）也能登录、提交/查看本人单据
--       （单据接口门禁要求 VIEW_OWN_FORMS 或 SUBMIT_ALL_FORMS，无角色=403）
-- =====================================================================

INSERT INTO sys_role (name, post, data_scope_id)
SELECT '普通员工', '普通员工', (SELECT id FROM sys_data_scope WHERE code = 'OWN')
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE name = '普通员工');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN ('SUBMIT_ALL_FORMS', 'VIEW_OWN_FORMS')
WHERE r.name = '普通员工'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x
                  WHERE x.role_id = r.id AND x.permission_id = p.id);
