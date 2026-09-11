-- 权限点显示名诚实化：原名继承自原型演示文案，"全部/本人"字样暗示粒度，
-- 但这 4 个是二值能力门禁（粒度由数据范围负责），名字与语义对齐，杜绝误读。
-- 仅改 sys_permission.name 显示值，code 与勾选关系不变，代码零影响。

UPDATE sys_permission SET name = '可提交单据'           WHERE code = 'SUBMIT_ALL_FORMS';
UPDATE sys_permission SET name = '可访问单据'           WHERE code = 'VIEW_OWN_FORMS';
UPDATE sys_permission SET name = '超级审批（代审任意节点）' WHERE code = 'APPROVE_ALL_NODES';
-- CONFIGURE_FLOW_PERMISSION「配置流程与权限」命名本就准确，不动。
