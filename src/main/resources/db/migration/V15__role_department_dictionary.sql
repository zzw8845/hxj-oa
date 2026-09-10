-- 角色归属部门字典化：sys_role 增加部门外键（原字符串列保留为展示快照，
-- 由服务层在部门改名时同步），杜绝部门改名后角色引用悬空。

-- 1. 加外键列（可空：角色允许暂不归属部门，管理接口保存时强制要求选择）
ALTER TABLE sys_role
    ADD COLUMN department_id BIGINT NULL COMMENT '归属部门ID' AFTER department;

-- 2. 历史自愈：角色使用的部门字符串若不在字典中，自动补入字典
INSERT INTO sys_department (name, sort_order)
SELECT DISTINCT r.department, 98
FROM sys_role r
WHERE r.department IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_department d WHERE d.name = r.department);

-- 3. 按名称回填外键
UPDATE sys_role r
JOIN sys_department d ON d.name = r.department
SET r.department_id = d.id;

ALTER TABLE sys_role
    ADD CONSTRAINT fk_role_department FOREIGN KEY (department_id) REFERENCES sys_department(id);
