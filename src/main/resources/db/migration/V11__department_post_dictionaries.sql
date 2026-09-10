-- 部门/岗位字典化：闭包表模型支持任意层级；sys_user 改为字典外键引用，
-- 原 department/post 字符串列保留为展示快照（与单据快照逻辑一致），由服务层同步维护。

-- 1. 部门表（支持任意层级，parent_id 为空表示根部门）
CREATE TABLE sys_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '部门名称（全公司唯一）',
    parent_id BIGINT NULL COMMENT '上级部门ID，根部门为空',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '同级排序号',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_department_name UNIQUE (name),
    CONSTRAINT fk_department_parent FOREIGN KEY (parent_id) REFERENCES sys_department(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表（闭包表模型，支持任意层级）';

-- 2. 部门闭包表：祖先-后代路径（含自身 depth=0），子树查询/移动均为一条 SQL
CREATE TABLE sys_department_closure (
    ancestor_id BIGINT NOT NULL,
    descendant_id BIGINT NOT NULL,
    depth INT NOT NULL COMMENT '祖先到后代的距离，自身为0',
    PRIMARY KEY (ancestor_id, descendant_id),
    CONSTRAINT fk_closure_ancestor FOREIGN KEY (ancestor_id) REFERENCES sys_department(id) ON DELETE CASCADE,
    CONSTRAINT fk_closure_descendant FOREIGN KEY (descendant_id) REFERENCES sys_department(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门闭包表：祖先-后代路径';

-- 3. 岗位字典表
CREATE TABLE sys_post (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '岗位名称（唯一）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_post_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位字典表';

-- 4. 预置部门：一级中心 + 二级部门（与 V6 员工数据命名对齐）
--    "各一级中心/各二级部门"为 V6 演示占位部门，真实运营时可移动员工后删除。
INSERT INTO sys_department (id, name, parent_id, sort_order) VALUES
(1, '总经办', NULL, 1),
(2, '财务中心', NULL, 2),
(3, '人力行政中心', NULL, 3),
(4, '业务支持中心', NULL, 4),
(5, '供应链中心', NULL, 5),
(6, '资管中心', NULL, 6),
(7, '各一级中心', NULL, 90),
(8, '各二级部门', NULL, 91),
(9, '总经办-内控部', 1, 1),
(10, '总经办-法务部', 1, 2),
(11, '财务中心-财务部', 2, 1),
(12, '人力行政中心-行政部', 3, 1),
(13, '业务支持中心-运营服务部', 4, 1),
(14, '供应链中心-交付部', 5, 1);

-- 5. 预置岗位（覆盖 V6 全部员工岗位取值）
INSERT INTO sys_post (name) VALUES
('系统管理员'), ('公司领导'), ('执行总经理'), ('中心负责人'), ('部门负责人'),
('会计主管/内控'), ('会计'), ('内控主管'), ('内控专员'), ('法务'), ('法务主管'),
('行政专员'), ('行政主管'), ('商务专员'), ('运营服务主管'), ('交付主管'),
('财务经理'), ('出纳主管'), ('出纳');

-- 6. 历史数据自愈：历史员工使用过的部门/岗位字符串若不在预置字典中，
--    自动补入字典（排在末尾），保证回填零失败。历史脏值可在上线后由管理员规整。
INSERT INTO sys_department (name, sort_order)
SELECT DISTINCT u.department, 99
FROM sys_user u
WHERE u.department IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_department d WHERE d.name = u.department);

INSERT INTO sys_post (name)
SELECT DISTINCT u.post
FROM sys_user u
WHERE u.post IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_post p WHERE p.name = u.post);

-- 7. 闭包路径种子：自身路径 + 父子路径（通用写法，预置数据最大深度为 1）
INSERT INTO sys_department_closure (ancestor_id, descendant_id, depth)
SELECT id, id, 0 FROM sys_department;

INSERT INTO sys_department_closure (ancestor_id, descendant_id, depth)
SELECT p.id, c.id, 1
FROM sys_department c
JOIN sys_department p ON c.parent_id = p.id;

-- 8. sys_user 增加字典外键并回填（先以 NULL 加列，回填完成后再收紧为 NOT NULL）
ALTER TABLE sys_user
    ADD COLUMN department_id BIGINT NULL COMMENT '部门ID' AFTER department,
    ADD COLUMN post_id BIGINT NULL COMMENT '岗位ID' AFTER post;

UPDATE sys_user u
JOIN sys_department d ON d.name = u.department
SET u.department_id = d.id;

UPDATE sys_user u
JOIN sys_post p ON p.name = u.post
SET u.post_id = p.id;

ALTER TABLE sys_user
    MODIFY COLUMN department_id BIGINT NOT NULL COMMENT '部门ID',
    MODIFY COLUMN post_id BIGINT NOT NULL COMMENT '岗位ID',
    ADD CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES sys_department(id),
    ADD CONSTRAINT fk_user_post FOREIGN KEY (post_id) REFERENCES sys_post(id);
