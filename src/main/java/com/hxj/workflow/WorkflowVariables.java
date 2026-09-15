package com.hxj.workflow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 流程变量契约——变量名与类型的单一真相，三处消费共用，杜绝各处硬编码后漂移：
 * <ul>
 *   <li>提交链路（{@code DocumentApplicationService}）：按模板字段声明把表单值写入流程变量；</li>
 *   <li>保存关口（{@code FlowConfigManagementService}）：条件转移引用的变量必须是该流程
 *       绑定模板中声明"参与流程条件"的字段（{@link com.hxj.entity.FormField#isProcessVariable()}），
 *       比较值必须与控件类型匹配——否则网关求值时流程永久卡死；</li>
 *   <li>BPMN 生成（{@code ConfigDrivenProcessDefinitionService}）：条件表达式按本契约生成字面量。</li>
 * </ul>
 *
 * <p><b>字段即变量</b>（钉钉同构）：可作条件判据的变量由<b>模板字段声明</b>决定，不再硬编码白名单——
 * 管理员在模板里勾选"参与流程条件"即获得一个新判据，加分支维度不需改代码。
 * 本类只保留两类系统变量（非表单字段）：审批人解析产物与保留键的兜底类型。
 */
public final class WorkflowVariables {

    private WorkflowVariables() {
    }

    /** 发起人回环节点的指派变量（申请人账号）。 */
    public static final String INITIATOR = "initiator";
    /** 直属主管节点取链首账号（主管链解析器产出）。 */
    public static final String MANAGER_ACCOUNT = "managerAccount";
    /** 逐级主管节点的串行审批链（自下而上）。 */
    public static final String MANAGER_CHAIN = "managerChain";
    /** 发起人自选节点的串行审批链（提交时指定）。 */
    public static final String APPROVER_CHAIN = "approverChain";

    /** 节点级动态指派变量名前缀：{@code ROLE + AssigneeScopeEnum.INITIATOR_DEPT} 节点按部门解析后的主办账号。 */
    private static final String SCOPED_ASSIGNEE_PREFIX = "deptScopedAssignee_";

    /** 变量值类型（保存关口按此校验比较值合法性）。 */
    public enum ValueTypeEnum { NUMERIC, BOOLEAN, STRING }

    /** SELECT / TEXT / TEXTAREA / DATE 等文本型控件的默认类型。 */
    private static final ValueTypeEnum TEXT_TYPE = ValueTypeEnum.STRING;

    /**
     * 保留键的兜底类型：模板未定义同名字段时（如流程尚未绑定模板）仍可用的条件变量。
     * 一旦模板定义了同名字段，以字段控件类型为准（见 {@code ConditionVariableCatalog}）。
     */
    private static final Map<String, ValueTypeEnum> RESERVED_KEY_TYPES = Map.of(
            "amount", ValueTypeEnum.NUMERIC,
            "involvesFunds", ValueTypeEnum.BOOLEAN,
            "requiresAdminReview", ValueTypeEnum.BOOLEAN,
            "businessMode", ValueTypeEnum.STRING,
            "needPostMaterial", ValueTypeEnum.BOOLEAN);

    /** 保留键兜底条件变量集合（无绑定模板时的可用判据，保证存量流程仍可配置）。 */
    public static final Set<String> RESERVED_KEY_VARIABLES = RESERVED_KEY_TYPES.keySet();

    /** 保留键的兜底类型；非保留键返回 null。 */
    public static ValueTypeEnum reservedKeyType(String variableName) {
        return RESERVED_KEY_TYPES.get(variableName);
    }

    /** 表单控件类型 → 条件变量值类型（NUMBER 数值、BOOLEAN 布尔、其余按字符串比较）。 */
    public static ValueTypeEnum valueTypeOfControlType(String controlType) {
        if (controlType == null) {
            return TEXT_TYPE;
        }
        return switch (controlType) {
            case "NUMBER" -> ValueTypeEnum.NUMERIC;
            case "BOOLEAN" -> ValueTypeEnum.BOOLEAN;
            default -> TEXT_TYPE;
        };
    }

    /** 节点级按部门指派变量名（同一流程多个此类节点各自独立，故按节点 ID 区分）。 */
    public static String scopedAssigneeVariable(Long nodeId) {
        return SCOPED_ASSIGNEE_PREFIX + nodeId;
    }

    /** 便捷构造：变量名 → 类型（保留插入顺序，供错误提示稳定输出）。 */
    public static Map<String, ValueTypeEnum> orderedMap(Map<String, ValueTypeEnum> source) {
        return new LinkedHashMap<>(source);
    }
}
