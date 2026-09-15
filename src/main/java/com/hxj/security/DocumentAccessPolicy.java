package com.hxj.security;

import com.hxj.entity.OaDocument;
import com.hxj.entity.SysDepartment;
import com.hxj.repository.ApprovalRecordRepository;
import com.hxj.repository.CcRecordRepository;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysRoleRepository;
import com.hxj.workflow.WorkflowPort;
import org.flowable.task.api.Task;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将当前用户的数据范围转换为单据查询约束，未知范围默认拒绝（仅剩本人单据基线）。
 *
 * <p>数据范围模型为 5 种通用类型（V12 起收敛，替换旧 19 个业务耦合 code）；
 * 部门匹配一律按<b>部门 ID</b>（单据挂 department_id 外键），与部门改名解耦：
 * <ul>
 *   <li>{@code ALL}：全部单据；</li>
 *   <li>{@code OWN}：仅本人单据；</li>
 *   <li>{@code DEPT}：本部门（与用户主部门 ID 精确匹配）；</li>
 *   <li>{@code DEPT_AND_CHILD}：本部门及以下（经部门闭包表求子树 ID）；</li>
 *   <li>{@code CUSTOM}：自定义部门集合（角色配置，经 sys_role_scope_department）。</li>
 * </ul>
 * 「本人提交的单据」是所有用户的基线权利，与数据范围类型取并集。
 *
 * <p>单据级访问（详情/附件/凭证上传）使用 {@link #accessibleTo}：在数据范围之上
 * 并入「流程参与关系」——当前任务持有人、已审批留痕人、被抄送人。审批路由把任务
 * 派给谁与他的数据范围宽窄无关，参与人必须能看见并操作自己名下的单据，
 * 否则出现"收得到待办、打不开单据、传不了凭证"的死锁。
 */
@Component
public class DocumentAccessPolicy {

    public static final String ALL = "ALL";
    public static final String OWN = "OWN";
    public static final String DEPT = "DEPT";
    public static final String DEPT_AND_CHILD = "DEPT_AND_CHILD";
    public static final String CUSTOM = "CUSTOM";

    private final SysDepartmentRepository departmentRepository;
    private final SysRoleRepository roleRepository;
    private final ApprovalRecordRepository approvalRecordRepository;
    private final CcRecordRepository ccRecordRepository;
    private final WorkflowPort workflowPort;

    public DocumentAccessPolicy(
            SysDepartmentRepository departmentRepository,
            SysRoleRepository roleRepository,
            ApprovalRecordRepository approvalRecordRepository,
            CcRecordRepository ccRecordRepository,
            WorkflowPort workflowPort) {
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.approvalRecordRepository = approvalRecordRepository;
        this.ccRecordRepository = ccRecordRepository;
        this.workflowPort = workflowPort;
    }

    public Specification<OaDocument> visibleTo(AuthenticatedUserResponse currentUser) {
        if (currentUser == null) {
            return denyAll();
        }
        List<String> scopes = currentUser.dataScopes();
        if (scopes.contains(ALL)) {
            return Specification.where(null);
        }

        // 基线：本人提交的单据始终可见
        Specification<OaDocument> result = applicant(currentUser.userId());
        if (scopes.contains(DEPT) && currentUser.departmentId() != null) {
            result = or(result, departmentIdIn(Set.of(currentUser.departmentId())));
        }
        if (scopes.contains(DEPT_AND_CHILD) && currentUser.departmentId() != null) {
            result = or(result, departmentIdIn(subtreeDepartmentIds(currentUser.departmentId())));
        }
        if (scopes.contains(CUSTOM)) {
            result = or(result, departmentIdIn(customDepartmentIds(currentUser.roles())));
        }
        return result;
    }

    /**
     * 单据级统一访问语义：数据范围 ∪ 流程参与关系。
     *
     * <p>列表与单查共用本方法，避免"范围口径"与"参与人口径"在多个服务各自实现后漂移。
     * 参与关系三类来源：当前持有任务（处理人或候选组）、历史审批留痕、被抄送记录。
     */
    public Specification<OaDocument> accessibleTo(AuthenticatedUserResponse currentUser) {
        if (currentUser != null && currentUser.dataScopes() != null
                && currentUser.dataScopes().contains(ALL)) {
            // 全量范围已覆盖一切，无需再并入参与关系（省一次 Flowable 任务查询）
            return Specification.where(null);
        }
        return visibleTo(currentUser).or(participation(currentUser));
    }

    /** 流程参与关系：按单据 ID（审批留痕/抄送）与流程实例 ID（在办任务）并入可见集。 */
    private Specification<OaDocument> participation(AuthenticatedUserResponse currentUser) {
        if (currentUser == null || currentUser.userId() == null) {
            return denyAll();
        }
        Set<Long> participatedIds = new LinkedHashSet<>();
        participatedIds.addAll(ccRecordRepository.findDocumentIdsByTargetUserId(currentUser.userId()));
        participatedIds.addAll(approvalRecordRepository.findDocumentIdsByApproverId(currentUser.userId()));
        Specification<OaDocument> spec = documentIdIn(participatedIds);
        Set<String> activeInstanceIds = workflowPort
                .pendingTasksForUser(currentUser.account(), currentUser.roleIds()).stream()
                .map(Task::getProcessInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!activeInstanceIds.isEmpty()) {
            spec = or(spec, (root, query, builder) -> root.get("processInstanceId").in(activeInstanceIds));
        }
        return spec;
    }

    private Specification<OaDocument> documentIdIn(Set<Long> ids) {
        return ids.isEmpty()
                ? denyAll()
                : (root, query, builder) -> root.get("id").in(ids);
    }

    /** 本部门及以下的部门 ID 集合（闭包表求子树）。 */
    private Set<Long> subtreeDepartmentIds(Long departmentId) {
        return new LinkedHashSet<>(departmentRepository.findSubtreeIds(departmentId));
    }

    /** 角色配置的自定义部门 ID 集合（仅统计数据范围为 CUSTOM 的角色）。 */
    private Set<Long> customDepartmentIds(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return Set.of();
        }
        return roleRepository.findByNameIn(new LinkedHashSet<>(roleNames)).stream()
                .filter(role -> role.getDataScope() != null && CUSTOM.equals(role.getDataScope().getCode()))
                .flatMap(role -> role.getScopeDepartments().stream())
                .map(SysDepartment::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Specification<OaDocument> applicant(Long userId) {
        return (root, query, builder) -> builder.equal(root.get("applicant").get("id"), userId);
    }

    private Specification<OaDocument> departmentIdIn(Set<Long> departmentIds) {
        return departmentIds.isEmpty()
                ? denyAll()
                : (root, query, builder) -> root.get("departmentId").in(departmentIds);
    }

    private Specification<OaDocument> denyAll() {
        return (root, query, builder) -> builder.disjunction();
    }

    private Specification<OaDocument> or(
            Specification<OaDocument> left,
            Specification<OaDocument> right) {
        return left == null ? right : left.or(right);
    }
}
