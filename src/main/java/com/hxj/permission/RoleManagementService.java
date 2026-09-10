package com.hxj.permission;

import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysPermission;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.exception.BusinessException;
import com.hxj.repository.SysDataScopeRepository;
import com.hxj.repository.SysPermissionRepository;
import com.hxj.repository.SysRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleManagementService {

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final SysDataScopeRepository dataScopeRepository;
    private final com.hxj.repository.SysDepartmentRepository departmentRepository;
    private final com.hxj.repository.FlowNodeConfigRepository flowNodeConfigRepository;

    public RoleManagementService(
            SysRoleRepository roleRepository,
            SysPermissionRepository permissionRepository,
            SysDataScopeRepository dataScopeRepository,
            com.hxj.repository.SysDepartmentRepository departmentRepository,
            com.hxj.repository.FlowNodeConfigRepository flowNodeConfigRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.dataScopeRepository = dataScopeRepository;
        this.departmentRepository = departmentRepository;
        this.flowNodeConfigRepository = flowNodeConfigRepository;
    }

    @Transactional
    public RoleResponse create(SaveRoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCodeEnum.ROLE_EXISTS, "角色名称已存在");
        }
        SysRole role = new SysRole();
        apply(role, request);
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse update(Long roleId, SaveRoleRequest request) {
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.ROLE_NOT_FOUND, "角色不存在"));
        roleRepository.findByName(request.name())
                .filter(other -> !other.getId().equals(roleId))
                .ifPresent(other -> { throw new BusinessException(ErrorCodeEnum.ROLE_EXISTS, "角色名称已存在"); });
        String oldName = role.getName();
        boolean renamed = !oldName.equals(request.name());
        apply(role, request);
        if (renamed) {
            syncFlowNodeAssignee(oldName, request.name());
        }
        return toResponse(role);
    }

    /**
     * 角色改名后同步流程节点的审批角色字符串：组合串（"A/B"）按段精确替换，
     * 避免改名后流程节点绑定的旧角色名失效、审批任务无人可领。
     */
    private void syncFlowNodeAssignee(String oldName, String newName) {
        List<com.hxj.entity.FlowNodeConfig> nodes =
                flowNodeConfigRepository.findByAssigneeRoleContaining(oldName);
        for (com.hxj.entity.FlowNodeConfig node : nodes) {
            String updated = java.util.Arrays.stream(node.getAssigneeRole().split("[/、]"))
                    .map(segment -> segment.trim().equals(oldName) ? newName : segment.trim())
                    .collect(java.util.stream.Collectors.joining("/"));
            node.setAssigneeRole(updated);
        }
        flowNodeConfigRepository.saveAll(nodes);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * 删除角色：有员工引用或被流程节点（含组合角色串的某个段）引用时禁止删除。
     */
    @Transactional
    public void delete(Long roleId) {
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.ROLE_NOT_FOUND, "角色不存在"));
        if (!role.getMembers().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.ROLE_HAS_MEMBERS, "角色下存在员工，无法删除");
        }
        boolean flowUsed = flowNodeConfigRepository.findAllAssigneeRoles().stream()
                .anyMatch(assignee -> usesRole(assignee, role.getName()));
        if (flowUsed) {
            throw new BusinessException(ErrorCodeEnum.ROLE_IN_FLOW_USE, "角色被流程节点引用，无法删除");
        }
        roleRepository.delete(role);
    }

    /** 组合审批角色串（如 "会计主管&内控/执行总经理"）按 "/" 拆段后精确匹配。 */
    private boolean usesRole(String assigneeRole, String roleName) {
        for (String segment : assigneeRole.split("/")) {
            if (segment.trim().equals(roleName)) {
                return true;
            }
        }
        return false;
    }

    /** 权限点字典（只读，供角色编辑下拉使用；权限点与代码逻辑强耦合，不开放写）。 */
    @Transactional(readOnly = true)
    public List<PermissionViews.PermissionPoint> listPermissions() {
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(SysPermission::getCode))
                .map(permission -> new PermissionViews.PermissionPoint(
                        permission.getCode(), permission.getName(), permission.getDescription()))
                .toList();
    }

    /** 数据范围字典（只读，供角色编辑下拉使用；数据范围与代码逻辑强耦合，不开放写）。 */
    @Transactional(readOnly = true)
    public List<DataScopeViews.Scope> listDataScopes() {
        return dataScopeRepository.findAll().stream()
                .sorted(Comparator.comparing(SysDataScope::getCode))
                .map(scope -> new DataScopeViews.Scope(
                        scope.getCode(), scope.getName(), scope.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentRoleNodeResponse> departmentTree() {
        Map<String, List<SysRole>> grouped = roleRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        role -> role.getDepartment() == null ? "未分配" : role.getDepartment()));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DepartmentRoleNodeResponse(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(SysRole::getName))
                                .map(this::toTreeNode)
                                .toList()))
                .toList();
    }

    private void apply(SysRole role, SaveRoleRequest request) {
        SysDataScope dataScope = dataScopeRepository.findByCode(request.dataScope())
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.DATA_SCOPE_NOT_FOUND, "数据范围不存在"));
        Set<String> permissionCodes = new LinkedHashSet<>(request.permissions());
        List<SysPermission> permissions = permissionRepository.findByCodeIn(permissionCodes);
        if (permissionCodes.isEmpty() || permissions.size() != permissionCodes.size()) {
            throw new BusinessException(ErrorCodeEnum.PERMISSION_NOT_FOUND, "权限点不存在");
        }
        com.hxj.entity.SysDepartment department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.DEPARTMENT_NOT_FOUND, "归属部门不存在"));
        List<com.hxj.entity.SysDepartment> scopeDepartments =
                departmentRepository.findAllById(request.scopeDepartmentIds());
        if (scopeDepartments.size() != request.scopeDepartmentIds().size()) {
            throw new BusinessException(ErrorCodeEnum.DEPARTMENT_NOT_FOUND, "自定义数据范围部门不存在");
        }
        role.setName(request.name());
        role.setDepartmentId(department.getId());
        role.setDepartment(department.getName());
        role.setPost(request.post());
        role.setDataScope(dataScope);
        role.setScopeDepartments(new LinkedHashSet<>(scopeDepartments));
        role.setPermissions(new LinkedHashSet<>(permissions));
    }

    private RoleResponse toResponse(SysRole role) {
        return new RoleResponse(
                role.getId(), role.getName(), role.getDepartmentId(), role.getDepartment(), role.getPost(),
                role.getDataScope().getCode(), scopeDepartmentIds(role),
                permissionCodes(role), memberNames(role));
    }

    private List<Long> scopeDepartmentIds(SysRole role) {
        return role.getScopeDepartments().stream()
                .map(com.hxj.entity.SysDepartment::getId)
                .sorted()
                .toList();
    }

    private RoleTreeNodeResponse toTreeNode(SysRole role) {
        return new RoleTreeNodeResponse(
                role.getId(), role.getName(), role.getPost(), role.getDataScope().getCode(),
                permissionCodes(role), memberNames(role));
    }

    private List<String> permissionCodes(SysRole role) {
        return role.getPermissions().stream().map(SysPermission::getCode).sorted().toList();
    }

    private List<String> memberNames(SysRole role) {
        return role.getMembers().stream().map(SysUser::getName).sorted().toList();
    }
}
