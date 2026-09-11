-- =====================================================================
-- V24: 修复张龙缺失的角色归属（对齐原型 roleConfigs，幂等）
-- 依据：docs/design/原型数据清单.md 表 2——张龙应有：
--       一级中心负责人、二级部门负责人、交付主管
-- 原因：V22 种子取自当时活库快照，快照中已缺失此二行
-- =====================================================================

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.name = '二级部门负责人'
WHERE u.account = 'zhanglong'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role x WHERE x.user_id = u.id AND x.role_id = r.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.name = '交付主管'
WHERE u.account = 'zhanglong'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role x WHERE x.user_id = u.id AND x.role_id = r.id);
