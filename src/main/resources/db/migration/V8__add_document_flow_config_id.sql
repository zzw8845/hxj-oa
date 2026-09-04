-- 单据表增加流程配置ID：记录提交时的流程配置版本，保证驳回时按历史配置解析目标节点。

-- 幂等处理：仅当列不存在时才添加
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'oa_document'
      AND COLUMN_NAME = 'flow_config_id'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE oa_document ADD COLUMN flow_config_id BIGINT NULL COMMENT ''提交时的流程配置ID'' AFTER process_instance_id',
    'SELECT ''Column flow_config_id already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 幂等处理：仅当外键不存在时才添加
SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'oa_document'
      AND CONSTRAINT_NAME = 'fk_doc_flow_config'
);

SET @sql2 = IF(@fk_exists = 0,
    'ALTER TABLE oa_document ADD CONSTRAINT fk_doc_flow_config FOREIGN KEY (flow_config_id) REFERENCES flow_config(id)',
    'SELECT ''Constraint fk_doc_flow_config already exists'' AS message'
);

PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;