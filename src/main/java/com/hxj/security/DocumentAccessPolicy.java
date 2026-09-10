package com.hxj.security;

import com.hxj.entity.OaDocument;
import com.hxj.entity.SysDepartment;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysRoleRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将当前用户的数据范围转换为单据查询约束，未知范围默认拒绝（仅剩本人单据基线）。
 *
 * <p>数据范围模型为 5 种通用类型（V12 起收敛，替换旧 19 个业务耦合 code）：
 * <ul>
 *   <li>{@code ALL}：全部单据；</li>
 *   <li>{@code OWN}：仅本人单据；</li>
 *   <li>{@code DEPT}：本部门（与用户部门名称精确匹配）；</li>
 *   <li>{@code DEPT_AND_CHILD}：本部门及以下（经部门闭包表求子树）；</li>
 *   <li>{@code CUSTOM}：自定义部门集合（角色配置，经 sys_role_scope_department）。</li>
 * </ul>
 * 「本人提交的单据」是所有用户的基线权利，与数据范围类型取并集。
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

    public DocumentAccessPolicy(
            SysDepartmentRepository departmentRepository,
            SysRoleRepository roleRepository) {
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
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
        if (scopes.contains(DEPT)) {
            result = or(result, departmentNameIn(Set.of(currentUser.department())));
        }
        if (scopes.contains(DEPT_AND_CHILD)) {
            result = or(result, departmentNameIn(subtreeDepartmentNames(currentUser.department())));
        }
        if (scopes.contains(CUSTOM)) {
            result = or(result, departmentNameIn(customDepartmentNames(currentUser.roles())));
        }
        return result;
    }

    /** 本部门及以下的部门名称集合（闭包表求子树；部门字典缺失时退化为仅本部门）。 */
    private Set<String> subtreeDepartmentNames(String departmentName) {
        if (departmentName == null) {
            return Set.of();
        }
        return departmentRepository.findByName(departmentName)
                .map(department -> {
                    LinkedHashSet<String> names = new LinkedHashSet<>();
                    for (Long id : departmentRepository.findSubtreeIds(department.getId())) {
                        departmentRepository.findById(id).map(SysDepartment::getName).ifPresent(names::add);
                    }
                    return names;
                })
                .orElseGet(() -> new LinkedHashSet<>(Set.of(departmentName)));
    }

    /** 角色配置的自定义部门名称集合（仅统计数据范围为 CUSTOM 的角色）。 */
    private Set<String> customDepartmentNames(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return Set.of();
        }
        return roleRepository.findByNameIn(new LinkedHashSet<>(roleNames)).stream()
                .filter(role -> role.getDataScope() != null && CUSTOM.equals(role.getDataScope().getCode()))
                .flatMap(role -> role.getScopeDepartments().stream())
                .map(SysDepartment::getName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Specification<OaDocument> applicant(Long userId) {
        return (root, query, builder) -> builder.equal(root.get("applicant").get("id"), userId);
    }

    private Specification<OaDocument> departmentNameIn(Set<String> departmentNames) {
        return departmentNames.isEmpty()
                ? denyAll()
                : (root, query, builder) -> root.get("department").in(departmentNames);
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
