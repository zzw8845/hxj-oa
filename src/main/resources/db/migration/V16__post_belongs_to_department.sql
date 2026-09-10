-- 岗位归属部门（对齐 role-permission 规范："部门下的岗位、角色及权限"）：
-- 岗位挂到部门下，员工选部门后岗位联动过滤；department_id 为空的岗位为通用岗位。

ALTER TABLE sys_post
    ADD COLUMN department_id BIGINT NULL COMMENT '归属部门ID，空表示通用岗位' AFTER name;

-- 存量回填：按使用该岗位的员工所在部门归属
UPDATE sys_post p
JOIN sys_user u ON u.post_id = p.id
JOIN sys_department d ON d.id = u.department_id
SET p.department_id = d.id
WHERE p.department_id IS NULL;

ALTER TABLE sys_post
    ADD CONSTRAINT fk_post_department FOREIGN KEY (department_id) REFERENCES sys_department(id);
