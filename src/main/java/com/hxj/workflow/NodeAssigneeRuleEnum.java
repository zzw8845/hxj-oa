package com.hxj.workflow;

/**
 * 审批节点的指派规则类型——动态指派特判的策略收敛点。
 *
 * <p>历史上以 assigneeRole/节点名字符串特判（直属主管、发起人前缀、会计（按部门））
 * 散落在部署与校验两处，收敛于此。新增动态规则的步骤：
 * 加一个枚举值 + 在 of() 归类 + 在消费方（部署 createElement、保存校验）switch 一行。
 */
public enum NodeAssigneeRuleEnum {

    /** 静态角色：assigneeRole 即 RBAC 角色名（多角色用 / 、 分隔），部署为候选组。 */
    STATIC_ROLE,

    /** 发起人回环：节点名以「发起人」开头（签收/归还/上传归档附件），指派给申请人。 */
    INITIATOR,

    /** 直属主管：按汇报线动态指派给申请人的直属主管。 */
    MANAGER,

    /** 按部门职能：「会计（按部门）」，按核算分工解析主办会计。 */
    DEPT_ROLE;

    /**
     * 归类一个节点。优先级：发起人节点名前缀 &gt; 直属主管 &gt; 会计（按部门） &gt; 静态角色。
     */
    public static NodeAssigneeRuleEnum of(String nodeName, String assigneeRole) {
        if (nodeName != null && nodeName.startsWith("发起人")) {
            return INITIATOR;
        }
        if ("直属主管".equals(assigneeRole)) {
            return MANAGER;
        }
        if ("会计（按部门）".equals(assigneeRole)) {
            return DEPT_ROLE;
        }
        return STATIC_ROLE;
    }

    /** 是否静态角色节点：仅这类节点需要做"审批人引用的角色必须存在"校验。 */
    public boolean isStaticRole() {
        return this == STATIC_ROLE;
    }
}
