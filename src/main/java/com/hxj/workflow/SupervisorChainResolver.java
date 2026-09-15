package com.hxj.workflow;

import com.hxj.entity.SysDepartment;
import com.hxj.entity.SysUser;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysUserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 主管链解析器（钉钉模式）：主管锚定在部门负责人，人员档案的直属主管仅作人工覆盖。
 *
 * <p>解析规则（对申请链上每一个人逐级应用）：
 * <ol>
 *   <li>{@code manager_id} 有值 → 用人工指定的直属主管（保留虚线汇报、代理等现实灵活性）；</li>
 *   <li>未设 → 所在部门的负责人（{@code sys_department.leader_user_id}）；</li>
 *   <li>部门也未设负责人 → 沿部门闭包表自近及远找最近的设了负责人的祖先部门
 *       （保证链路向上兜底，与钉钉"逐级向上找部门负责人"一致）。</li>
 * </ol>
 *
 * <p>「直属主管」节点取链首，「逐级主管」节点消费整条链串行审批。
 * 防环：同一用户只入链一次；上限 10 级。任何一级解析失败即止——调用方
 * （提交校验前置）负责对空链给出可读的错误提示，此处不做静默编造。
 */
@Component
public class SupervisorChainResolver {

    /** 链长上限：与引擎多实例配置一致，防止脏数据成环撑爆流程。 */
    static final int MAX_CHAIN_LENGTH = 10;

    private final SysUserRepository userRepository;
    private final SysDepartmentRepository departmentRepository;

    public SupervisorChainResolver(SysUserRepository userRepository, SysDepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * 解析申请人的主管账号链（自下而上：直属主管在最前）。
     * 链可能为空——表示无任何可用主管来源，由调用方决定拦截或兜底。
     */
    public List<String> resolveChain(SysUser applicant) {
        List<String> chain = new ArrayList<>();
        if (applicant == null || applicant.getId() == null) {
            return chain;
        }
        Set<Long> visited = new LinkedHashSet<>();
        visited.add(applicant.getId());
        Long currentUserId = applicant.getId();
        Long currentManagerId = applicant.getManagerId();
        Long currentDepartmentId = applicant.getDepartmentId();
        while (chain.size() < MAX_CHAIN_LENGTH) {
            Long nextId = nextSupervisorUserId(currentUserId, currentManagerId, currentDepartmentId, visited);
            if (nextId == null) {
                break;
            }
            SysUser supervisor = userRepository.findById(nextId).orElse(null);
            if (supervisor == null || !visited.add(supervisor.getId())) {
                break;
            }
            chain.add(supervisor.getAccount());
            currentUserId = supervisor.getId();
            currentManagerId = supervisor.getManagerId();
            currentDepartmentId = supervisor.getDepartmentId();
        }
        return List.copyOf(chain);
    }

    /** 解析直属主管账号（链首；空串表示无主管——与流程变量 ${managerAccount} 的空值语义一致）。 */
    public String resolveFirstAccount(SysUser applicant) {
        List<String> chain = resolveChain(applicant);
        return chain.isEmpty() ? "" : chain.get(0);
    }

    /**
     * 找当前用户的下一级主管：人工指定优先，其次本部门负责人，最后沿祖先部门向上。
     * 指向已入链用户的指派视为环/重复，跳过并继续尝试下一来源。
     */
    private Long nextSupervisorUserId(
            Long currentUserId, Long managerId, Long departmentId, Set<Long> visited) {
        if (managerId != null && !visited.contains(managerId)) {
            return managerId;
        }
        if (departmentId == null) {
            return null;
        }
        SysDepartment department = departmentRepository.findById(departmentId).orElse(null);
        if (department != null && department.getLeaderUserId() != null
                && !visited.contains(department.getLeaderUserId())) {
            return department.getLeaderUserId();
        }
        for (Long ancestorId : departmentRepository.findAncestorIdsByDepth(departmentId)) {
            SysDepartment ancestor = departmentRepository.findById(ancestorId).orElse(null);
            if (ancestor != null && ancestor.getLeaderUserId() != null
                    && !visited.contains(ancestor.getLeaderUserId())) {
                return ancestor.getLeaderUserId();
            }
        }
        return null;
    }
}
