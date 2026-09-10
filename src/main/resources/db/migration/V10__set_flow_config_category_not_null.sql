-- 流程分类为必填：flow_config.category 收紧为 NOT NULL，与实体 nullable=false 对齐。
-- 存量数据均已填写分类，无需回填；COMMENT 需一并带上，否则会丢失 V9 同步的注释。

ALTER TABLE flow_config
    MODIFY COLUMN category VARCHAR(50) NOT NULL
    COMMENT '分类(日常/业务/用印)';
