-- 修正列名与实体映射对齐（primaryDepartment → primary_department）
ALTER TABLE sys_user_department CHANGE is_primary primary_department TINYINT(1) NOT NULL DEFAULT 0;
