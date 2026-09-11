package com.hxj.permission;

import com.hxj.config.SecurityBeansConfig;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysPermission;
import com.hxj.entity.SysRole;
import com.hxj.entity.CcRecord;
import com.hxj.entity.OaDocument;
import com.hxj.entity.SysUser;
import com.hxj.enums.FlowCategoryEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.enums.BusinessTypeEnum;
import com.hxj.enums.CcSourceEnum;
import com.hxj.enums.DocumentStatusEnum;
import com.hxj.enums.DocumentTypeEnum;
import com.hxj.enums.UserStatusEnum;
import com.hxj.exception.BusinessException;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.SysDataScopeRepository;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysPermissionRepository;
import com.hxj.repository.SysRoleRepository;
import com.hxj.repository.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({EmployeeManagementService.class, EmployeeOffboardingService.class, RoleManagementService.class, PermissionManagementServiceTest.OffboardingPortConfig.class,
        DepartmentManagementService.class, PostManagementService.class,
        SecurityBeansConfig.class})
class PermissionManagementServiceTest {

    @Autowired private EmployeeManagementService employeeService;
    @Autowired private RoleManagementService roleService;
    @Autowired private DepartmentManagementService departmentService;
    @Autowired private PostManagementService postService;
    @Autowired private SysUserRepository userRepository;
    @Autowired private SysRoleRepository roleRepository;
    @Autowired private SysDataScopeRepository dataScopeRepository;
    @Autowired private SysPermissionRepository permissionRepository;
    @Autowired private SysDepartmentRepository departmentRepository;
    @Autowired private FlowConfigRepository flowConfigRepository;
    @Autowired private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;
    @Autowired private EmployeeOffboardingService offboardingService;
    @Autowired private PasswordEncoder passwordEncoder;

    private SysDataScope scope;
    private SysPermission permission;
    private SysRole role;
    private Long departmentId;
    private Long postId;

    @BeforeEach
    void setUp() {
        scope = dataScopeRepository.save(new SysDataScope("OWN", "仅本人单据"));
        permission = permissionRepository.save(new SysPermission("VIEW_OWN_FORMS", "查看本人表单"));
        role = new SysRole();
        role.setName("普通员工");
        role.setDepartment("业务部");
        role.setPost("员工");
        role.setDataScope(scope);
        role.addPermission(permission);
        roleRepository.save(role);

        departmentId = departmentService.create(new SaveDepartmentRequest("业务部", null, 1)).id();
        role.setDepartmentId(departmentId);
        roleRepository.save(role);
        postId = postService.create(new SavePostRequest("专员")).id();
    }

    /** 离职校验上下文的空工作流端口（本测试不涉及 Flowable 流转）。 */
    @TestConfiguration
    static class OffboardingPortConfig {
        @Bean
        public com.hxj.workflow.WorkflowPort offboardingWorkflowPort() {
            return new com.hxj.workflow.WorkflowPort() {
                @Override public String startProcess(Long configId, Long documentId, java.util.Map<String, Object> variables) { return "pid-" + documentId; }
                @Override public void completeTask(String taskId, java.util.Map<String, Object> variables) { }
                @Override public java.util.List<org.flowable.task.api.Task> pendingTasksForUser(String account, java.util.List<String> roleNames) { return java.util.List.of(); }
                @Override public java.util.List<org.flowable.task.api.Task> tasksForProcess(String processInstanceId) { return java.util.List.of(); }
                @Override public java.util.List<org.flowable.task.api.Task> allActiveTasks() { return java.util.List.of(); }
                @Override public java.util.List<org.flowable.task.api.Task> delegatedTasks() { return java.util.List.of(); }
                @Override public void moveTaskToActivity(String processInstanceId, String taskId, String targetActivityId) { }
                @Override public void endProcess(String processInstanceId, String reason) { }
                @Override public void setAssignee(String taskId, String account) { }
                @Override public void delegateTask(String taskId, String account) { }
                @Override public void resolveTask(String taskId) { }
                @Override public java.util.List<com.hxj.workflow.WorkflowHistoryItemResponse> history(String processInstanceId) { return java.util.List.of(); }
                @Override public java.util.List<com.hxj.workflow.WorkflowNodeStatResponse> nodeStatistics() { return java.util.List.of(); }
            };
        }
    }
    @Test
    void shouldCreateAndEditEmployeeAccount() {
        EmployeeResponse created = employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, null, List.of(role.getName())));

        SysUser persisted = userRepository.findById(created.id()).orElseThrow();
        assertThat(passwordEncoder.matches("password", persisted.getPassword())).isTrue();
        assertThat(persisted.getDepartmentId()).isEqualTo(departmentId);
        assertThat(persisted.getDepartment()).isEqualTo("业务部");
        assertThat(persisted.getPostId()).isEqualTo(postId);
        assertThat(persisted.getPost()).isEqualTo("专员");
        assertThat(persisted.getRoles()).extracting(SysRole::getName).containsExactly("普通员工");
        assertThat(persisted.hasPermission("VIEW_OWN_FORMS")).isTrue();

        Long newDepartmentId = departmentService.create(new SaveDepartmentRequest("财务部", null, 2)).id();
        
        EmployeeResponse updated = employeeService.update(created.id(), new UpdateEmployeeRequest(
                "张三（离职）", "HXJ100", newDepartmentId, postId, null, UserStatusEnum.RESIGNED,
                null, List.of(role.getName())));
        assertThat(updated.status()).isEqualTo(UserStatusEnum.RESIGNED);
        assertThat(updated.departmentId()).isEqualTo(newDepartmentId);
        assertThat(userRepository.findById(created.id()).orElseThrow().isLoginEnabled()).isFalse();
    }

    @Test
    void shouldRejectUnknownDepartmentOrPost() {
        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", 999L, postId, null, List.of(role.getName()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("部门不存在");

        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, 999L, null, List.of(role.getName()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位不存在");
    }

    @Test
    void shouldResetEmployeePassword() {
        EmployeeResponse created = employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, null, List.of(role.getName())));

        employeeService.resetPassword(created.id(), new ResetPasswordRequest("new-pass"));

        SysUser persisted = userRepository.findById(created.id()).orElseThrow();
        assertThat(passwordEncoder.matches("new-pass", persisted.getPassword())).isTrue();
    }

    @Test
    void shouldRejectIncompleteOrDuplicateEmployeeAccount() {
        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "", "", departmentId, postId, null, List.of(role.getName()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请完整填写员工、账号和密码信息");

        employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, null, List.of(role.getName())));
        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest(
                "李四", "HXJ101", "zhangsan", "password", departmentId, postId, null, List.of(role.getName()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录账号已存在");
    }

    @Test
    void shouldCreateUpdateRoleAndReturnDepartmentTree() {
        SysPermission approve = permissionRepository.save(
                new SysPermission("DEPARTMENT_HEAD_APPROVAL", "部门负责人审批"));

        RoleResponse created = roleService.create(new SaveRoleRequest(
                "部门负责人", departmentId, "经理", scope.getCode(), null, List.of(permission.getCode(), approve.getCode())));
        assertThat(created.permissions()).containsExactlyInAnyOrder("VIEW_OWN_FORMS", "DEPARTMENT_HEAD_APPROVAL");

        RoleResponse updated = roleService.update(created.id(), new SaveRoleRequest(
                "部门经理", departmentId, "经理", scope.getCode(), null, List.of(approve.getCode())));
        assertThat(updated.name()).isEqualTo("部门经理");
        assertThat(updated.permissions()).containsExactly("DEPARTMENT_HEAD_APPROVAL");

        List<DepartmentRoleNodeResponse> tree = roleService.departmentTree();
        DepartmentRoleNodeResponse businessDepartment = tree.stream()
                .filter(node -> node.name().equals("业务部")).findFirst().orElseThrow();
        assertThat(businessDepartment.id()).isEqualTo(departmentId);
        assertThat(businessDepartment.parentId()).isNull();
        assertThat(businessDepartment.roles()).extracting(RoleTreeNodeResponse::name)
                .contains("普通员工", "部门经理");
    }

    @Test
    void shouldGuardDepartmentAndPostDictionaryOperations() {
        // 部门重名
        assertThatThrownBy(() -> departmentService.create(new SaveDepartmentRequest("业务部", null, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("部门名称已存在");
        // 有员工的部门禁止删除
        EmployeeResponse employee = employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, null, List.of(role.getName())));
        assertThatThrownBy(() -> departmentService.delete(departmentId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("部门下存在员工，无法删除");
        // 有员工的岗位禁止删除、岗位重名
        assertThatThrownBy(() -> postService.delete(postId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位下存在员工，无法删除");
        assertThatThrownBy(() -> postService.create(new SavePostRequest("专员")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位名称已存在");

        // 部门改名同步员工快照
        departmentService.update(departmentId, new SaveDepartmentRequest("销售部", null, 1));
        assertThat(userRepository.findById(employee.id()).orElseThrow().getDepartment()).isEqualTo("销售部");

        // 无引用后可删除
        employeeService.update(employee.id(), new UpdateEmployeeRequest(
                "张三", "HXJ100", departmentId, postId, null, UserStatusEnum.RESIGNED,
                null, List.of(role.getName())));
        userRepository.delete(userRepository.findById(employee.id()).orElseThrow());
        // 有角色引用的部门禁止删除（角色挂在部门上的守卫）
        assertThatThrownBy(() -> departmentService.delete(departmentId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("部门被角色引用，无法删除");
        roleRepository.delete(role);
        departmentService.delete(departmentId);
        assertThat(departmentRepository.existsById(departmentId)).isFalse();
    }

    @Test
    void shouldGuardRoleDeletion() {
        // 有员工引用的角色禁止删除
        EmployeeResponse employee = employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, null, List.of(role.getName())));
        assertThatThrownBy(() -> roleService.delete(role.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("角色下存在员工，无法删除");

        // 解除员工引用（转入过渡角色）后，被流程节点引用（含组合角色串的段）仍禁止删除
        RoleResponse bridgeRole = roleService.create(new SaveRoleRequest(
                "过渡角色", departmentId, "助理", scope.getCode(), null, List.of(permission.getCode())));
        employeeService.update(employee.id(), new UpdateEmployeeRequest(
                "张三", "HXJ100", departmentId, postId, null, UserStatusEnum.RESIGNED,
                null, List.of(bridgeRole.name())));
        userRepository.flush();
        FlowConfig flowConfig = new FlowConfig();
        flowConfig.setType("测试流程");
        flowConfig.setCategory(FlowCategoryEnum.DAILY);
        FlowNodeConfig node = new FlowNodeConfig("直属主管", FlowNodeTypeEnum.APPROVAL);
        node.setAssigneeRole("二级部门负责人/" + role.getName());
        flowConfig.addNode(node);
        flowConfigRepository.saveAndFlush(flowConfig);
        assertThatThrownBy(() -> roleService.delete(role.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("角色被流程节点引用，无法删除");

        // 名称部分重叠但不同段的角色不受误伤，无引用角色可正常删除
        RoleResponse freeRole = roleService.create(new SaveRoleRequest(
                "二级部门负责人助理", departmentId, "助理", scope.getCode(), null, List.of(permission.getCode())));
        roleService.delete(freeRole.id());
        assertThat(roleRepository.existsById(freeRole.id())).isFalse();
    }

    @Test
    void shouldManageEmployeeManagerLine() {
        EmployeeResponse manager = employeeService.create(new CreateEmployeeRequest(
                "王主管", "HXJ200", "wangzhu", "password", departmentId, postId, null, List.of(role.getName())));
        EmployeeResponse employee = employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ201", "zhangsan2", "password", departmentId, postId, "wangzhu", List.of(role.getName())));
        org.assertj.core.api.Assertions.assertThat(employee.managerAccount()).isEqualTo("wangzhu");
        org.assertj.core.api.Assertions.assertThat(employee.managerName()).isEqualTo("王主管");

        // 自引用拒绝
        assertThatThrownBy(() -> employeeService.update(employee.id(), new UpdateEmployeeRequest(
                "张三", "HXJ201", departmentId, postId, "zhangsan2", UserStatusEnum.ACTIVE, null, List.of(role.getName()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("直属主管不能是自己");

        // 主管不存在拒绝
        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest(
                "李四", "HXJ202", "lisi", "password", departmentId, postId, "ghost", List.of(role.getName()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("直属主管不存在");

        // 清空汇报线
        EmployeeResponse cleared = employeeService.update(employee.id(), new UpdateEmployeeRequest(
                "张三", "HXJ201", departmentId, postId, null, UserStatusEnum.ACTIVE, null, List.of(role.getName())));
        org.assertj.core.api.Assertions.assertThat(cleared.managerId()).isNull();
    }

    @Test
    void shouldSyncFlowNodeAndRolePostOnRename() {
        // 流程节点绑定的角色组合串随角色改名按段同步
        FlowConfig flowConfig = new FlowConfig();
        flowConfig.setType("改名同步流程");
        flowConfig.setCategory(FlowCategoryEnum.DAILY);
        FlowNodeConfig node = new FlowNodeConfig("直属主管", FlowNodeTypeEnum.APPROVAL);
        node.setAssigneeRole(role.getName() + "/其他角色");
        flowConfig.addNode(node);
        flowConfigRepository.saveAndFlush(flowConfig);

        roleService.update(role.getId(), new SaveRoleRequest(
                "改名后角色", departmentId, "员工", scope.getCode(), null, List.of(permission.getCode())));

        assertThat(roleRepository.findById(role.getId()).orElseThrow().getName()).isEqualTo("改名后角色");
        FlowNodeConfig updated = flowConfigRepository.findById(flowConfig.getId()).orElseThrow()
                .getNodes().stream().filter(n -> n.getName().equals("直属主管")).findFirst().orElseThrow();
        assertThat(updated.getAssigneeRole()).isEqualTo("改名后角色/其他角色");

        // 岗位改名同步角色岗位快照
        role.setPost("专员");
        roleRepository.save(role);
        postService.update(postId, new SavePostRequest("高级专员"));
        assertThat(roleRepository.findById(role.getId()).orElseThrow().getPost()).isEqualTo("高级专员");
    }

    @Test
    void shouldListPermissionAndDataScopeDictionaries() {
        List<PermissionViews.PermissionPoint> permissions = roleService.listPermissions();
        assertThat(permissions).extracting(PermissionViews.PermissionPoint::code).contains("VIEW_OWN_FORMS");
        assertThat(permissions).allSatisfy(point -> assertThat(point.name()).isNotBlank());

        List<DataScopeViews.Scope> scopes = roleService.listDataScopes();
        assertThat(scopes).extracting(DataScopeViews.Scope::code).contains("OWN");
    }

    @Test
    void shouldFilterEmployeeListByDictionaryAndStatus() {
        employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, null, List.of(role.getName())));

        assertThat(employeeService.list(departmentId, postId, null)).hasSize(1);
        assertThat(employeeService.list(999L, postId, null)).isEmpty();
        assertThat(employeeService.list(null, 999L, null)).isEmpty();
        assertThat(employeeService.list(null, null, UserStatusEnum.RESIGNED)).isEmpty();
        assertThat(employeeService.list(null, null, UserStatusEnum.ACTIVE)).hasSize(1);
        assertThat(employeeService.list(null, null, null)).hasSize(1);
    }

    @Test
    void shouldReassignReportingLineOnResignationAndTransfer() {
        // 汇报线：陈总监 ← 主管甲 ← 下属乙
        EmployeeResponse mgr1 = employeeService.create(new CreateEmployeeRequest(
                "陈总监", "HXJ201", "mgr1", "password", departmentId, postId, null, List.of(role.getName())));
        EmployeeResponse mgrA = employeeService.create(new CreateEmployeeRequest(
                "主管甲", "HXJ202", "mgr_a", "password", departmentId, postId, "mgr1", List.of(role.getName())));
        employeeService.create(new CreateEmployeeRequest(
                "下属乙", "HXJ203", "sub_b", "password", departmentId, postId, "mgr_a", List.of(role.getName())));

        // 路径一：有下属的管理者转离职 → 更新被守卫拦截，强制先走离职交接
        assertThatThrownBy(() -> employeeService.update(mgrA.id(), new UpdateEmployeeRequest(
                "主管甲", "HXJ202", departmentId, postId, "mgr1", UserStatusEnum.RESIGNED,
                null, List.of(role.getName()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直属下属");
        // 交接后：下属汇报线改挂交接人
        EmployeeResponse mgrC = employeeService.create(new CreateEmployeeRequest(
                "交接丙", "HXJ204", "mgr_c", "password", departmentId, postId, "mgr1", List.of(role.getName())));
        offboardingService.transferAll(mgrA.id(), "mgr_c", "mgr1");
        assertThat(userRepository.findByAccount("sub_b").orElseThrow().getManagerId())
                .isEqualTo(mgrC.id());

        // 路径二：无交接人退回 → 下属汇报线上移给离职者的主管
        EmployeeResponse mgrD = employeeService.create(new CreateEmployeeRequest(
                "主管丁", "HXJ205", "mgr_d", "password", departmentId, postId, "mgr1", List.of(role.getName())));
        employeeService.create(new CreateEmployeeRequest(
                "下属戊", "HXJ206", "sub_e", "password", departmentId, postId, "mgr_d", List.of(role.getName())));
        assertThatThrownBy(() -> employeeService.update(mgrD.id(), new UpdateEmployeeRequest(
                "主管丁", "HXJ205", departmentId, postId, "mgr1", UserStatusEnum.RESIGNED,
                null, List.of(role.getName()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直属下属");
        offboardingService.rejectAll(mgrD.id(), "mgr1");
        assertThat(userRepository.findByAccount("sub_e").orElseThrow().getManagerId())
                .isEqualTo(mgr1.id());
    }

    @Test
    void shouldGuardDepartmentReferencedByCustomScope() {
        Long dept2 = departmentService.create(new SaveDepartmentRequest("风控部", null, 2)).id();
        dataScopeRepository.save(new SysDataScope("CUSTOM", "自定义部门集合"));
        roleService.create(new SaveRoleRequest("风控合规", departmentId, "合规岗", "CUSTOM",
                List.of(dept2), List.of(permission.getCode())));
        // CUSTOM 范围的部门集合引用：外键是 CASCADE，必须由守卫拦截，否则角色可见范围静默缩水
        assertThatThrownBy(() -> departmentService.delete(dept2))
                .isInstanceOf(BusinessException.class)
                .hasMessage("部门被自定义数据范围引用，无法删除");
    }

    @Test
    void shouldGuardRoleReferencedByCcRecord() {
        // 申请人挂专用角色，避免占用被删角色的成员引用而触发成员守卫
        RoleResponse applicantRole = roleService.create(new SaveRoleRequest(
                "申请人专用", departmentId, "专员", "OWN", null, List.of(permission.getCode())));
        EmployeeResponse applicant = employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, null, List.of(applicantRole.name())));
        OaDocument document = new OaDocument();
        document.setDocCode("BX202601010001");
        document.setBusinessType(BusinessTypeEnum.DAILY_PAYMENT);
        document.setApplicant(userRepository.findByAccount("zhangsan").orElseThrow());
        document.setStatus(DocumentStatusEnum.PENDING);
        entityManager.persistAndFlush(document);
        entityManager.persistAndFlush(CcRecord.toRole(document, role, CcSourceEnum.FLOW));
        // 抄送记录历史直接引用角色 ID（外键无级联），不守卫删除会 500
        assertThatThrownBy(() -> roleService.delete(role.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("角色被抄送记录引用，无法删除");
    }
}