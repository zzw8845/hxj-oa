package com.hxj.permission;

import com.hxj.common.ErrorCode;
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

    public RoleManagementService(
            SysRoleRepository roleRepository,
            SysPermissionRepository permissionRepository,
            SysDataScopeRepository dataScopeRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.dataScopeRepository = dataScopeRepository;
    }

    @Transactional
    public RoleResponse create(SaveRoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.ROLE_EXISTS, "角色名称已存在");
        }
        SysRole role = new SysRole();
        apply(role, request);
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse update(Long roleId, SaveRoleRequest request) {
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND, "角色不存在"));
        roleRepository.findByName(request.name())
                .filter(other -> !other.getId().equals(roleId))
                .ifPresent(other -> { throw new BusinessException(ErrorCode.ROLE_EXISTS, "角色名称已存在"); });
        apply(role, request);
        return toResponse(role);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentRoleNode> departmentTree() {
        Map<String, List<SysRole>> grouped = roleRepository.findAll().stream()
                .collect(Collectors.groupingBy(SysRole::getDepartment));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DepartmentRoleNode(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(SysRole::getName))
                                .map(this::toTreeNode)
                                .toList()))
                .toList();
    }

    private void apply(SysRole role, SaveRoleRequest request) {
        SysDataScope dataScope = dataScopeRepository.findById(request.dataScopeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_SCOPE_NOT_FOUND, "数据范围不存在"));
        Set<Long> permissionIds = new LinkedHashSet<>(request.permissionIds());
        List<SysPermission> permissions = permissionRepository.findAllById(permissionIds);
        if (permissionIds.isEmpty() || permissions.size() != permissionIds.size()) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND, "权限点不存在");
        }
        role.setName(request.name());
        role.setDepartment(request.department());
        role.setPost(request.post());
        role.setDataScope(dataScope);
        role.setPermissions(new LinkedHashSet<>(permissions));
    }

    private RoleResponse toResponse(SysRole role) {
        return new RoleResponse(
                role.getId(), role.getName(), role.getDepartment(), role.getPost(),
                role.getDataScope().getCode(), permissionCodes(role), memberNames(role));
    }

    private RoleTreeNode toTreeNode(SysRole role) {
        return new RoleTreeNode(
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
