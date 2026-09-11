-- 权限点字典精简：仅保留 4 个被代码硬约束（@PreAuthorize / 审批逻辑）真实引用的"真开关"。
-- 其余 40 个为原型演示遗留的声明性标签（假开关）——运行时无任何校验引用，
-- 勾选与否不改变系统行为，且会误导管理员以为勾选即授权。全部清除。
--
-- 保留清单（引用位置）：
--   CONFIGURE_FLOW_PERMISSION  管理接口 @PreAuthorize 门禁 + 作废管理员判定
--   APPROVE_ALL_NODES          超级审批人判定（可代审任意节点）
--   SUBMIT_ALL_FORMS           单据接口门禁（hasAnyAuthority 之一）
--   VIEW_OWN_FORMS             单据接口门禁（hasAnyAuthority 之一）
--
-- 真正的授权开关（与本迁移无关，勿混淆）：
--   数据范围类型（看多广）+ 流程节点 assignee_role 角色名（谁能审），均数据驱动。

-- 1. 先清角色-权限关联（外键引用方）
DELETE FROM sys_role_permission
WHERE permission_id IN (
    SELECT id FROM sys_permission
    WHERE code NOT IN ('CONFIGURE_FLOW_PERMISSION', 'APPROVE_ALL_NODES',
                       'SUBMIT_ALL_FORMS', 'VIEW_OWN_FORMS')
);

-- 2. 再清权限点本体
DELETE FROM sys_permission
WHERE code NOT IN ('CONFIGURE_FLOW_PERMISSION', 'APPROVE_ALL_NODES',
                   'SUBMIT_ALL_FORMS', 'VIEW_OWN_FORMS');
