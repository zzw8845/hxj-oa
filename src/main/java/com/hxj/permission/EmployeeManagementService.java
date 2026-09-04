package com.hxj.permission;

import com.hxj.common.ErrorCode;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.entity.UserStatus;
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

@Service
public class EmployeeManagementService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeManagementService(
            SysUserRepository userRepository,
            SysRoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        validateCreate(request);
        if (userRepository.existsByAccount(request.account())) {
            throw new BusinessException(ErrorCode.ACCOUNT_EXISTS, "登录账号已存在");
        }
        if (userRepository.existsByJobNo(request.jobNo())) {
            throw new BusinessException(ErrorCode.JOB_NO_EXISTS, "员工工号已存在");
        }
        SysUser user = new SysUser();
        user.setName(request.name());
        user.setJobNo(request.jobNo());
        user.setAccount(request.account());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDepartment(request.department());
        user.setPost(request.post());
        user.setStatus(UserStatus.ACTIVE);
        replaceRoles(user, request.roleIds());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public EmployeeResponse update(Long userId, UpdateEmployeeRequest request) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "员工不存在"));
        userRepository.findAll().stream()
                .filter(other -> !other.getId().equals(userId))
                .filter(other -> request.jobNo().equals(other.getJobNo()))
                .findAny()
                .ifPresent(other -> { throw new BusinessException(ErrorCode.JOB_NO_EXISTS, "员工工号已存在"); });
        user.setName(request.name());
        user.setJobNo(request.jobNo());
        user.setDepartment(request.department());
        user.setPost(request.post());
        user.setStatus(request.status());
        if (StringUtils.hasText(request.newPassword())) {
            user.setPassword(passwordEncoder.encode(request.newPassword()));
        }
        replaceRoles(user, request.roleIds());
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> list() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    private void validateCreate(CreateEmployeeRequest request) {
        if (!StringUtils.hasText(request.name()) || !StringUtils.hasText(request.account())
                || !StringUtils.hasText(request.password())) {
            throw new BusinessException(ErrorCode.EMPLOYEE_ACCOUNT_INCOMPLETE, "请完整填写员工、账号和密码信息");
        }
    }

    /** roleIds 来自请求 DTO 的不可变列表（紧凑构造器已保证非 null），可直接构造集合。 */
    private void replaceRoles(SysUser user, List<Long> roleIds) {
        Set<Long> uniqueIds = new LinkedHashSet<>(roleIds);
        List<SysRole> roles = roleRepository.findAllById(uniqueIds);
        if (uniqueIds.isEmpty() || roles.size() != uniqueIds.size()) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND, "分配角色不存在");
        }
        user.clearRoles();
        roles.forEach(user::addRole);
    }

    private EmployeeResponse toResponse(SysUser user) {
        return new EmployeeResponse(
                user.getId(), user.getName(), user.getJobNo(), user.getAccount(),
                user.getDepartment(), user.getPost(), user.getStatus(),
                user.getRoles().stream().map(SysRole::getName).sorted().toList());
    }
}
