-- 对齐钉钉组织模型：
-- 1) 岗位回退为全公司职种库（撤销 V16 的部门归属，岗位与部门解耦，员工岗位字段保留）
-- 2) 增加汇报线：sys_user.manager_id（直属主管），审批流「直属主管」节点据此路由

-- 1. 岗位回全局库
ALTER TABLE sys_post DROP FOREIGN KEY fk_post_department;
ALTER TABLE sys_post DROP COLUMN department_id;

-- 2. 汇报线（可空：员工可暂未设置直属主管）
ALTER TABLE sys_user
    ADD COLUMN manager_id BIGINT NULL COMMENT '直属主管ID（汇报线）' AFTER status;

ALTER TABLE sys_user
    ADD CONSTRAINT fk_user_manager FOREIGN KEY (manager_id) REFERENCES sys_user(id);
