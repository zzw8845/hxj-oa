package com.hxj.workflow;

import java.util.Map;
import java.util.Set;

/**
 * 流程变量契约——变量名与类型的单一真相。
 *
 * <p>三处消费共用本契约，杜绝各处硬编码后漂移：
 * <ul>
 *   <li>提交链路（{@code DocumentApplicationService}）：按本契约把表单提升键写入流程变量；</li>
 *   <li>保存关口（{@code FlowConfigManagementService}）：条件转移引用的变量必须在本白名单内，
 *       比较值必须与 {@link #valueType} 声明的类型匹配——否则网关求值时流程永久卡死；</li>
 *   <li>BPMN 生成（{@code ConfigDrivenProcessDefinitionService}）：条件表达式按本契约生成字面量。</li>
 * </ul>
 */
public final class WorkflowVariables {

    private WorkflowVariables() {
    }

    /** 单据金额（数值型），条件分支最常用的变量。 */
    public static final String AMOUNT = "amount";
    /** 是否涉及资金（布尔型）。 */
    public static final String INVOLVES_FUNDS = "involvesFunds";
    /** 是否需行政复核（布尔型）。 */
    public static final String REQUIRES_ADMIN_REVIEW = "requiresAdminReview";
    /** 业务模式（字符串型）。 */
    public static final String BUSINESS_MODE = "businessMode";

    /** 发起人回环节点的指派变量（申请人账号）。 */
    public static final String INITIATOR = "initiator";
    /** 直属主管节点取链首账号（主管链解析器产出）。 */
    public static final String MANAGER_ACCOUNT = "managerAccount";
    /** 逐级主管节点的串行审批链（自下而上）。 */
    public static final String MANAGER_CHAIN = "managerChain";
    /** 发起人自选节点的串行审批链（提交时指定）。 */
    public static final String APPROVER_CHAIN = "approverChain";
    /** 核算分工解析的主办会计账号。 */
    public static final String DEPT_ACCOUNTANT = "deptAccountant";

    /** 条件转移可引用的变量白名单。 */
    public static final Set<String> CONDITION_VARIABLES =
            Set.of(AMOUNT, INVOLVES_FUNDS, REQUIRES_ADMIN_REVIEW, BUSINESS_MODE);

    /** 变量值类型（用于保存关口校验比较值合法性）。 */
    public enum ValueTypeEnum { NUMERIC, BOOLEAN, STRING }

    private static final Map<String, ValueTypeEnum> VALUE_TYPES = Map.of(
            AMOUNT, ValueTypeEnum.NUMERIC,
            INVOLVES_FUNDS, ValueTypeEnum.BOOLEAN,
            REQUIRES_ADMIN_REVIEW, ValueTypeEnum.BOOLEAN,
            BUSINESS_MODE, ValueTypeEnum.STRING);

    /** 变量的值类型；白名单外的变量返回 null（调用方应已在白名单校验中拒绝）。 */
    public static ValueTypeEnum valueType(String variableName) {
        return VALUE_TYPES.get(variableName);
    }
}
