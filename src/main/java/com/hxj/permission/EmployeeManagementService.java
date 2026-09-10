package com.hxj.permission;

import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.SysDepartment;
import com.hxj.entity.SysPost;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.enums.UserStatusEnum;
import com.hxj.exception.BusinessException;
import com.hxj.repository.SysRoleRepository;
import com.hxj.repository.SysUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 员工管理服务：员工账号 CRUD、字典引用解析（部门/岗位）与密码重置。
 *
 * <p>部门/岗位以字典外键传入，服务解析后同时写入外键与名称快照
 * （快照用于单据快照展示与数据范围匹配，改名时由字典服务同步）。
 */
@Service
public class EmployeeManagementService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final DepartmentManagementService departmentService;
    private final PostManagementService postService;

    public EmployeeManagementService(
            SysUserRepository userRepository,
            SysRoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            DepartmentManagementService departmentService,
            PostManagementService postService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.departmentService = departmentService;
        this.postService = postService;
    }

    /** 创建员工账号：解析部门/岗位字典引用，写入外键与快照。 */
    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        validateCreate(request);
        if (userRepository.existsByAccount(request.account())) {
            throw new BusinessException(ErrorCodeEnum.ACCOUNT_EXISTS, "登录账号已存在");
        }
        if (userRepository.existsByJobNo(request.jobNo())) {
            throw new BusinessException(ErrorCodeEnum.JOB_NO_EXISTS, "员工工号已存在");
        }
        SysDepartment department = departmentService.requireDepartment(request.departmentId());
        SysPost post = postService.requirePost(request.postId());
        SysUser user = new SysUser();
        user.setName(request.name());
        user.setJobNo(request.jobNo());
        user.setAccount(request.account());
        user.setPassword(passwordEncoder.encode(request.password()));
        applyDictionary(user, department, post);
        user.setStatus(UserStatusEnum.ACTIVE);
        replaceRoles(user, request.roles());
        return toResponse(userRepository.save(user));
    }

    /** 编辑员工：支持改名/调部门/调岗/在职状态变更/修改密码/调整角色。 */
    @Transactional
    public EmployeeResponse update(Long userId, UpdateEmployeeRequest request) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "员工不存在"));
        userRepository.findAll().stream()
                .filter(other -> !other.getId().equals(userId))
                .filter(other -> request.jobNo().equals(other.getJobNo()))
                .findAny()
                .ifPresent(other -> { throw new BusinessException(ErrorCodeEnum.JOB_NO_EXISTS, "员工工号已存在"); });
        SysDepartment department = departmentService.requireDepartment(request.departmentId());
        SysPost post = postService.requirePost(request.postId());
        user.setName(request.name());
        user.setJobNo(request.jobNo());
        applyDictionary(user, department, post);
        user.setStatus(request.status());
        if (StringUtils.hasText(request.newPassword())) {
            user.setPassword(passwordEncoder.encode(request.newPassword()));
        }
        replaceRoles(user, request.roles());
        return toResponse(user);
    }

    /** 管理员重置员工密码。 */
    @Transactional
    public EmployeeResponse resetPassword(Long userId, ResetPasswordRequest request) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "员工不存在"));
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        return toResponse(user);
    }

    /** 员工列表：支持按部门/岗位/在职状态筛选（参数为空表示不过滤，员工量级为千人内，内存过滤即可）。 */
    @Transactional(readOnly = true)
    public List<EmployeeResponse> list(Long departmentId, Long postId, UserStatusEnum status) {
        return userRepository.findAll().stream()
                .filter(user -> departmentId == null || departmentId.equals(user.getDepartmentId()))
                .filter(user -> postId == null || postId.equals(user.getPostId()))
                .filter(user -> status == null || status == user.getStatus())
                .map(this::toResponse)
                .toList();
    }

    private void validateCreate(CreateEmployeeRequest request) {
        if (!StringUtils.hasText(request.name()) || !StringUtils.hasText(request.account())
                || !StringUtils.hasText(request.password())) {
            throw new BusinessException(ErrorCodeEnum.EMPLOYEE_ACCOUNT_INCOMPLETE, "请完整填写员工、账号和密码信息");
        }
    }

    /** 写入字典外键与名称快照；岗位必须归属于员工所在部门（或为通用岗位）。 */
    private void applyDictionary(SysUser user, SysDepartment department, SysPost post) {
        if (post.getDepartmentId() != null && !post.getDepartmentId().equals(department.getId())) {
            throw new BusinessException(ErrorCodeEnum.POST_DEPARTMENT_MISMATCH, "岗位不属于该员工所在部门");
        }
        user.setDepartmentId(department.getId());
        user.setDepartment(department.getName());
        user.setPostId(post.getId());
        user.setPost(post.getName());
    }

    /** 角色以名称传递（与 EmployeeResponse.roles 同源），此处解析为实体后按外键关联。 */
    private void replaceRoles(SysUser user, List<String> roleNames) {
        Set<String> uniqueNames = new LinkedHashSet<>(roleNames);
        List<SysRole> roles = roleRepository.findByNameIn(uniqueNames);
        if (uniqueNames.isEmpty() || roles.size() != uniqueNames.size()) {
            throw new BusinessException(ErrorCodeEnum.ROLE_NOT_FOUND, "分配角色不存在");
        }
        user.clearRoles();
        roles.forEach(user::addRole);
    }

    private EmployeeResponse toResponse(SysUser user) {
        return new EmployeeResponse(
                user.getId(), user.getName(), user.getJobNo(), user.getAccount(),
                user.getDepartmentId(), user.getDepartment(),
                user.getPostId(), user.getPost(),
                user.getStatus(),
                user.getRoles().stream().map(SysRole::getName).sorted().toList());
    }
}
