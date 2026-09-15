package com.hxj.workflow;

import com.hxj.entity.SysUser;
import com.hxj.repository.SysUserRepository;
import com.hxj.repository.SysUserServiceDeptRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 「角色 + 按发起人部门」范围（{@code ROLE} + {@code AssigneeScopeEnum.INITIATOR_DEPT}）的解析器。
 *
 * <p>这是钉钉同构的核心之一：<b>任何"按部门的某个业务角色"都复用本解析器，不新增审批人类型</b>。
 * 核算会计、部门HR、部门出纳、部门法务……全部是"角色数据 + 本解析器"，零代码扩展。
 *
 * <p>解析优先级（自近及远）：
 * <ol>
 *   <li><b>显式服务分工</b>：{@code sys_user_service_dept} 声明"该成员服务该部门"的角色成员
 *       ——共享服务中心模式（如核算会计集中坐席财务条线、按部门分工处理业务条线单据）；</li>
 *   <li><b>成员所属部门</b>：角色成员本身就归属该部门——钉钉原语义（如各部门HR），
 *       无需额外配分工即命中；</li>
 *   <li>均无命中 → 返回空列表，由提交关口拒绝并提示补配（把错误挡在提交时，而非卡单等管理员）。</li>
 * </ol>
 *
 * <p>多人命中按账号升序，取首个为主办（主办制）；其余作为同组候选保留在返回值中。
 */
@Component
public class DeptScopedRoleResolver {

    private final SysUserServiceDeptRepository serviceDeptRepository;
    private final SysUserRepository userRepository;

    public DeptScopedRoleResolver(SysUserServiceDeptRepository serviceDeptRepository,
                                  SysUserRepository userRepository) {
        this.serviceDeptRepository = serviceDeptRepository;
        this.userRepository = userRepository;
    }

    /** 解析服务/归属发起人所在部门的角色成员账号（按账号升序）；无命中返回空列表。 */
    public List<String> resolve(SysUser applicant, List<Long> roleIds) {
        if (applicant == null || applicant.getDepartmentId() == null
                || roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<String> served = serviceDeptRepository
                .findServedAccountsByRoleIds(applicant.getDepartmentId(), roleIds);
        if (!served.isEmpty()) {
            return served;
        }
        return userRepository.findAccountsByRoleIdsAndDepartmentId(roleIds, applicant.getDepartmentId());
    }

    /** 解析主办账号（链首）；无命中返回空串——供流程变量写入与提交关口判空复用。 */
    public String resolveFirst(SysUser applicant, List<Long> roleIds) {
        List<String> accounts = resolve(applicant, roleIds);
        return accounts.isEmpty() ? "" : accounts.get(0);
    }
}
