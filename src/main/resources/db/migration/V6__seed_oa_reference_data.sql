-- 海峡金 OA 审批系统预置数据。
-- 示例账号初始密码统一为 password（BCrypt 哈希），生产环境首次登录后应强制修改。

-- 1. 数据范围
INSERT INTO sys_data_scope (code, name, description) VALUES
('ALL_DEPARTMENTS_ALL_NODES', '全部部门与全部节点', '可查看全部部门单据并审批全部节点'),
('MAJOR_CONDITIONAL_APPROVAL', '重大事项与条件审批', '公司领导负责重大事项及条件分支审批'),
('OPERATIONS_ADMIN_MATTERS', '经营及行政事项', '执行总经理负责经营和行政事项'),
('OWN_CENTER_DOCUMENTS', '所属中心单据', '查看所属一级中心提交的单据'),
('OWN_DEPARTMENT_DOCUMENTS', '所属部门单据', '查看所属二级部门提交的单据'),
('FINANCE_INTERNAL_CONTROL', '财务及内控单据', '查看财务复核及内控相关单据'),
('ASSIGNED_BUSINESS_DEPARTMENTS', '按业务部门分配', '按核算会计负责的业务部门分配单据'),
('COMPANY_INTERNAL_CONTROL', '全公司内控单据', '查看全公司的内控相关单据'),
('SEAL_COMPLIANCE', '用印及合规单据', '查看用印办理及合规相关单据'),
('CONTRACT_OFFICIAL_DOCUMENT', '合同与发文事项', '查看合同和公司发文事项'),
('CONTRACT_REVIEW', '合同复审事项', '查看需要法务复审的合同事项'),
('ADMIN_ASSET', '行政与资产事项', '查看行政办理和资产管理事项'),
('ADMIN_APPROVAL', '行政审批事项', '查看需要行政主管审批的事项'),
('SETTLEMENT_SEAL_ARCHIVE', '结算与用印归档', '查看结算、用印和归档事项'),
('OPERATIONS_MATTERS', '运营类事项', '查看运营服务相关事项'),
('DELIVERY_PICKUP', '交付及提货事项', '查看交付和提货人变更事项'),
('FINANCE_MANAGEMENT', '财务管理事项', '查看财务管理及资产财务确认事项'),
('FUNDS_ACCOUNTS', '资金与账户事项', '查看资金和银行账户事项'),
('ASSIGNED_PAYMENT_LINES', '按业务条线分配付款', '按出纳负责的业务条线分配付款单据');

-- 2. 权限点字典（包含规范通用权限点及预置角色专用权限点）
INSERT INTO sys_permission (code, name, description) VALUES
('VIEW_OWN_FORMS', '查看本人表单', '查看本人提交的表单'),
('VIEW_DEPARTMENT_FORMS', '查看本部门表单', '查看本部门提交的表单'),
('VIEW_ALL_FORMS', '查看全部表单', '查看全公司全部表单'),
('SUBMIT_ALL_FORMS', '提交全部表单', '提交全部业务类型表单'),
('DEPARTMENT_HEAD_APPROVAL', '部门负责人审批', '执行部门负责人审批节点'),
('ACCOUNTING_APPROVAL', '核算会计审批', '执行核算会计审批节点'),
('INTERNAL_CONTROL_APPROVAL', '内控合规审批', '执行内控合规审批节点'),
('CASHIER_APPROVAL', '出纳审批', '执行出纳审批节点'),
('APPROVE_ALL_NODES', '审批全部节点', '超级管理员审批任意节点'),
('UPLOAD_APPROVAL_EVIDENCE', '上传审批凭证', '上传当前审批节点凭证'),
('CONFIGURE_FLOW_PERMISSION', '配置流程与权限', '维护流程、角色和权限'),
('MAJOR_MATTER_APPROVAL', '重大事项审批', '审批重大经营及条件事项'),
('VIEW_PREVIOUS_MATERIALS', '查看前置资料', '查看全部前置节点资料'),
('BUSINESS_MATTER_APPROVAL', '经营事项审批', '审批经营类事项'),
('ADMIN_MATTER_APPROVAL', '行政事项审批', '审批行政类事项'),
('CENTER_HEAD_APPROVAL', '中心负责人审批', '执行一级中心负责人审批'),
('VIEW_CENTER_FORMS', '查看所属中心单据', '查看所属一级中心单据'),
('DIRECT_DEPARTMENT_APPROVAL', '直属部门审批', '执行直属二级部门审批'),
('VIEW_OWN_DEPARTMENT_DOCUMENTS', '查看所属部门单据', '查看所属二级部门单据'),
('FINANCE_REVIEW', '财务复核', '执行财务复核'),
('INTERNAL_CONTROL_REVIEW', '内控审核', '执行内控审核'),
('UPLOAD_ACCOUNTING_EVIDENCE', '上传核算凭证', '上传核算节点凭证'),
('VIEW_ALL_ATTACHMENTS', '查看全部附件', '查看单据全部节点附件'),
('SEAL_HANDLING', '用印办理', '执行用印办理节点'),
('UPLOAD_SEAL_EVIDENCE', '上传用印凭证', '上传盖章或用印凭证'),
('LEGAL_REVIEW', '法务审核', '执行法务审核'),
('VIEW_CONTRACT_ATTACHMENTS', '查看合同附件', '查看合同相关附件'),
('LEGAL_SECOND_REVIEW', '法务复审', '执行法务主管复审'),
('ADMIN_HANDLING', '行政办理', '执行行政办理节点'),
('ASSET_REGISTRATION', '资产登记', '登记固定资产'),
('OFFICIAL_DOC_HANDLING', '发文办理', '执行编号发文'),
('ADMIN_SUPERVISOR_APPROVAL', '行政主管审批', '执行行政主管审批'),
('VIEW_ADMIN_FORMS', '查看行政单据', '查看行政相关单据'),
('BUSINESS_REVIEW', '商务审核', '执行商务审核'),
('SETTLEMENT_ARCHIVE', '结算归档', '执行结算和归档'),
('OPERATIONS_APPROVAL', '运营服务审批', '执行运营服务审批'),
('VIEW_OPERATIONS_FORMS', '查看运营单据', '查看运营服务相关单据'),
('DELIVERY_APPROVAL', '交付审批', '执行交付审批'),
('PICKUP_PERSON_APPROVAL', '提货人审批', '审批提货人新增或变更'),
('FINANCE_MANAGER_APPROVAL', '财务经理审批', '执行财务经理审批'),
('ASSET_FINANCE_CONFIRM', '资产财务确认', '确认资产相关财务信息'),
('CASHIER_REVIEW', '出纳复核', '执行出纳主管复核'),
('BANK_ACCOUNT_CONFIRM', '银行账户确认', '确认银行账户调整'),
('UPLOAD_BANK_RECEIPT', '上传银行回单', '上传付款银行回单');

-- 3. 19 个预置角色
INSERT INTO sys_role (name, department, post, data_scope_id) VALUES
('超级管理员', '总经办', '系统管理员', (SELECT id FROM sys_data_scope WHERE code = 'ALL_DEPARTMENTS_ALL_NODES')),
('公司领导', '总经办', '公司领导', (SELECT id FROM sys_data_scope WHERE code = 'MAJOR_CONDITIONAL_APPROVAL')),
('执行总经理', '总经办', '执行总经理', (SELECT id FROM sys_data_scope WHERE code = 'OPERATIONS_ADMIN_MATTERS')),
('一级中心负责人', '各一级中心', '中心负责人', (SELECT id FROM sys_data_scope WHERE code = 'OWN_CENTER_DOCUMENTS')),
('二级部门负责人', '各二级部门', '部门负责人', (SELECT id FROM sys_data_scope WHERE code = 'OWN_DEPARTMENT_DOCUMENTS')),
('会计主管&内控', '财务中心', '会计主管/内控', (SELECT id FROM sys_data_scope WHERE code = 'FINANCE_INTERNAL_CONTROL')),
('核算会计', '财务中心-财务部', '会计', (SELECT id FROM sys_data_scope WHERE code = 'ASSIGNED_BUSINESS_DEPARTMENTS')),
('内控主管', '总经办-内控部', '内控主管', (SELECT id FROM sys_data_scope WHERE code = 'COMPANY_INTERNAL_CONTROL')),
('内控专员', '总经办-内控部', '内控专员', (SELECT id FROM sys_data_scope WHERE code = 'SEAL_COMPLIANCE')),
('法务', '总经办-法务部', '法务', (SELECT id FROM sys_data_scope WHERE code = 'CONTRACT_OFFICIAL_DOCUMENT')),
('法务主管', '总经办-法务部', '法务主管', (SELECT id FROM sys_data_scope WHERE code = 'CONTRACT_REVIEW')),
('行政专员', '人力行政中心-行政部', '行政专员', (SELECT id FROM sys_data_scope WHERE code = 'ADMIN_ASSET')),
('行政主管', '人力行政中心-行政部', '行政主管', (SELECT id FROM sys_data_scope WHERE code = 'ADMIN_APPROVAL')),
('商务专员', '业务支持中心-运营服务部', '商务专员', (SELECT id FROM sys_data_scope WHERE code = 'SETTLEMENT_SEAL_ARCHIVE')),
('运营服务主管', '业务支持中心-运营服务部', '运营服务主管', (SELECT id FROM sys_data_scope WHERE code = 'OPERATIONS_MATTERS')),
('交付主管', '供应链中心-交付部', '交付主管', (SELECT id FROM sys_data_scope WHERE code = 'DELIVERY_PICKUP')),
('财务经理', '财务中心-财务部', '财务经理', (SELECT id FROM sys_data_scope WHERE code = 'FINANCE_MANAGEMENT')),
('出纳主管', '资管中心', '出纳主管', (SELECT id FROM sys_data_scope WHERE code = 'FUNDS_ACCOUNTS')),
('出纳', '资管中心', '出纳', (SELECT id FROM sys_data_scope WHERE code = 'ASSIGNED_PAYMENT_LINES'));

-- 4. 角色权限映射
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('VIEW_ALL_FORMS', 'SUBMIT_ALL_FORMS', 'APPROVE_ALL_NODES', 'CONFIGURE_FLOW_PERMISSION')
WHERE r.name = '超级管理员';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('MAJOR_MATTER_APPROVAL', 'VIEW_PREVIOUS_MATERIALS') WHERE r.name = '公司领导';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('BUSINESS_MATTER_APPROVAL', 'ADMIN_MATTER_APPROVAL') WHERE r.name = '执行总经理';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('CENTER_HEAD_APPROVAL', 'VIEW_CENTER_FORMS') WHERE r.name = '一级中心负责人';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('DIRECT_DEPARTMENT_APPROVAL', 'VIEW_OWN_DEPARTMENT_DOCUMENTS') WHERE r.name = '二级部门负责人';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('FINANCE_REVIEW', 'INTERNAL_CONTROL_REVIEW', 'VIEW_PREVIOUS_MATERIALS') WHERE r.name = '会计主管&内控';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('ACCOUNTING_APPROVAL', 'VIEW_PREVIOUS_MATERIALS', 'UPLOAD_ACCOUNTING_EVIDENCE') WHERE r.name = '核算会计';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('INTERNAL_CONTROL_APPROVAL', 'VIEW_ALL_ATTACHMENTS') WHERE r.name = '内控主管';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('SEAL_HANDLING', 'UPLOAD_SEAL_EVIDENCE') WHERE r.name = '内控专员';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('LEGAL_REVIEW', 'VIEW_CONTRACT_ATTACHMENTS') WHERE r.name = '法务';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('LEGAL_SECOND_REVIEW', 'VIEW_CONTRACT_ATTACHMENTS') WHERE r.name = '法务主管';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('ADMIN_HANDLING', 'ASSET_REGISTRATION', 'OFFICIAL_DOC_HANDLING') WHERE r.name = '行政专员';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('ADMIN_SUPERVISOR_APPROVAL', 'VIEW_ADMIN_FORMS') WHERE r.name = '行政主管';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('BUSINESS_REVIEW', 'SETTLEMENT_ARCHIVE') WHERE r.name = '商务专员';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('OPERATIONS_APPROVAL', 'VIEW_OPERATIONS_FORMS') WHERE r.name = '运营服务主管';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('DELIVERY_APPROVAL', 'PICKUP_PERSON_APPROVAL') WHERE r.name = '交付主管';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('FINANCE_MANAGER_APPROVAL', 'ASSET_FINANCE_CONFIRM') WHERE r.name = '财务经理';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('CASHIER_REVIEW', 'BANK_ACCOUNT_CONFIRM') WHERE r.name = '出纳主管';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('CASHIER_APPROVAL', 'UPLOAD_BANK_RECEIPT') WHERE r.name = '出纳';

-- 5. 40 名唯一预置员工
INSERT INTO sys_user (name, job_no, account, password, department, post, status) VALUES
('林安然', 'HXJ001', 'linanran', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '总经办', '系统管理员', 'ACTIVE'),
('连力', 'HXJ002', 'lianli', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '总经办', '公司领导', 'ACTIVE'),
('王强', 'HXJ003', 'wangqiang', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '总经办', '公司领导', 'ACTIVE'),
('舒飞', 'HXJ004', 'shufei', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '各一级中心', '中心负责人', 'ACTIVE'),
('卢乙芬', 'HXJ005', 'luyifen', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '各一级中心', '中心负责人', 'ACTIVE'),
('刘婷', 'HXJ006', 'liuting', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '资管中心', '出纳主管', 'ACTIVE'),
('王海亮', 'HXJ007', 'wanghailiang', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '各一级中心', '中心负责人', 'ACTIVE'),
('张龙', 'HXJ008', 'zhanglong', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '供应链中心-交付部', '交付主管', 'ACTIVE'),
('刘佳慧', 'HXJ009', 'liujiahui', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '人力行政中心-行政部', '行政主管', 'ACTIVE'),
('陈成辉', 'HXJ010', 'chenchenghui', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '各二级部门', '部门负责人', 'ACTIVE'),
('郭静宇', 'HXJ011', 'guojingyu', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '各二级部门', '部门负责人', 'ACTIVE'),
('吴荷珍', 'HXJ012', 'wuhezhen', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '财务中心-财务部', '财务经理', 'ACTIVE'),
('王莹', 'HXJ013', 'wangying', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '各二级部门', '部门负责人', 'ACTIVE'),
('黄政哲', 'HXJ014', 'huangzhengzhe', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '业务支持中心-运营服务部', '运营服务主管', 'ACTIVE'),
('李林华', 'HXJ015', 'lilinhua', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '各二级部门', '部门负责人', 'ACTIVE'),
('苗福鑫', 'HXJ016', 'miaofuxin', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '各二级部门', '部门负责人', 'ACTIVE'),
('翁建发', 'HXJ017', 'wengjianfa', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '各二级部门', '部门负责人', 'ACTIVE'),
('汪洋', 'HXJ018', 'wangyang', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '财务中心', '会计主管/内控', 'ACTIVE'),
('姚志豪', 'HXJ019', 'yaozhihao', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '财务中心', '会计主管/内控', 'ACTIVE'),
('翁婷婷', 'HXJ020', 'wengtingting', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '财务中心', '会计主管/内控', 'ACTIVE'),
('冯训漪', 'HXJ021', 'fengxunyi', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '财务中心-财务部', '会计', 'ACTIVE'),
('朱昀怡', 'HXJ022', 'zhuyunyi', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '财务中心-财务部', '会计', 'ACTIVE'),
('施惠君', 'HXJ023', 'shihuijun', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '财务中心-财务部', '会计', 'ACTIVE'),
('蔡赐绵', 'HXJ024', 'caicimian', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '财务中心-财务部', '会计', 'ACTIVE'),
('李小妹', 'HXJ025', 'lixiaomei', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '财务中心-财务部', '会计', 'ACTIVE'),
('石瀚文', 'HXJ026', 'shihanwen', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '财务中心-财务部', '会计', 'ACTIVE'),
('张欣怡', 'HXJ027', 'zhangxinyi', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '财务中心-财务部', '会计', 'ACTIVE'),
('温超群', 'HXJ028', 'wenchaoqun', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '财务中心-财务部', '会计', 'ACTIVE'),
('孙倩倩', 'HXJ029', 'sunqianqian', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '总经办-内控部', '内控主管', 'ACTIVE'),
('郑宁静', 'HXJ030', 'zhengningjing', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '总经办-内控部', '内控专员', 'ACTIVE'),
('唐菁蔚', 'HXJ031', 'tangjingwei', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '总经办-法务部', '法务', 'ACTIVE'),
('陈楠', 'HXJ032', 'chennan', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '总经办-法务部', '法务主管', 'ACTIVE'),
('朱铭骏', 'HXJ033', 'zhumingjun', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '人力行政中心-行政部', '行政专员', 'ACTIVE'),
('刘颖宁', 'HXJ034', 'liuyingning', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '业务支持中心-运营服务部', '商务专员', 'ACTIVE'),
('杨淑欢', 'HXJ035', 'yangshuhuan', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '业务支持中心-运营服务部', '商务专员', 'ACTIVE'),
('龚蓉', 'HXJ036', 'gongrong', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '业务支持中心-运营服务部', '商务专员', 'ACTIVE'),
('林丽婷', 'HXJ037', 'linliting', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '资管中心', '出纳', 'ACTIVE'),
('陈伟璇', 'HXJ038', 'chenweixuan', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '资管中心', '出纳', 'ACTIVE'),
('沈盼静', 'HXJ039', 'shenpanjing', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '资管中心', '出纳', 'ACTIVE'),
('李展仪', 'HXJ040', 'lizhanyi', '$2y$10$eWcufB2byZqOZspjH6UaO.4PKvivPpotPILxGL1LscJspy4a5t0VW', '资管中心', '出纳', 'ACTIVE');

-- 6. 规范中的角色成员映射（同一员工可分配多个角色）
CREATE TEMPORARY TABLE seed_user_role (
    user_name VARCHAR(50) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_name, role_name)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
INSERT INTO seed_user_role (user_name, role_name) VALUES
('林安然','超级管理员'),
('连力','公司领导'),('王强','公司领导'),
('王强','执行总经理'),
('王强','一级中心负责人'),('舒飞','一级中心负责人'),('卢乙芬','一级中心负责人'),('刘婷','一级中心负责人'),('王海亮','一级中心负责人'),('张龙','一级中心负责人'),
('刘佳慧','二级部门负责人'),('陈成辉','二级部门负责人'),('刘婷','二级部门负责人'),('郭静宇','二级部门负责人'),('吴荷珍','二级部门负责人'),('张龙','二级部门负责人'),('王莹','二级部门负责人'),('黄政哲','二级部门负责人'),('李林华','二级部门负责人'),('苗福鑫','二级部门负责人'),('卢乙芬','二级部门负责人'),('舒飞','二级部门负责人'),('翁建发','二级部门负责人'),
('汪洋','会计主管&内控'),('姚志豪','会计主管&内控'),('翁婷婷','会计主管&内控'),
('冯训漪','核算会计'),('汪洋','核算会计'),('朱昀怡','核算会计'),('施惠君','核算会计'),('蔡赐绵','核算会计'),('姚志豪','核算会计'),('李小妹','核算会计'),('石瀚文','核算会计'),('张欣怡','核算会计'),('温超群','核算会计'),
('孙倩倩','内控主管'),('郑宁静','内控专员'),('唐菁蔚','法务'),('陈楠','法务主管'),
('朱铭骏','行政专员'),('刘佳慧','行政专员'),('刘佳慧','行政主管'),
('刘颖宁','商务专员'),('杨淑欢','商务专员'),('龚蓉','商务专员'),
('黄政哲','运营服务主管'),('张龙','交付主管'),('吴荷珍','财务经理'),('刘婷','出纳主管'),
('林丽婷','出纳'),('陈伟璇','出纳'),('翁婷婷','出纳'),('沈盼静','出纳'),('李展仪','出纳');
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM seed_user_role s
JOIN sys_user u ON u.name = s.user_name
JOIN sys_role r ON r.name = s.role_name;
DROP TEMPORARY TABLE seed_user_role;

-- 7. 20 条预置流程
INSERT INTO flow_config (type, category) VALUES
('闭店', 'BUSINESS'),
('采购申请', 'DAILY'),
('低值易耗品（含办公物品）领用', 'DAILY'),
('非标合同审批及用印', 'SEAL'),
('费用报销', 'DAILY'),
('费用预算', 'DAILY'),
('应付款申请', 'BUSINESS'),
('公司发文申请', 'DAILY'),
('固定资产报废报损', 'DAILY'),
('固定资产调拨', 'DAILY'),
('固定资产入库', 'DAILY'),
('黄金业务开户', 'BUSINESS'),
('借款申请', 'DAILY'),
('客户交易手续费调整', 'BUSINESS'),
('客户结算服务费', 'BUSINESS'),
('提货人新增或变更', 'BUSINESS'),
('通用审批申请', 'SEAL'),
('业务招待申请', 'DAILY'),
('银行账户管理调整', 'BUSINESS'),
('用印及证照申请', 'SEAL');

CREATE TEMPORARY TABLE seed_flow_node (
    flow_type VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL,
    node_name VARCHAR(100) NOT NULL,
    node_type VARCHAR(30) NOT NULL,
    assignee_role VARCHAR(100) NULL,
    PRIMARY KEY (flow_type, sort_order)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
INSERT INTO seed_flow_node VALUES
('闭店',0,'发起人','START',NULL),('闭店',1,'条件分支','CONDITION',NULL),('闭店',2,'直属主管','APPROVAL','二级部门负责人'),('闭店',3,'商务专员/运营服务主管','APPROVAL','商务专员/运营服务主管'),('闭店',4,'执行总经理/公司领导','APPROVAL','执行总经理/公司领导'),('闭店',5,'会计主管&内控','APPROVAL','会计主管&内控'),('闭店',6,'出纳','APPROVAL','出纳'),('闭店',7,'抄送相关负责人','CC',NULL),
('采购申请',0,'发起人','START',NULL),('采购申请',1,'直属主管','APPROVAL','二级部门负责人'),('采购申请',2,'采购询价','HANDLER',NULL),('采购申请',3,'行政主管（按需）','APPROVAL','行政主管'),('采购申请',4,'会计主管&内控','APPROVAL','会计主管&内控'),('采购申请',5,'执行总经理（≥2万元）','APPROVAL','执行总经理'),('采购申请',6,'采购办理','HANDLER',NULL),('采购申请',7,'发起人签收','HANDLER',NULL),
('低值易耗品（含办公物品）领用',0,'发起人','START',NULL),('低值易耗品（含办公物品）领用',1,'直属主管','APPROVAL','二级部门负责人'),('低值易耗品（含办公物品）领用',2,'行政专员发放','HANDLER','行政专员'),('低值易耗品（含办公物品）领用',3,'发起人签收','HANDLER',NULL),
('非标合同审批及用印',0,'发起人','START',NULL),('非标合同审批及用印',1,'直属主管','APPROVAL','二级部门负责人'),('非标合同审批及用印',2,'会计主管&内控（涉及资金）','APPROVAL','会计主管&内控'),('非标合同审批及用印',3,'法务','APPROVAL','法务'),('非标合同审批及用印',4,'法务主管','APPROVAL','法务主管'),('非标合同审批及用印',5,'公司领导（涉及资金）','APPROVAL','公司领导'),('非标合同审批及用印',6,'内控专员用印','HANDLER','内控专员'),('非标合同审批及用印',7,'发起人上传归档附件','HANDLER',NULL),
('费用报销',0,'发起人','START',NULL),('费用报销',1,'直属主管','APPROVAL','二级部门负责人'),('费用报销',2,'会计（按部门）','APPROVAL','核算会计'),('费用报销',3,'会计主管&内控','APPROVAL','会计主管&内控'),('费用报销',4,'公司领导（大额）','APPROVAL','公司领导'),('费用报销',5,'出纳','APPROVAL','出纳'),('费用报销',6,'抄送相关负责人','CC',NULL),
('费用预算',0,'发起人','START',NULL),('费用预算',1,'直属主管','APPROVAL','二级部门负责人'),('费用预算',2,'会计主管&内控','APPROVAL','会计主管&内控'),('费用预算',3,'发起人自选抄送','CC',NULL),
('应付款申请',0,'发起人','START',NULL),('应付款申请',1,'直属主管','APPROVAL','二级部门负责人'),('应付款申请',2,'会计（按部门）','APPROVAL','核算会计'),('应付款申请',3,'会计主管&内控','APPROVAL','会计主管&内控'),('应付款申请',4,'公司领导（大额）','APPROVAL','公司领导'),('应付款申请',5,'出纳','APPROVAL','出纳'),('应付款申请',6,'抄送相关负责人','CC',NULL),
('公司发文申请',0,'发起人','START',NULL),('公司发文申请',1,'逐级主管','APPROVAL','一级中心负责人/二级部门负责人'),('公司发文申请',2,'法务','APPROVAL','法务'),('公司发文申请',3,'内控主管','APPROVAL','内控主管'),('公司发文申请',4,'执行总经理','APPROVAL','执行总经理'),('公司发文申请',5,'行政专员编号发文','HANDLER','行政专员'),('公司发文申请',6,'发起人自选抄送','CC',NULL),
('固定资产报废报损',0,'发起人','START',NULL),('固定资产报废报损',1,'资产归属部门确认','APPROVAL','二级部门负责人'),('固定资产报废报损',2,'财务经理','APPROVAL','财务经理'),('固定资产报废报损',3,'执行总经理','APPROVAL','执行总经理'),('固定资产报废报损',4,'资产管理员办理','HANDLER','行政专员'),('固定资产报废报损',5,'抄送管理人员','CC',NULL),
('固定资产调拨',0,'发起人','START',NULL),('固定资产调拨',1,'调出部门确认','APPROVAL','二级部门负责人'),('固定资产调拨',2,'调入部门确认','APPROVAL','二级部门负责人'),('固定资产调拨',3,'调出/调入资产管理员办理','HANDLER','行政专员'),('固定资产调拨',4,'抄送财务及内控','CC',NULL),
('固定资产入库',0,'发起人','START',NULL),('固定资产入库',1,'直属主管','APPROVAL','二级部门负责人'),('固定资产入库',2,'行政专员入库登记','HANDLER','行政专员'),('固定资产入库',3,'抄送财务经理','CC','财务经理'),
('黄金业务开户',0,'发起人','START',NULL),('黄金业务开户',1,'公司领导','APPROVAL','公司领导'),('黄金业务开户',2,'抄送内控及内控主管','CC','内控主管'),('黄金业务开户',3,'发起人办理','HANDLER',NULL),('黄金业务开户',4,'抄送总经办/法务/商务','CC',NULL),
('借款申请',0,'发起人','START',NULL),('借款申请',1,'直属主管','APPROVAL','二级部门负责人'),('借款申请',2,'会计主管&内控','APPROVAL','会计主管&内控'),('借款申请',3,'会计处理','HANDLER','核算会计'),('借款申请',4,'出纳付款并上传回单','HANDLER','出纳'),('借款申请',5,'抄送财务及公司领导','CC',NULL),
('客户交易手续费调整',0,'发起人','START',NULL),('客户交易手续费调整',1,'执行总经理','APPROVAL','执行总经理'),('客户交易手续费调整',2,'抄送财务/内控/法务/运营服务','CC',NULL),
('客户结算服务费',0,'发起人','START',NULL),('客户结算服务费',1,'会计（按业务类型）','APPROVAL','核算会计'),('客户结算服务费',2,'财务经理/内控主管/财务总监','APPROVAL','财务经理/内控主管'),('客户结算服务费',3,'会计主管&内控','APPROVAL','会计主管&内控'),('客户结算服务费',4,'出纳或商务对账','HANDLER','出纳/商务专员'),('客户结算服务费',5,'抄送相关负责人','CC',NULL),
('提货人新增或变更',0,'发起人','START',NULL),('提货人新增或变更',1,'交付主管','APPROVAL','交付主管'),('提货人新增或变更',2,'抄送内控主管及总经办','CC',NULL),
('通用审批申请',0,'发起人','START',NULL),('通用审批申请',1,'逐级主管','APPROVAL','一级中心负责人/二级部门负责人'),('通用审批申请',2,'会计主管&内控（涉及资金）/执行总经理','APPROVAL','会计主管&内控/执行总经理'),('通用审批申请',3,'抄送总经办','CC',NULL),
('业务招待申请',0,'发起人','START',NULL),('业务招待申请',1,'直属主管','APPROVAL','二级部门负责人'),('业务招待申请',2,'会计主管&内控','APPROVAL','会计主管&内控'),('业务招待申请',3,'执行总经理','APPROVAL','执行总经理'),('业务招待申请',4,'抄送总经办','CC',NULL),
('银行账户管理调整',0,'发起人','START',NULL),('银行账户管理调整',1,'公司领导','APPROVAL','公司领导'),('银行账户管理调整',2,'出纳确认','APPROVAL','出纳'),('银行账户管理调整',3,'内控主管','APPROVAL','内控主管'),('银行账户管理调整',4,'抄送财务/法务/商务','CC',NULL),
('用印及证照申请',0,'发起人','START',NULL),('用印及证照申请',1,'直属主管','APPROVAL','二级部门负责人'),('用印及证照申请',2,'条件分支','CONDITION',NULL),('用印及证照申请',3,'运营服务主管/会计','APPROVAL','运营服务主管/核算会计'),('用印及证照申请',4,'会计主管&内控','APPROVAL','会计主管&内控'),('用印及证照申请',5,'财务经理','APPROVAL','财务经理'),('用印及证照申请',6,'公司领导','APPROVAL','公司领导'),('用印及证照申请',7,'内控专员用印或领取证照','HANDLER','内控专员'),('用印及证照申请',8,'发起人归还/商务归档','HANDLER','商务专员'),('用印及证照申请',9,'抄送内控主管','CC','内控主管');

INSERT INTO flow_node_config (flow_config_id, name, node_type, assignee_role, sort_order)
SELECT f.id, s.node_name, s.node_type, s.assignee_role, s.sort_order
FROM seed_flow_node s JOIN flow_config f ON f.type = s.flow_type;
DROP TEMPORARY TABLE seed_flow_node;

CREATE TEMPORARY TABLE seed_flow_condition (
    flow_type VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL,
    variable_name VARCHAR(100) NOT NULL,
    operator VARCHAR(50) NOT NULL,
    expected_value VARCHAR(200) NOT NULL,
    target_node_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (flow_type, sort_order)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
INSERT INTO seed_flow_condition VALUES
('闭店',0,'businessMode','EQUAL','DIRECT_SALES','商务专员/运营服务主管'),
('采购申请',0,'requiresAdminReview','EQUAL','true','行政主管（按需）'),
('采购申请',1,'amount','GREATER_THAN_OR_EQUAL','20000','执行总经理（≥2万元）'),
('非标合同审批及用印',0,'involvesFunds','EQUAL','true','会计主管&内控（涉及资金）'),
('非标合同审批及用印',1,'involvesFunds','EQUAL','true','公司领导（涉及资金）'),
('费用报销',0,'amount','GREATER_THAN_OR_EQUAL','80000','公司领导（大额）'),
('应付款申请',0,'amount','GREATER_THAN_OR_EQUAL','80000','公司领导（大额）'),
('通用审批申请',0,'involvesFunds','EQUAL','true','会计主管&内控（涉及资金）/执行总经理'),
('用印及证照申请',0,'involvesFunds','EQUAL','true','运营服务主管/会计');
INSERT INTO flow_condition_rule
(flow_config_id, variable_name, operator, expected_value, target_node_name, sort_order)
SELECT f.id, s.variable_name, s.operator, s.expected_value, s.target_node_name, s.sort_order
FROM seed_flow_condition s JOIN flow_config f ON f.type = s.flow_type;
DROP TEMPORARY TABLE seed_flow_condition;

-- 8. 快捷单据目录：日常付款 37 项、业务付款 9 项、用印申请 3 项
INSERT INTO quick_document (business_type, name, sort_order) VALUES
('DAILY_PAYMENT','闭店',1),('DAILY_PAYMENT','采购申请',2),('DAILY_PAYMENT','低值易耗品（含办公物品）领用',3),('DAILY_PAYMENT','费用报销',4),('DAILY_PAYMENT','费用预算',5),('DAILY_PAYMENT','公司发文申请',6),('DAILY_PAYMENT','固定资产报废报损',7),('DAILY_PAYMENT','固定资产调拨',8),('DAILY_PAYMENT','固定资产入库',9),('DAILY_PAYMENT','黄金业务开户',10),('DAILY_PAYMENT','借款申请',11),('DAILY_PAYMENT','客户交易手续费调整',12),('DAILY_PAYMENT','客户结算服务费',13),('DAILY_PAYMENT','提货人新增或变更',14),('DAILY_PAYMENT','业务招待申请',15),('DAILY_PAYMENT','银行账户管理调整',16),('DAILY_PAYMENT','招待费',17),('DAILY_PAYMENT','差旅费',18),('DAILY_PAYMENT','员工加班餐费',19),('DAILY_PAYMENT','员工加班交通费',20),('DAILY_PAYMENT','检测费证书费',21),('DAILY_PAYMENT','快递费',22),('DAILY_PAYMENT','保险费',23),('DAILY_PAYMENT','平台保证金',24),('DAILY_PAYMENT','电商投流推广费充值',25),('DAILY_PAYMENT','合作方退款',26),('DAILY_PAYMENT','聚水潭接口费',27),('DAILY_PAYMENT','日常费用（已经垫付）',28),('DAILY_PAYMENT','日常费用（未垫付）',29),('DAILY_PAYMENT','租金物业费',30),('DAILY_PAYMENT','装修费',31),('DAILY_PAYMENT','技术服务费',32),('DAILY_PAYMENT','其他专项服务费',33),('DAILY_PAYMENT','广告费',34),('DAILY_PAYMENT','宽带费电信费',35),('DAILY_PAYMENT','固定资产采购',36),('DAILY_PAYMENT','税费缴纳',37),
('BUSINESS_PAYMENT','应付款申请',1),('BUSINESS_PAYMENT','加工费',2),('BUSINESS_PAYMENT','直销代销结算服务费',3),('BUSINESS_PAYMENT','供应商货款',4),('BUSINESS_PAYMENT','员工工资社保公积金个税',5),('BUSINESS_PAYMENT','备用金申请',6),('BUSINESS_PAYMENT','银行贴现利息',7),('BUSINESS_PAYMENT','低风险贸易业务货款保证金',8),('BUSINESS_PAYMENT','借款利息',9),
('SEAL_APPLICATION','非标合同审批及用印',1),('SEAL_APPLICATION','通用审批申请',2),('SEAL_APPLICATION','用印及证照申请',3);

-- 9. 示例单据
INSERT INTO oa_document
(doc_code, business_type, doc_type, project_name, contract_no, applicant_id, company, department,
 amount, invoice_summary, reason, need_post_material, status, current_node, risk_flag)
VALUES
('BX202608260018','DAILY_PAYMENT','DAILY_APPLICATION','深圳出差费用报销',NULL,
 (SELECT id FROM sys_user WHERE account='linanran'),'HAI_XIA_JIN','总经办',4860.00,
 '增值税电子普通发票3张','深圳出差费用报销',0,'PENDING','核算会计',0),
('FK202608260031','BUSINESS_PAYMENT','PAYMENT_APPLICATION','8月供应商货款','HT2026-0801',
 (SELECT id FROM sys_user WHERE account='liuyingning'),'HAI_XIA_JIN_SUPPLY_CHAIN','业务支持中心-运营服务部',286500.00,
 '增值税专用发票5张','支付8月供应商货款',1,'APPROVING','会计主管&内控',1),
('FK202608240019','BUSINESS_PAYMENT','PAYMENT_APPLICATION','固定资产采购','HT2026-0726',
 (SELECT id FROM sys_user WHERE account='zhumingjun'),'HAI_XIA_JIN','人力行政中心-行政部',45800.00,
 '增值税专用发票1张','固定资产采购付款',0,'APPROVED','已完成',0),
('YY202608290001','SEAL_APPLICATION','SEAL_APPLICATION','非标合同审批及用印',NULL,
 (SELECT id FROM sys_user WHERE account='tangjingwei'),NULL,'总经办-法务部',NULL,
 NULL,NULL,0,'PENDING','直属主管',0);

UPDATE oa_document SET
seal_project='供应商合同盖章', seal_department='总经办-法务部',
seal_time='2026-08-30 10:00:00', file_name='供应商合同.pdf',
seal_type='CONTRACT_SEAL', seal_reason='合同签署'
WHERE doc_code='YY202608290001';

INSERT INTO archive_ledger
(doc_id, doc_code, project_name, business_type, doc_type, company, applicant, department, amount)
SELECT d.id, d.doc_code, d.project_name, d.business_type, d.doc_type, d.company, u.name, d.department, d.amount
FROM oa_document d JOIN sys_user u ON u.id=d.applicant_id
WHERE d.doc_code='FK202608240019' AND d.status='APPROVED';

-- 10. 收紧核心关联约束（所有预置及历史初始数据已经完成映射）
ALTER TABLE sys_role MODIFY COLUMN data_scope_id BIGINT NOT NULL;