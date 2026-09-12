-- 合并按级别命名的两个负责人角色：
--   一级中心负责人（DEPT_AND_CHILD，6人） + 二级部门负责人（DEPT，13人） → 「部门负责人」
-- 依据：DEPT_AND_CHILD 锚定用户主部门算子树——挂中心者子树=中心+下属部门（等价原"一级"），
--       挂叶子部门者子树=本部门（自动退化为 DEPT，等价原"二级"）。两角色权限集完全相同。
-- 合并时机：流程节点对两角色引用数为 0（改名级联无目标），合并零代价。
-- 幂等：全部按角色名判重，重跑无副作用。

-- 1. 成员并入（5 人身兼两角色，NOT EXISTS 防重复）
INSERT INTO sys_user_role (user_id, role_id)
SELECT ur.user_id, r1.id
FROM sys_user_role ur
JOIN sys_role r2 ON r2.id = ur.role_id AND r2.name = '二级部门负责人'
JOIN sys_role r1 ON r1.name = '一级中心负责人'
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role x WHERE x.user_id = ur.user_id AND x.role_id = r1.id);

-- 2. 权限并入（防御性：万一两角色权限集有差，补齐到存活角色）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT rp.role_id, rp.permission_id
FROM sys_role_permission rp
JOIN sys_role r2 ON r2.id = rp.role_id AND r2.name = '二级部门负责人'
JOIN sys_role r1 ON r1.name = '一级中心负责人'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission x
                  WHERE x.role_id = r1.id AND x.permission_id = rp.permission_id);

-- 3. 删除空壳角色及其关联
DELETE ur FROM sys_user_role ur JOIN sys_role r2 ON r2.id = ur.role_id WHERE r2.name = '二级部门负责人';
DELETE rp FROM sys_role_permission rp JOIN sys_role r2 ON r2.id = rp.role_id WHERE r2.name = '二级部门负责人';
DELETE FROM sys_role WHERE name = '二级部门负责人';

-- 4. 存活角色改名为通用称谓（post 同步为部门负责人）
UPDATE sys_role SET name = '部门负责人', post = '部门负责人' WHERE name = '一级中心负责人';
