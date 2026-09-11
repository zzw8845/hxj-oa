-- =====================================================================
-- V23: 兼职部门按设计图补全 + 翁婷婷身份更正（幂等，可重复执行）
-- 依据：docs/design/海峡金OA审批系统原型.html roleConfigs
--   - 角色归属部门 ≠ 人员主部门的，落为兼职部门（sys_user_department）
--   - 翁婷婷按出纳角色（资管中心/出纳）为主身份，会计主管&内控（财务中心）转兼职
-- =====================================================================

-- ---------- 1. 翁婷婷：主身份更正为出纳（资管中心），原会计主管面转兼职 ----------
UPDATE sys_user SET
  department_id = (SELECT id FROM sys_department WHERE name = '资管中心'),
  post_id       = (SELECT id FROM sys_post WHERE name = '出纳')
WHERE account = 'wengtingting'
  AND (department_id <> (SELECT id FROM sys_department WHERE name = '资管中心')
       OR post_id <> (SELECT id FROM sys_post WHERE name = '出纳'));

-- ---------- 2. 兼职部门落库（17 人，按 account + 部门名判重） ----------
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'liujiahui'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'liuting'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'luyifen'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'wuhezhen'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '财务部'
WHERE u.account = 'yaozhihao'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'zhanglong'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'lilinhua'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '财务部'
WHERE u.account = 'wangyang'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'wanghailiang'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'wangying'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '财务中心'
WHERE u.account = 'wengtingting'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'wengjianfa'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'shufei'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'miaofuxin'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'guojingyu'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'chenchenghui'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);
INSERT INTO sys_user_department (user_id, department_id)
SELECT u.id, d.id FROM sys_user u JOIN sys_department d ON d.name = '总经办'
WHERE u.account = 'huangzhengzhe'
  AND NOT EXISTS (SELECT 1 FROM sys_user_department x WHERE x.user_id = u.id AND x.department_id = d.id);

-- 完毕：幂等可重复执行
