package com.hxj.workflow;

import java.util.Map;
import java.util.Set;

/**
 * 流程变量契约——变量名与类型的单一真相，三处消费共用（提交链路 / 保存关口 / BPMN 生成），
 * 杜绝各处硬编码后漂移。
 *
 * <p>变量分三类：
 * <ol>
 *   <li><b>按节点派生的变量</b>（{@link #assigneeVariable}/{@link #candidatesVariable}/
 *       {@link #skipVariable}/{@link #autoRejectVariable}）：提交时解析写入，BPMN 侧以表达式引用，
 *       同一流程多个同类节点各自独立（以节点 ID 区分）；</li>
 *   <li><b>系统字段</b>（{@link #SYS_INITIATOR} 等）：可由条件分支直接引用，钉钉同款
 *       "按发起人部门/岗位分流"由此表达；</li>
 *   <li><b>表单字段</b>（模板声明"参与流程条件"）：由 {@code ConditionVariableCatalog} 动态求。</li>
 * </ol>
 */
public final class WorkflowVariables {

    private WorkflowVariables() {
    }

    // ==================== 按节点派生的变量 ====================

    private static final String ASSIGNEE_PREFIX = "assignee_";
    private static final String CANDIDATES_PREFIX = "candidates_";
    private static final String SKIP_PREFIX = "skip_";
    private static final String AUTO_REJECT_PREFIX = "autoReject_";

    /** 节点主办账号变量名（或签单指派、兜底指派）。 */
    public static String assigneeVariable(Long nodeId) {
        return ASSIGNEE_PREFIX + nodeId;
    }

    /** 节点候选账号列表变量名（会签/依次及多候选人或签的多实例输入）。 */
    public static String candidatesVariable(Long nodeId) {
        return CANDIDATES_PREFIX + nodeId;
    }

    /** 节点跳过变量名（空策略=自动通过时为 true，BPMN 前置网关据此旁路）。 */
    public static String skipVariable(Long nodeId) {
        return SKIP_PREFIX + nodeId;
    }

    /** 节点自动拒绝变量名（空策略=自动拒绝时为 true，BPMN 前置网关据此转到拒绝处理器）。 */
    public static String autoRejectVariable(Long nodeId) {
        return AUTO_REJECT_PREFIX + nodeId;
    }

    // ==================== 系统字段（条件分支可直接引用） ====================

    /** 发起人账号。 */
    public static final String SYS_INITIATOR = "sysInitiator";
    /** 发起人所在部门 ID（数值，支持"属于某几个部门"的集合判断）。 */
    public static final String SYS_INITIATOR_DEPT_ID = "sysInitiatorDeptId";
    /** 发起人所在部门名称。 */
    public static final String SYS_INITIATOR_DEPT_NAME = "sysInitiatorDeptName";
    /** 发起人岗位名称。 */
    public static final String SYS_INITIATOR_POST = "sysInitiatorPost";

    /** 系统字段集合（条件变量目录的固定部分）。 */
    public static final Set<String> SYSTEM_VARIABLES =
            Set.of(SYS_INITIATOR, SYS_INITIATOR_DEPT_ID, SYS_INITIATOR_DEPT_NAME, SYS_INITIATOR_POST);

    /**
     * 保留键兜底类型：模板未定义同名字段时仍可用的条件变量。
     * 一旦模板定义了同名字段，以字段控件类型为准。
     */
    private static final Map<String, ValueTypeEnum> RESERVED_KEY_TYPES = Map.of(
            "amount", ValueTypeEnum.NUMERIC,
            "involvesFunds", ValueTypeEnum.BOOLEAN,
            "requiresAdminReview", ValueTypeEnum.BOOLEAN,
            "businessMode", ValueTypeEnum.STRING,
            "needPostMaterial", ValueTypeEnum.BOOLEAN);

    /** 保留键兜底条件变量集合（无绑定模板时的可用判据）。 */
    public static final Set<String> RESERVED_KEY_VARIABLES = RESERVED_KEY_TYPES.keySet();

    /** 保留键的兜底类型；非保留键返回 null。 */
    public static ValueTypeEnum reservedKeyType(String variableName) {
        return RESERVED_KEY_TYPES.get(variableName);
    }

    /** 系统字段的类型（供保存关口校验比较值）。 */
    public static ValueTypeEnum systemVariableType(String variableName) {
        return switch (variableName) {
            case SYS_INITIATOR_DEPT_ID -> ValueTypeEnum.NUMERIC;
            case SYS_INITIATOR, SYS_INITIATOR_DEPT_NAME, SYS_INITIATOR_POST -> ValueTypeEnum.STRING;
            default -> null;
        };
    }

    // ==================== 类型 ====================

    /** 变量值类型（保存关口按此校验比较值合法性）。 */
    public enum ValueTypeEnum { NUMERIC, BOOLEAN, STRING }

    /** 表单控件类型 → 条件变量值类型（NUMBER 数值、BOOLEAN 布尔、其余按字符串比较）。 */
    public static ValueTypeEnum valueTypeOfControlType(String controlType) {
        if (controlType == null) {
            return ValueTypeEnum.STRING;
        }
        return switch (controlType) {
            case "NUMBER" -> ValueTypeEnum.NUMERIC;
            case "BOOLEAN" -> ValueTypeEnum.BOOLEAN;
            default -> ValueTypeEnum.STRING;
        };
    }
}
