package com.hxj.workflow;

import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.SysDepartment;
import com.hxj.entity.SysUser;
import com.hxj.enums.AssigneeScopeEnum;
import com.hxj.enums.AssigneeSubjectEnum;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.repository.SysUserServiceDeptRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批人统一解析器——六维正交模型的<b>唯一</b>求值入口（钉钉同构）。
 *
 * <p>替代历史上的多个专用解析器（主管链解析器、核算会计解析器……）：新增业务场景只组合
 * {@link AssigneeSubjectEnum} × {@link AssigneeScopeEnum} × 层级，不新增解析器、不新增枚举值。
 *
 * <p>解析规则：
 * <ul>
 *   <li>{@code MEMBER}：值内账号；</li>
 *   <li>{@code INITIATOR}：申请人本人；</li>
 *   <li>{@code INITIATOR_SELECT}：提交时申请人指定；</li>
 *   <li>{@code ROLE}：角色成员——范围 GLOBAL 取全部；INITIATOR_DEPT / FORM_DEPT 按部门过滤
 *       （显式服务分工 {@code sys_user_service_dept} 优先，其次成员所属部门）；</li>
 *   <li>{@code SUPERIOR}：沿业务汇报线（{@code sys_user.manager_id}）向上取第 N 级/连续 1..N 级；</li>
 *   <li>{@code DEPT_HEAD}：沿部门负责人树（{@code sys_department.leader_user_id}）自近及远取第 N 级/连续 1..N 级；</li>
 *   <li>{@code FORM_MEMBER}：表单人员控件的值。</li>
 * </ul>
 *
 * <p>本类只负责"找到人"，不负责"找不到人怎么办"——空策略由提交关口（{@code DocumentApplicationService}）
 * 按 {@code EmptyAssigneeStrategyEnum} 落地。
 */
@Component
public class AssigneeResolver {

    /** 层级/链长上限（钉钉最高 8 级，留余量防脏数据成环）。 */
    public static final int MAX_LEVEL = 10;

    /** Flowable 原生 skipExpression 开关变量名（空策略"自动通过"依赖引擎跳过能力）。 */
    public static final String SKIP_EXPRESSION_ENABLED_VARIABLE = "_FLOWABLE_SKIP_EXPRESSION_ENABLED";

    private final SysUserRepository userRepository;
    private final SysDepartmentRepository departmentRepository;
    private final SysUserServiceDeptRepository serviceDeptRepository;

    public AssigneeResolver(SysUserRepository userRepository,
                            SysDepartmentRepository departmentRepository,
                            SysUserServiceDeptRepository serviceDeptRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.serviceDeptRepository = serviceDeptRepository;
    }

    /** 解析上下文：一次提交的全部输入。 */
    public record Context(SysUser applicant,
                          Map<String, Object> formValues,
                          List<String> selectedAccounts) {

        public Context {
            formValues = formValues == null ? Map.of() : formValues;
            selectedAccounts = selectedAccounts == null ? List.of() : List.copyOf(selectedAccounts);
        }
    }

    /** 解析节点的候选审批人账号（按优先级有序）；解析不到返回空列表。 */
    public List<String> resolve(FlowNodeConfig node, Context context) {
        AssigneeSubjectEnum subject = node.getAssigneeSubject();
        if (subject == null) {
            return List.of();
        }
        return switch (subject) {
            case MEMBER -> accountsOf(node.getAssigneeValue());
            case INITIATOR -> context.applicant() == null || context.applicant().getAccount() == null
                    ? List.of() : List.of(context.applicant().getAccount());
            case INITIATOR_SELECT -> context.selectedAccounts();
            case ROLE -> resolveRole(node, context);
            case SUPERIOR -> levels(superiorChain(context.applicant()), node);
            case DEPT_HEAD -> levels(departmentHeadChain(departmentStart(node, context)), node);
            case FORM_MEMBER -> accountsOf(formText(node.getAssigneeValue(), context));
        };
    }

    /** 解析主管链（连续多级节点/兼容旧链式消费）：沿业务汇报线向上，自近及远。 */
    public List<String> superiorChain(SysUser applicant) {
        List<String> chain = new ArrayList<>();
        if (applicant == null) {
            return chain;
        }
        Set<Long> visited = new LinkedHashSet<>();
        visited.add(applicant.getId());
        Long cursor = applicant.getManagerId();
        while (cursor != null && chain.size() < MAX_LEVEL && visited.add(cursor)) {
            SysUser supervisor = userRepository.findById(cursor).orElse(null);
            if (supervisor == null) {
                break;
            }
            chain.add(supervisor.getAccount());
            cursor = supervisor.getManagerId();
        }
        return List.copyOf(chain);
    }

    /**
     * 部门负责人链：从给定部门起，沿部门树向上收集设了负责人的部门（自近及远）。
     * 逐级向上天然兜底——顶到根部门必有负责人即无断链。
     */
    public List<String> departmentHeadChain(Long departmentId) {
        List<String> chain = new ArrayList<>();
        if (departmentId == null) {
            return chain;
        }
        Set<Long> visitedLeaders = new LinkedHashSet<>();
        Long cursor = departmentId;
        int guard = 0;
        while (cursor != null && guard++ < MAX_LEVEL * 2) {
            SysDepartment department = departmentRepository.findById(cursor).orElse(null);
            if (department == null) {
                break;
            }
            Long leaderId = department.getLeaderUserId();
            if (leaderId != null && visitedLeaders.add(leaderId)) {
                userRepository.findById(leaderId)
                        .ifPresent(leader -> chain.add(leader.getAccount()));
            }
            cursor = department.getParentId();
        }
        return List.copyOf(chain);
    }

    /** 角色成员解析：GLOBAL 取全部；按部门时显式服务分工优先，回落成员所属部门。 */
    private List<String> resolveRole(FlowNodeConfig node, Context context) {
        List<Long> roleIds = idsOf(node.getAssigneeValue());
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Long departmentId = scopeDepartmentId(node, context);
        if (departmentId == null) {
            return userRepository.findAccountsByRoleIds(roleIds);
        }
        List<String> served = serviceDeptRepository.findServedAccountsByRoleIds(departmentId, roleIds);
        if (!served.isEmpty()) {
            return served;
        }
        return userRepository.findAccountsByRoleIdsAndDepartmentId(roleIds, departmentId);
    }

    /**
     * 部门主管链的起点部门：{@code FORM_DEPT} 取表单部门控件选中的部门，
     * 其余情况（含默认 GLOBAL）取发起人所在部门——部门主管天然从申请人部门向上。
     */
    private Long departmentStart(FlowNodeConfig node, Context context) {
        if (node.getAssigneeScope() == AssigneeScopeEnum.FORM_DEPT) {
            return longOf(formText(node.getAssigneeScopeValue(), context));
        }
        return context.applicant() == null ? null : context.applicant().getDepartmentId();
    }

    /** 角色范围维度 → 部门 ID（GLOBAL 返回 null 表示不限部门）。 */
    private Long scopeDepartmentId(FlowNodeConfig node, Context context) {
        AssigneeScopeEnum scope = node.getAssigneeScope() == null
                ? AssigneeScopeEnum.GLOBAL : node.getAssigneeScope();
        return switch (scope) {
            case GLOBAL -> null;
            case INITIATOR_DEPT -> context.applicant() == null
                    ? null : context.applicant().getDepartmentId();
            case FORM_DEPT -> longOf(formText(node.getAssigneeScopeValue(), context));
        };
    }

    /** 层级维度：连续多级取前 N 级（逐级串行消费）；单级取第 N 级那一个。 */
    private List<String> levels(List<String> chain, FlowNodeConfig node) {
        int level = node.getAssigneeLevel() == null ? 1
                : Math.max(1, Math.min(node.getAssigneeLevel(), MAX_LEVEL));
        if (chain.isEmpty()) {
            return List.of();
        }
        if (node.isAssigneeChain()) {
            return chain.subList(0, Math.min(level, chain.size()));
        }
        return chain.size() >= level ? List.of(chain.get(level - 1)) : List.of();
    }

    /** 逗号分隔的账号列表。 */
    private static List<String> accountsOf(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> accounts = new ArrayList<>();
        for (String piece : value.split(",")) {
            if (!piece.isBlank()) {
                accounts.add(piece.trim());
            }
        }
        return accounts;
    }

    /** 逗号分隔的数字 ID 列表。 */
    private static List<Long> idsOf(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String piece : value.split(",")) {
            String trimmed = piece.trim();
            if (trimmed.matches("\\d+")) {
                ids.add(Long.valueOf(trimmed));
            }
        }
        return ids;
    }

    /** 表单字段值 → 文本（支持字符串/数字/集合）。 */
    private static String formText(String fieldKey, Context context) {
        if (fieldKey == null || fieldKey.isBlank()) {
            return null;
        }
        Object value = context.formValues().get(fieldKey);
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(null);
        }
        return String.valueOf(value);
    }

    private static Long longOf(String text) {
        return text != null && text.trim().matches("\\d+") ? Long.valueOf(text.trim()) : null;
    }
}
