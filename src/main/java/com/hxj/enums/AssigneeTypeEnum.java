package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 审批节点的审批人解析<b>机制</b>（结构化，替代以中文节点名/角色名字符串特判的历史实现）。
 *
 * <p>设计原则：本枚举只承载<b>与业务无关的寻人机制</b>。任何具体业务概念
 * （核算会计、部门HR、出纳……）都必须表达为"机制 + 数据"，绝不新增枚举值——
 * 否则每来一个业务角色就要改代码、上枚举、建解析器（历史 DEPT_ACCOUNTANT 的教训）。
 *
 * <ul>
 *   <li>{@link #ROLE}：按角色找——{@code assigneeValue} 存候选角色 ID（逗号分隔），
 *       再由 {@code assigneeScope} 决定范围（{@link AssigneeScopeEnum#GLOBAL} 全部成员 /
 *       {@link AssigneeScopeEnum#INITIATOR_DEPT} 仅服务发起人部门者）；</li>
 *   <li>{@link #INITIATOR}：发起人回环（签收/归还/上传归档附件），指派申请人本人；</li>
 *   <li>{@link #MANAGER}：直属主管（主管链解析器产出链首，人工指定优先→部门负责人树兜底）；</li>
 *   <li>{@link #MANAGER_CHAIN}：逐级主管，整条主管链串行审批；</li>
 *   <li>{@link #SELF_SELECT}：发起人自选，提交时指定，按选择顺序串行审批。</li>
 * </ul>
 */
public enum AssigneeTypeEnum {
    ROLE,
    INITIATOR,
    MANAGER,
    MANAGER_CHAIN,
    SELF_SELECT;

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static AssigneeTypeEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }
}
