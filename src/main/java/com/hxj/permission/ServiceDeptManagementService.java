package com.hxj.permission;

import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.SysDepartment;
import com.hxj.entity.SysUser;
import com.hxj.entity.SysUserServiceDept;
import com.hxj.exception.BusinessException;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.repository.SysUserServiceDeptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 职能服务分工管理：维护成员服务哪些部门的单据（核算会计按部门分工的配置入口）。 */
@Service
public class ServiceDeptManagementService {

    private final SysUserRepository userRepository;
    private final SysDepartmentRepository departmentRepository;
    private final SysUserServiceDeptRepository serviceDeptRepository;

    public ServiceDeptManagementService(SysUserRepository userRepository,
                                        SysDepartmentRepository departmentRepository,
                                        SysUserServiceDeptRepository serviceDeptRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.serviceDeptRepository = serviceDeptRepository;
    }

    @Transactional(readOnly = true)
    public List<ServiceDeptView> list(Long employeeId) {
        return serviceDeptRepository.findByUserIdOrderByIdAsc(employeeId).stream()
                .map(item -> departmentRepository.findById(item.getDepartmentId())
                        .map(dept -> new ServiceDeptView(dept.getId(), dept.getName()))
                        .orElse(new ServiceDeptView(item.getDepartmentId(), "")))
                .toList();
    }

    @Transactional
    public List<ServiceDeptView> update(Long employeeId, List<Long> departmentIds) {
        SysUser user = userRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "用户不存在"));
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        if (departmentIds != null) {
            uniqueIds.addAll(departmentIds);
        }
        List<Long> ids = new ArrayList<>(uniqueIds);
        List<SysDepartment> departments = departmentRepository.findAllById(ids);
        if (departments.size() != ids.size()) {
            throw new BusinessException(ErrorCodeEnum.DEPARTMENT_NOT_FOUND, "部门不存在");
        }
        serviceDeptRepository.deleteByUserId(employeeId);
        departments.forEach(dept ->
                serviceDeptRepository.save(new SysUserServiceDept(user.getId(), dept.getId())));
        return list(employeeId);
    }

    /** 按部门列出全部服务成员账号（核算分工一览，供流程排障与配置核对）。 */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> accountsByDepartment(List<Long> departmentIds) {
        return departmentIds.stream().collect(Collectors.toMap(
                Function.identity(),
                deptId -> serviceDeptRepository.findServingAccountantAccounts(
                        deptId, com.hxj.workflow.DeptAccountantResolver.DEPT_ROLE_NAME)));
    }

    /** 服务部门视图：部门 ID + 名称。 */
    public record ServiceDeptView(Long departmentId, String departmentName) {
    }
}
