package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 审批节点的审批人解析协议（结构化，替代以中文节点名/角色名字符串特判的历史实现）。
 *
 * <p>节点 {@code name} 仅作展示，路由行为完全由 {@code assigneeType + assigneeValue} 决定：
 * <ul>
 *   <li>{@link #ROLE}：{@code assigneeValue} 存候选角色 ID（逗号分隔），部署为 Flowable 候选组；</li>
 *   <li>{@link #INITIATOR}：发起人回环（签收/归还/上传归档附件），指派申请人本人；</li>
 *   <li>{@link #MANAGER}：直属主管（主管链解析器产出链首，钉钉同款：人工指定优先→部门负责人树兜底）；</li>
 *   <li>{@link #MANAGER_CHAIN}：逐级主管，整条主管链串行审批；</li>
 *   <li>{@link #DEPT_ACCOUNTANT}：按申请人部门解析核算分工主办会计；</li>
 *   <li>{@link #SELF_SELECT}：发起人自选，提交时指定，按选择顺序串行审批。</li>
 * </ul>
 */
public enum AssigneeTypeEnum {
    ROLE,
    INITIATOR,
    MANAGER,
    MANAGER_CHAIN,
    DEPT_ACCOUNTANT,
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
