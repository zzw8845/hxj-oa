-- 补充材料要求的解决状态：SUPPLEMENT 记录在材料回传后置为已解决。

ALTER TABLE approval_record
    ADD COLUMN resolved TINYINT(1) NOT NULL DEFAULT 1 COMMENT '补充材料要求是否已解决' AFTER evidence_file_id;
