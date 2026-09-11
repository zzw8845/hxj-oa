package com.hxj.permission;

import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.SysDepartment;
import com.hxj.entity.SysUser;
import com.hxj.exception.BusinessException;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门管理服务：基于闭包表实现任意层级的部门树增删改查与移动。
 *
 * <p>闭包表维护约定（sys_department_closure，含自身路径 depth=0）：
 * <ul>
 *   <li>新建：写自身路径 + 复制父部门祖先链（距离 +1）；</li>
 *   <li>移动：先删除「后代在子树内、祖先在子树外」的路径使子树脱离原祖先链，
 *       再按新父部门祖先链 × 子树后代重建路径；</li>
 *   <li>删除：仅允许无下级、无员工的叶部门，删除时同步清理路径。</li>
 * </ul>
 * 移动前校验目标父部门不在被移动子树内，杜绝成环。
 */
@Service
public class DepartmentManagementService {

    private final SysDepartmentRepository departmentRepository;
    private final SysUserRepository userRepository;
    private final com.hxj.repository.SysRoleRepository roleRepository;

    public DepartmentManagementService(
            SysDepartmentRepository departmentRepository,
            SysUserRepository userRepository,
            com.hxj.repository.SysRoleRepository roleRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    /** 新建部门并初始化闭包路径。 */
    @Transactional
    public DepartmentViews.Department create(SaveDepartmentRequest request) {
        if (departmentRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCodeEnum.DEPARTMENT_NAME_EXISTS, "部门名称已存在");
        }
        SysDepartment parent = requireParent(request.parentId());
        SysDepartment department = new SysDepartment();
        department.setName(request.name());
        department.setParentId(parent == null ? null : parent.getId());
        department.setSortOrder(request.sortOrder());
        SysDepartment saved = departmentRepository.saveAndFlush(department);

        departmentRepository.insertSelfPath(saved.getId());
        if (parent != null) {
            departmentRepository.attachUnderParent(saved.getId(), parent.getId());
        }
        return toLeafNode(saved);
    }

    /** 编辑部门：改名、移动（parentId 为空表示移动为根部门）、调整排序，并同步员工展示快照。 */
    @Transactional
    public DepartmentViews.Department update(Long departmentId, SaveDepartmentRequest request) {
        SysDepartment department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.DEPARTMENT_NOT_FOUND, "部门不存在"));
        departmentRepository.findByName(request.name())
                .filter(other -> !other.getId().equals(departmentId))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCodeEnum.DEPARTMENT_NAME_EXISTS, "部门名称已存在");
                });

        SysDepartment newParent = requireParent(request.parentId());
        boolean parentChanged = !java.util.Objects.equals(
                department.getParentId(), newParent == null ? null : newParent.getId());
        if (parentChanged) {
            if (newParent != null) {
                ensureNotInSubtree(departmentId, newParent.getId());
                departmentRepository.detachSubtree(departmentId);
                departmentRepository.attachSubtreeUnderParent(departmentId, newParent.getId());
            } else {
                departmentRepository.detachSubtree(departmentId);
            }
        }

        boolean renamed = !department.getName().equals(request.name());
        department.setName(request.name());
        department.setParentId(newParent == null ? null : newParent.getId());
        department.setSortOrder(request.sortOrder());
        if (renamed) {
            syncDepartmentRename(department.getId(), request.name());
        }
        return toLeafNode(departmentRepository.save(department));
    }

    /** 删除部门：仅允许无下级、无员工、无岗位、且未被角色引用的叶部门。 */
    @Transactional
    public void delete(Long departmentId) {
        SysDepartment department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.DEPARTMENT_NOT_FOUND, "部门不存在"));
        if (departmentRepository.existsByParentId(departmentId)) {
            throw new BusinessException(ErrorCodeEnum.DEPARTMENT_HAS_CHILDREN, "存在下级部门，无法删除");
        }
        if (userRepository.existsByDepartmentId(departmentId)) {
            throw new BusinessException(ErrorCodeEnum.DEPARTMENT_HAS_EMPLOYEES, "部门下存在员工，无法删除");
        }
        if (roleRepository.existsByDepartmentId(departmentId)) {
            throw new BusinessException(ErrorCodeEnum.DEPARTMENT_HAS_ROLES, "部门被角色引用，无法删除");
        }
        // CUSTOM 数据范围的部门集合引用：外键为 CASCADE，不守卫会被静默删除，
        // 导致对应角色的可见部门范围悄悄缩水
        if (departmentRepository.countScopeReferences(departmentId) > 0) {
            throw new BusinessException(ErrorCodeEnum.DEPARTMENT_IN_SCOPE_USE, "部门被自定义数据范围引用，无法删除");
        }
        departmentRepository.deleteAllPathsOf(departmentId);
        departmentRepository.delete(department);
    }

    /** 查询完整部门树（按同级排序号升序，自底向上递归构建）。 */
    @Transactional(readOnly = true)
    public List<DepartmentViews.Department> tree() {
        List<SysDepartment> all = departmentRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<SysDepartment>> childrenByParent = new LinkedHashMap<>();
        List<SysDepartment> roots = new ArrayList<>();
        for (SysDepartment department : all) {
            if (department.getParentId() == null) {
                roots.add(department);
            } else {
                childrenByParent.computeIfAbsent(department.getParentId(), key -> new ArrayList<>())
                        .add(department);
            }
        }
        return roots.stream()
                .map(root -> buildNode(root, childrenByParent))
                .toList();
    }

    /** 递归构建树节点（子节点保持仓库排序：sort_order 升序、id 升序）。 */
    private DepartmentViews.Department buildNode(
            SysDepartment department, Map<Long, List<SysDepartment>> childrenByParent) {
        List<DepartmentViews.Department> children = childrenByParent
                .getOrDefault(department.getId(), List.of()).stream()
                .map(child -> buildNode(child, childrenByParent))
                .toList();
        return new DepartmentViews.Department(department.getId(), department.getName(),
                department.getParentId(), department.getSortOrder(), children);
    }

    /** 解析部门引用（员工创建/编辑使用）：必须存在。 */
    @Transactional(readOnly = true)
    public SysDepartment requireDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.DEPARTMENT_NOT_FOUND, "部门不存在"));
    }

    private SysDepartment requireParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return departmentRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.DEPARTMENT_NOT_FOUND, "上级部门不存在"));
    }

    /** 环检测：目标父部门不能是被移动部门自身或其子孙。 */
    private void ensureNotInSubtree(Long subtreeRootId, Long targetParentId) {
        if (subtreeRootId.equals(targetParentId)
                || departmentRepository.countPath(subtreeRootId, targetParentId) > 0) {
            throw new BusinessException(ErrorCodeEnum.DEPARTMENT_MOVE_CYCLE, "不能将部门移动到自身或其下级部门下");
        }
    }

    /** 部门改名后同步员工与角色的展示快照（历史单据快照不受影响）。 */
    private void syncDepartmentRename(Long departmentId, String newName) {
        List<SysUser> members = userRepository.findByDepartmentId(departmentId);
        for (SysUser member : members) {
            member.setDepartment(newName);
        }
        userRepository.saveAll(members);
        List<com.hxj.entity.SysRole> roles = roleRepository.findByDepartmentId(departmentId);
        for (com.hxj.entity.SysRole role : roles) {
            role.setDepartment(newName);
        }
        roleRepository.saveAll(roles);
    }

    private DepartmentViews.Department toLeafNode(SysDepartment department) {
        return new DepartmentViews.Department(department.getId(), department.getName(),
                department.getParentId(), department.getSortOrder(), List.of());
    }
}
