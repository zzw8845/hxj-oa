-- 数据范围模型收敛为 5 种通用类型 + 角色自定义部门集合：
--   ALL          全部单据
--   OWN          仅本人单据（同时作为所有用户的基线权利，类型保留用于显式声明）
--   DEPT         本部门
--   DEPT_AND_CHILD 本部门及以下（基于部门闭包表）
--   CUSTOM       自定义部门集合（sys_role_scope_department）
-- 旧 19 个业务耦合 code 的映射（行为以旧 DocumentAccessPolicy 实际实现为准）：
--   ALL_DEPARTMENTS_ALL_NODES                                    -> ALL
--   OWN_CENTER_DOCUMENTS                                         -> DEPT_AND_CHILD
--   OWN_DEPARTMENT_DOCUMENTS                                     -> DEPT
--   FINANCE_INTERNAL_CONTROL / ASSIGNED_BUSINESS_DEPARTMENTS /
--   FINANCE_MANAGEMENT / FUNDS_ACCOUNTS / ASSIGNED_PAYMENT_LINES /
--   OPERATIONS_ADMIN_MATTERS / OPERATIONS_MATTERS / DELIVERY_PICKUP -> ALL
--     （旧实现为"全公司付款类单据可见"，收敛为全部单据可见的超集，低风险）
--   MAJOR_CONDITIONAL_APPROVAL / COMPANY_INTERNAL_CONTROL /
--   SEAL_COMPLIANCE / CONTRACT_OFFICIAL_DOCUMENT / CONTRACT_REVIEW /
--   ADMIN_ASSET / ADMIN_APPROVAL / SETTLEMENT_SEAL_ARCHIVE       -> OWN
--     （旧实现对这些 code 无任何列表可见性，仅靠任务兜底；收敛为本人单据基线，
--      审批场景仍由流程任务与可见性兜底覆盖）

-- 1. 新类型字典（不存在则插入）
INSERT INTO sys_data_scope (code, name, description)
SELECT * FROM (SELECT 'ALL', '全部单据', '可查看全部单据') AS seed
WHERE NOT EXISTS (SELECT 1 FROM sys_data_scope WHERE code = 'ALL');

INSERT INTO sys_data_scope (code, name, description)
SELECT * FROM (SELECT 'OWN', '仅本人单据', '仅可查看本人提交的单据') AS seed
WHERE NOT EXISTS (SELECT 1 FROM sys_data_scope WHERE code = 'OWN');

INSERT INTO sys_data_scope (code, name, description)
SELECT * FROM (SELECT 'DEPT', '本部门', '可查看本部门提交的单据') AS seed
WHERE NOT EXISTS (SELECT 1 FROM sys_data_scope WHERE code = 'DEPT');

INSERT INTO sys_data_scope (code, name, description)
SELECT * FROM (SELECT 'DEPT_AND_CHILD', '本部门及以下', '可查看本部门及其下级部门提交的单据') AS seed
WHERE NOT EXISTS (SELECT 1 FROM sys_data_scope WHERE code = 'DEPT_AND_CHILD');

INSERT INTO sys_data_scope (code, name, description)
SELECT * FROM (SELECT 'CUSTOM', '自定义部门集合', '可查看指定部门集合提交的单据') AS seed
WHERE NOT EXISTS (SELECT 1 FROM sys_data_scope WHERE code = 'CUSTOM');

-- 2. 角色自定义部门集合
CREATE TABLE sys_role_scope_department (
    role_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, department_id),
    CONSTRAINT fk_role_scope_dept_role
        FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_scope_dept_department
        FOREIGN KEY (department_id) REFERENCES sys_department(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色自定义数据范围部门集合';

-- 3. 旧 code -> 新类型重映射
UPDATE sys_role r
JOIN sys_data_scope old_scope ON old_scope.id = r.data_scope_id
JOIN sys_data_scope new_scope ON new_scope.code = 'ALL'
SET r.data_scope_id = new_scope.id
WHERE old_scope.code IN (
    'ALL_DEPARTMENTS_ALL_NODES',
    'FINANCE_INTERNAL_CONTROL', 'ASSIGNED_BUSINESS_DEPARTMENTS',
    'FINANCE_MANAGEMENT', 'FUNDS_ACCOUNTS', 'ASSIGNED_PAYMENT_LINES',
    'OPERATIONS_ADMIN_MATTERS', 'OPERATIONS_MATTERS', 'DELIVERY_PICKUP');

UPDATE sys_role r
JOIN sys_data_scope old_scope ON old_scope.id = r.data_scope_id
JOIN sys_data_scope new_scope ON new_scope.code = 'DEPT_AND_CHILD'
SET r.data_scope_id = new_scope.id
WHERE old_scope.code = 'OWN_CENTER_DOCUMENTS';

UPDATE sys_role r
JOIN sys_data_scope old_scope ON old_scope.id = r.data_scope_id
JOIN sys_data_scope new_scope ON new_scope.code = 'DEPT'
SET r.data_scope_id = new_scope.id
WHERE old_scope.code = 'OWN_DEPARTMENT_DOCUMENTS';

UPDATE sys_role r
JOIN sys_data_scope old_scope ON old_scope.id = r.data_scope_id
JOIN sys_data_scope new_scope ON new_scope.code = 'OWN'
SET r.data_scope_id = new_scope.id
WHERE old_scope.code IN (
    'MAJOR_CONDITIONAL_APPROVAL', 'COMPANY_INTERNAL_CONTROL',
    'SEAL_COMPLIANCE', 'CONTRACT_OFFICIAL_DOCUMENT', 'CONTRACT_REVIEW',
    'ADMIN_ASSET', 'ADMIN_APPROVAL', 'SETTLEMENT_SEAL_ARCHIVE',
    'OWN_DOCUMENTS');

-- 4. 清理旧业务耦合 code（重映射完成后无引用）
DELETE FROM sys_data_scope
WHERE code NOT IN ('ALL', 'OWN', 'DEPT', 'DEPT_AND_CHILD', 'CUSTOM');
