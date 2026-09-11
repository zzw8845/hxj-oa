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
    private final EmployeeOffboardingService offboardingService;

    public EmployeeManagementService(
            SysUserRepository userRepository,
            SysRoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            DepartmentManagementService departmentService,
            PostManagementService postService,
            EmployeeOffboardingService offboardingService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.departmentService = departmentService;
        this.postService = postService;
        this.offboardingService = offboardingService;
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
        applyManager(user, request.managerAccount());
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
        applyManager(user, request.managerAccount());
        // 钉钉式离职交接：转为离职前必须清空在途待办并安置直属下属（转交或退回），
        // 避免审批任务悬空、汇报线指向已离职者
        if (request.status() == UserStatusEnum.RESIGNED
                && user.getStatus() == UserStatusEnum.ACTIVE) {
            int pending = offboardingService.pendingCount(userId);
            int subordinates = userRepository.findByManagerId(userId).size();
            if (pending > 0 || subordinates > 0) {
                throw new BusinessException(ErrorCodeEnum.EMPLOYEE_PENDING_TASKS,
                        "该员工还有 " + pending + " 笔在途待办、" + subordinates + " 名直属下属，请先完成转交或退回");
            }
        }
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

    /** 写入字典外键与名称快照。 */
    private void applyDictionary(SysUser user, SysDepartment department, SysPost post) {
        user.setDepartmentId(department.getId());
        user.setDepartment(department.getName());
        user.setPostId(post.getId());
        user.setPost(post.getName());
    }

    /** 写入直属主管（汇报线）：主管必须存在且不能是本人；留空表示未设置。 */
    private void applyManager(SysUser user, String managerAccount) {
        if (!StringUtils.hasText(managerAccount)) {
            user.setManagerId(null);
            return;
        }
        if (managerAccount.equals(user.getAccount())) {
            throw new BusinessException(ErrorCodeEnum.MANAGER_SELF_REFERENCE, "直属主管不能是自己");
        }
        SysUser manager = userRepository.findByAccount(managerAccount)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.MANAGER_NOT_FOUND, "直属主管不存在"));
        user.setManagerId(manager.getId());
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
        SysUser manager = user.getManagerId() == null
                ? null : userRepository.findById(user.getManagerId()).orElse(null);
        // 关联部门 = 本人部门 ∪ 各角色归属部门（组织覆盖面，业务语义由服务端统一推导）
        java.util.LinkedHashSet<String> related = new java.util.LinkedHashSet<>();
        if (user.getDepartment() != null) {
            related.add(user.getDepartment());
        }
        user.getRoles().forEach(r -> {
            if (r.getDepartment() != null) {
                related.add(r.getDepartment());
            }
        });
        return new EmployeeResponse(
                user.getId(), user.getName(), user.getJobNo(), user.getAccount(),
                user.getDepartmentId(), user.getDepartment(),
                user.getPostId(), user.getPost(),
                user.getManagerId(), manager == null ? null : manager.getAccount(),
                manager == null ? null : manager.getName(),
                user.getStatus(),
                user.getRoles().stream().map(SysRole::getName).sorted().toList(),
                List.copyOf(related));
    }
}
