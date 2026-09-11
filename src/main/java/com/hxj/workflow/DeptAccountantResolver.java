package com.hxj.workflow;

import com.hxj.entity.SysUser;
import com.hxj.repository.SysUserServiceDeptRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 「会计（按部门）」节点的路由解析器：按申请人部门，在核算分工表中
 * 找到挂「核算会计」角色且服务该部门的成员，返回其账号作为动态指派对象。
 *
 * <p>裁决规则：同一部门多名服务成员时按用户 ID 升序取首个（主办会计制）；
 * 无任何映射时返回空串，任务保持未指派，由管理员人工指派——与「直属主管」
 * 节点未设汇报线时的兜底行为一致。
 */
@Component
public class DeptAccountantResolver {

    /** 「会计（按部门）」节点路由的职能角色名。 */
    public static final String DEPT_ROLE_NAME = "核算会计";

    private final SysUserServiceDeptRepository serviceDeptRepository;

    public DeptAccountantResolver(SysUserServiceDeptRepository serviceDeptRepository) {
        this.serviceDeptRepository = serviceDeptRepository;
    }

    /** 解析申请人部门的主办会计账号；无分工映射时返回空串。 */
    public String resolve(SysUser applicant) {
        if (applicant == null || applicant.getDepartmentId() == null) {
            return "";
        }
        List<String> accounts = serviceDeptRepository
                .findServingAccountantAccounts(applicant.getDepartmentId(), DEPT_ROLE_NAME);
        return accounts.isEmpty() ? "" : accounts.get(0);
    }
}
