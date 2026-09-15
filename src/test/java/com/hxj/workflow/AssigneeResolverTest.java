package com.hxj.workflow;

import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysDepartment;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.entity.SysUserServiceDept;
import com.hxj.enums.AssigneeScopeEnum;
import com.hxj.enums.AssigneeSubjectEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.repository.SysDataScopeRepository;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysRoleRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.repository.SysUserServiceDeptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审批人统一解析器（六维正交模型）的求值规则测试。
 *
 * <p>覆盖钉钉全部寻人机制的组合：主体（成员/发起人/角色/直属主管/部门主管/表单联系人）
 * × 范围（全局/发起人部门）× 层级（第 N 级/连续多级）。
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({AssigneeResolver.class})
class AssigneeResolverTest {

    @Autowired private AssigneeResolver resolver;
    @Autowired private SysUserRepository userRepository;
    @Autowired private SysDepartmentRepository departmentRepository;
    @Autowired private SysRoleRepository roleRepository;
    @Autowired private SysUserServiceDeptRepository serviceDeptRepository;
    @Autowired private SysDataScopeRepository dataScopeRepository;

    private SysDataScope ownScope;
    private SysUser applicant;
    private SysUser firstManager;
    private SysUser secondManager;
    private SysUser centerHead;
    private SysUser departmentHead;
    private SysUser accountantInCenter;
    private SysUser accountantInDept;
    private SysRole accountantRole;

    @BeforeEach
    void setUp() {
        ownScope = dataScopeRepository.save(new SysDataScope("OWN", "仅本人单据"));
        accountantRole = roleRepository.save(role("核算会计", ownScope));
        roleRepository.save(role("出纳", ownScope));

        // 部门树：公司 → 中心 → 部门（负责人分别配在中心与部门）
        Long companyId = saveDepartment("公司", null);
        Long centerId = saveDepartment("中心", companyId);
        Long deptId = saveDepartment("部门", centerId);

        centerHead = saveUser("center-head", centerId, null);
        departmentHead = saveUser("dept-head", deptId, null);
        // 业务汇报线：applicant → first-manager → second-manager（上级指向高一级）
        secondManager = saveUser("second-manager", companyId, null);
        firstManager = saveUser("first-manager", companyId, secondManager.getId());
        applicant = saveUser("applicant", deptId, firstManager.getId());

        // 部门负责人：中心与部门各配一人（部门主管链自近及远为 dept-head → center-head）
        departmentRepository.findById(deptId).ifPresent(d -> d.setLeaderUserId(departmentHead.getId()));
        departmentRepository.findById(centerId).ifPresent(d -> d.setLeaderUserId(centerHead.getId()));
        departmentRepository.findAll().forEach(departmentRepository::save);

        // 角色成员：一人坐席中心（靠服务分工覆盖部门）、一人归属部门本体
        accountantInCenter = saveUser("accountant-center", centerId, null);
        accountantInDept = saveUser("accountant-dept", deptId, null);
        attachRole(accountantInCenter, accountantRole);
        attachRole(accountantInDept, accountantRole);
        // 显式服务分工：中心坐席的会计服务"部门"
        serviceDeptRepository.save(new SysUserServiceDept(accountantInCenter.getId(), deptId));
    }

    @Test
    void resolvesMemberSubjectFromAccounts() {
        FlowNodeConfig node = node(AssigneeSubjectEnum.MEMBER, firstManager.getAccount() + ","
                + secondManager.getAccount());
        assertThat(resolver.resolve(node, context())).containsExactly(
                firstManager.getAccount(), secondManager.getAccount());
    }

    @Test
    void resolvesInitiatorAndSelfSelectedSubjects() {
        FlowNodeConfig initiator = node(AssigneeSubjectEnum.INITIATOR, null);
        assertThat(resolver.resolve(initiator, context())).containsExactly("applicant");

        FlowNodeConfig selfSelect = node(AssigneeSubjectEnum.INITIATOR_SELECT, null);
        AssigneeResolver.Context withSelection =
                new AssigneeResolver.Context(applicant, Map.of(), List.of("center-head"));
        assertThat(resolver.resolve(selfSelect, withSelection)).containsExactly("center-head");
    }

    @Test
    void resolvesRoleGlobally() {
        FlowNodeConfig node = node(AssigneeSubjectEnum.ROLE, String.valueOf(accountantRole.getId()));
        assertThat(resolver.resolve(node, context()))
                .containsExactlyInAnyOrder("accountant-center", "accountant-dept");
    }

    @Test
    void resolvesRoleScopedToInitiatorDepartmentWithExplicitServiceMapping() {
        FlowNodeConfig node = node(AssigneeSubjectEnum.ROLE, String.valueOf(accountantRole.getId()));
        node.setAssigneeScope(AssigneeScopeEnum.INITIATOR_DEPT);
        // 显式服务分工：中心坐席的会计服务该部门 → 命中并优先于"所属部门"回落
        assertThat(resolver.resolve(node, context())).containsExactly("accountant-center");
    }

    @Test
    void fallsBackToMemberOwnDepartmentWhenNoServiceMapping() {
        serviceDeptRepository.deleteAll();
        FlowNodeConfig node = node(AssigneeSubjectEnum.ROLE, String.valueOf(accountantRole.getId()));
        node.setAssigneeScope(AssigneeScopeEnum.INITIATOR_DEPT);
        assertThat(resolver.resolve(node, context())).containsExactly("accountant-dept");
    }

    @Test
    void resolvesSuperiorLevelsAlongBusinessReportingLine() {
        FlowNodeConfig first = node(AssigneeSubjectEnum.SUPERIOR, null);
        first.setAssigneeLevel(1);
        assertThat(resolver.resolve(first, context())).containsExactly("first-manager");

        FlowNodeConfig second = node(AssigneeSubjectEnum.SUPERIOR, null);
        second.setAssigneeLevel(2);
        assertThat(resolver.resolve(second, context())).containsExactly("second-manager");

        // 连续多级：逐级审到第 2 级 → 两人都要审
        FlowNodeConfig chain = node(AssigneeSubjectEnum.SUPERIOR, null);
        chain.setAssigneeLevel(2);
        chain.setAssigneeChain(true);
        assertThat(resolver.resolve(chain, context()))
                .containsExactly("first-manager", "second-manager");
    }

    @Test
    void resolvesDeptHeadAlongDepartmentLeaderChain() {
        FlowNodeConfig first = node(AssigneeSubjectEnum.DEPT_HEAD, null);
        first.setAssigneeLevel(1);
        assertThat(resolver.resolve(first, context())).containsExactly("dept-head");

        FlowNodeConfig second = node(AssigneeSubjectEnum.DEPT_HEAD, null);
        second.setAssigneeLevel(2);
        assertThat(resolver.resolve(second, context())).containsExactly("center-head");

        FlowNodeConfig chain = node(AssigneeSubjectEnum.DEPT_HEAD, null);
        chain.setAssigneeLevel(2);
        chain.setAssigneeChain(true);
        assertThat(resolver.resolve(chain, context()))
                .containsExactly("dept-head", "center-head");
    }

    @Test
    void resolvesFormMemberFromFormValues() {
        FlowNodeConfig node = node(AssigneeSubjectEnum.FORM_MEMBER, "reviewer");
        AssigneeResolver.Context context =
                new AssigneeResolver.Context(applicant, Map.of("reviewer", "center-head"), List.of());
        assertThat(resolver.resolve(node, context)).containsExactly("center-head");
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        FlowNodeConfig roleNode = node(AssigneeSubjectEnum.ROLE,
                String.valueOf(roleRepository.save(role("空角色", ownScope)).getId()));
        assertThat(resolver.resolve(roleNode, context())).isEmpty();

        SysUser orphan = saveUser("orphan", departmentHead.getDepartmentId(), null);
        FlowNodeConfig superior = node(AssigneeSubjectEnum.SUPERIOR, null);
        assertThat(resolver.resolve(superior, new AssigneeResolver.Context(orphan, Map.of(), List.of())))
                .isEmpty();
    }

    // ==================== 辅助 ====================

    private AssigneeResolver.Context context() {
        return new AssigneeResolver.Context(applicant, Map.of(), List.of());
    }

    private static FlowNodeConfig node(AssigneeSubjectEnum subject, String value) {
        FlowNodeConfig node = new FlowNodeConfig("测试节点", FlowNodeTypeEnum.APPROVAL);
        node.setAssigneeSubject(subject);
        node.setAssigneeValue(value);
        return node;
    }

    private SysRole role(String name, SysDataScope scope) {
        SysRole role = new SysRole();
        role.setName(name);
        role.setDepartment("公司");
        role.setPost("职能岗");
        role.setDataScope(scope);
        return role;
    }

    private void attachRole(SysUser user, SysRole role) {
        user.getRoles().add(role);
        userRepository.save(user);
    }

    private Long saveDepartment(String name, Long parentId) {
        SysDepartment department = new SysDepartment();
        department.setName(name);
        department.setParentId(parentId);
        department.setSortOrder(0);
        return departmentRepository.save(department).getId();
    }

    private SysUser saveUser(String account, Long departmentId, Long managerId) {
        SysUser user = new SysUser();
        user.setAccount(account);
        user.setName(account);
        user.setJobNo("J-" + account);
        user.setPassword("x");
        user.setDepartmentId(departmentId);
        user.setPostId(1L);
        user.setManagerId(managerId);
        return userRepository.save(user);
    }
}
