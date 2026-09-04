-- 为单据补充申请人和附件上传人的实体关联及附件 MIME 类型。

ALTER TABLE oa_document
    ADD COLUMN applicant_id BIGINT NULL COMMENT '申请人ID' AFTER contract_no,
    ADD CONSTRAINT fk_document_applicant
        FOREIGN KEY (applicant_id) REFERENCES sys_user(id);

ALTER TABLE oa_attachment
    ADD COLUMN content_type VARCHAR(100) NULL COMMENT '文件MIME类型' AFTER file_path,
    ADD COLUMN uploader_id BIGINT NULL COMMENT '上传人ID' AFTER file_size,
    ADD CONSTRAINT fk_attachment_uploader
        FOREIGN KEY (uploader_id) REFERENCES sys_user(id);

ALTER TABLE oa_document
    MODIFY COLUMN applicant VARCHAR(50) NULL COMMENT '旧申请人姓名（兼容字段）';

-- V1 的 applicant、uploader 文本列在任务 2.5 写入并迁移种子数据后清理。