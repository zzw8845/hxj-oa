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
    private final com.hxj.repository.CcRecordRepository ccRecordRepository;

    public RoleManagementService(
            SysRoleRepository roleRepository,
            SysPermissionRepository permissionRepository,
            SysDataScopeRepository dataScopeRepository,
            com.hxj.repository.SysDepartmentRepository departmentRepository,
            com.hxj.repository.FlowNodeConfigRepository flowNodeConfigRepository,
            com.hxj.repository.CcRecordRepository ccRecordRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.dataScopeRepository = dataScopeRepository;
        this.departmentRepository = departmentRepository;
        this.flowNodeConfigRepository = flowNodeConfigRepository;
        this.ccRecordRepository = ccRecordRepository;
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
        // 抄送记录历史：历史单据的抄送行直接引用角色 ID（外键无级联），不守卫会 500
        if (ccRecordRepository.existsByTargetRoleId(roleId)) {
            throw new BusinessException(ErrorCodeEnum.ROLE_IN_CC_USE, "角色被抄送记录引用，无法删除");
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

    /**
     * 部门角色架构：真树形结构——部门取自闭包表字典（支持任意层级），
     * 角色按归属部门（departmentId）挂载到对应节点；未归属部门的角色归入"未分配"虚拟节点。
     */
    @Transactional(readOnly = true)
    public List<DepartmentRoleNodeResponse> departmentTree() {
        List<com.hxj.entity.SysDepartment> all = departmentRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<SysRole>> rolesByDept = roleRepository.findAll().stream()
                .filter(role -> role.getDepartmentId() != null)
                .collect(Collectors.groupingBy(SysRole::getDepartmentId));
        Map<Long, List<com.hxj.entity.SysDepartment>> childrenByParent = new java.util.LinkedHashMap<>();
        List<com.hxj.entity.SysDepartment> roots = new java.util.ArrayList<>();
        for (com.hxj.entity.SysDepartment dept : all) {
            if (dept.getParentId() == null) {
                roots.add(dept);
            } else {
                childrenByParent.computeIfAbsent(dept.getParentId(), key -> new java.util.ArrayList<>()).add(dept);
            }
        }
        List<DepartmentRoleNodeResponse> tree = new java.util.ArrayList<>(roots.stream()
                .map(root -> buildDepartmentNode(root, childrenByParent, rolesByDept))
                .toList());
        List<SysRole> unassigned = roleRepository.findAll().stream()
                .filter(role -> role.getDepartmentId() == null)
                .sorted(Comparator.comparing(SysRole::getName))
                .toList();
        if (!unassigned.isEmpty()) {
            tree.add(new DepartmentRoleNodeResponse(null, "未分配", null, null,
                    unassigned.stream().map(this::toTreeNode).toList(), List.of()));
        }
        return tree;
    }

    /** 递归构建部门节点：角色列表按名称排序，子节点保持仓库排序（sort_order 升序、id 升序）。 */
    private DepartmentRoleNodeResponse buildDepartmentNode(com.hxj.entity.SysDepartment dept,
            Map<Long, List<com.hxj.entity.SysDepartment>> childrenByParent,
            Map<Long, List<SysRole>> rolesByDept) {
        List<DepartmentRoleNodeResponse> children = childrenByParent
                .getOrDefault(dept.getId(), List.of()).stream()
                .map(child -> buildDepartmentNode(child, childrenByParent, rolesByDept))
                .toList();
        List<RoleTreeNodeResponse> roles = rolesByDept.getOrDefault(dept.getId(), List.of()).stream()
                .sorted(Comparator.comparing(SysRole::getName))
                .map(this::toTreeNode)
                .toList();
        return new DepartmentRoleNodeResponse(dept.getId(), dept.getName(), dept.getParentId(),
                dept.getSortOrder(), roles, children);
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
