-- 清理 V6 预置的原型演示示例单据（4 条 oa_document 及其关联数据）。
-- 真实运营数据应通过接口产生，演示快照不应留存于生产库。
-- 按 oa_document 子表外键方向逐表清理。

DELETE FROM oa_attachment
WHERE doc_id IN (
    SELECT id FROM oa_document
    WHERE doc_code IN ('BX202608260018', 'FK202608260031', 'FK202608240019', 'YY202608290001'));

DELETE FROM approval_record
WHERE doc_id IN (
    SELECT id FROM oa_document
    WHERE doc_code IN ('BX202608260018', 'FK202608260031', 'FK202608240019', 'YY202608290001'));

DELETE FROM cc_record
WHERE doc_id IN (
    SELECT id FROM oa_document
    WHERE doc_code IN ('BX202608260018', 'FK202608260031', 'FK202608240019', 'YY202608290001'));

DELETE FROM archive_ledger
WHERE doc_id IN (
    SELECT id FROM oa_document
    WHERE doc_code IN ('BX202608260018', 'FK202608260031', 'FK202608240019', 'YY202608290001'));

DELETE FROM oa_document
WHERE doc_code IN ('BX202608260018', 'FK202608260031', 'FK202608240019', 'YY202608290001');
