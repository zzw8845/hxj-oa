-- 数据库重置为干净初始状态：仅保留超级管理员账号 admin。
-- 保留的系统字典：5 种数据范围类型（ALL/OWN/DEPT/DEPT_AND_CHILD/CUSTOM，与代码逻辑强耦合）
-- 与权限点清单（角色配置的下拉数据源）；其余组织、人员、角色、流程、快捷单据全部清空，
-- 由管理员通过管理接口自行 CRUD。
-- admin 初始密码与 V6 预置一致（password，BCrypt），首次登录后应立即修改。

-- 1. 清空业务数据（approval_record.evidence_file_id 引用 oa_attachment，
--    故审批记录先于附件删除；附件再先于单据）
DELETE FROM approval_record;
DELETE FROM cc_record;
DELETE FROM archive_ledger;
DELETE FROM oa_attachment;
DELETE FROM oa_document;

-- 2. 清空人员与角色关联（先删关联方，再删被引用方）
DELETE FROM sys_user_role;
DELETE FROM sys_role_permission;
DELETE FROM sys_role_scope_department;
DELETE FROM sys_user;
DELETE FROM sys_role;

-- 3. 清空流程配置与快捷单据目录
DELETE FROM flow_condition_rule;
DELETE FROM flow_node_config;
DELETE FROM flow_config;
DELETE FROM quick_document;

-- 4. 部门字典清空（先借闭包表删除所有非根部门，避开自引用外键；
--    闭包路径随外键级联清理），岗位仅保留一个供 admin 引用
DELETE d FROM sys_department d
JOIN sys_department_closure c ON c.descendant_id = d.id
WHERE c.ancestor_id <> d.id;
DELETE FROM sys_department;
DELETE FROM sys_post;

-- 5. 最小组织底座：一个根部门 + 一个岗位
INSERT INTO sys_department (name, sort_order) VALUES ('总经办', 1);
INSERT INTO sys_department_closure (ancestor_id, descendant_id, depth)
SELECT id, id, 0 FROM sys_department;
INSERT INTO sys_post (name) VALUES ('系统管理员');

-- 6. 超级管理员账号 admin（初始密码：password）
INSERT INTO sys_user (name, job_no, account, password, department, department_id, post, post_id, status)
VALUES ('超级管理员', '0001', 'admin',
        '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW',
        '总经办', (SELECT id FROM sys_department WHERE name = '总经办'),
        '系统管理员', (SELECT id FROM sys_post WHERE name = '系统管理员'),
        'ACTIVE');

-- 7. 超级管理员角色：全部数据范围 + 管理所需权限点，并绑定到 admin
INSERT INTO sys_role (name, department, post, data_scope_id)
VALUES ('超级管理员', '总经办', '系统管理员',
        (SELECT id FROM sys_data_scope WHERE code = 'ALL'));

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.account = 'admin' AND r.name = '超级管理员';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
JOIN sys_permission p ON p.code IN
('VIEW_ALL_FORMS', 'SUBMIT_ALL_FORMS', 'APPROVE_ALL_NODES', 'CONFIGURE_FLOW_PERMISSION')
WHERE r.name = '超级管理员';
