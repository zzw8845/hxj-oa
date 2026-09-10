package com.hxj.approval;

import com.hxj.entity.*;
import com.hxj.enums.*;
import com.hxj.exception.BusinessException;
import com.hxj.repository.ApprovalRecordRepository;
import com.hxj.repository.ArchiveLedgerRepository;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.OaAttachmentRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysDataScopeRepository;
import com.hxj.repository.SysRoleRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.permission.EmployeeResponse;
import com.hxj.permission.UpdateEmployeeRequest;
import com.hxj.security.AuthenticatedUserResponse;
import com.hxj.security.TestSecurityContext;
import com.hxj.workflow.OaWorkflowService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 5.4–5.8 审批操作全链路：通过、驳回、补材料、加签、权限校验。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:approval-oa;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.upload-dir=target/test-uploads",
        "flowable.database-schema-update=create-drop",
        "flowable.async-executor-activate=false",
        "flowable.async-history-executor-activate=false",
        "flowable.app.enabled=false",
        "flowable.cmmn.enabled=false",
        "flowable.dmn.enabled=false",
        "flowable.idm.enabled=false",
        "flowable.eventregistry.enabled=false"
})
class ApprovalActionServiceIntegrationTest {

    @Autowired private ApprovalActionService actionService;
    @Autowired private OaWorkflowService workflowService;
    @Autowired private OaDocumentRepository documentRepository;
    @Autowired private ApprovalRecordRepository approvalRepository;
    @Autowired private ArchiveLedgerRepository archiveLedgerRepository;
    @Autowired private OaAttachmentRepository attachmentRepository;
    @Autowired private FlowConfigRepository flowConfigRepository;
    @Autowired private SysUserRepository userRepository;
    @Autowired private SysRoleRepository roleRepository;
    @Autowired private SysDataScopeRepository dataScopeRepository;
    @Autowired private com.hxj.repository.SysDepartmentRepository departmentRepository;
    @Autowired private com.hxj.repository.SysPostRepository postRepository;
    @Autowired private com.hxj.permission.EmployeeOffboardingService offboardingService;
    @Autowired private com.hxj.permission.EmployeeManagementService employeeService;
    @Autowired private RuntimeService runtimeService;

    private AuthenticatedUserResponse manager;
    private AuthenticatedUserResponse gm;
    private AuthenticatedUserResponse outsider;
    private AuthenticatedUserResponse admin;
    private FlowConfig config;
    private long sequence = 0;

    @BeforeEach
    void setUp() {
        archiveLedgerRepository.deleteAll();
        approvalRepository.deleteAll();
        attachmentRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        dataScopeRepository.deleteAll();
        flowConfigRepository.deleteAll();

        SysDataScope deptScope = dataScopeRepository.save(
                new SysDataScope("OWN_DEPARTMENT_DOCUMENTS", "本部门单据"));
        SysRole managerRole = new SysRole();
        managerRole.setName("二级部门负责人");
        managerRole.setDataScope(deptScope);
        roleRepository.saveAndFlush(managerRole);
        SysRole gmRole = new SysRole();
        gmRole.setName("执行总经理");
        gmRole.setDataScope(deptScope);
        roleRepository.saveAndFlush(gmRole);

        SysUser managerUser = user("张主管", "manager1", "财务部", managerRole);
        SysUser gmUser = user("王总", "gm1", "管理层", gmRole);
        SysUser signerUser = user("李会计", "signer1", "财务部", managerRole);
        SysUser outsiderUser = user("赵外部", "outsider1", "其他部门", null);
        userRepository.saveAllAndFlush(List.of(managerUser, gmUser, signerUser, outsiderUser));

        manager = auth(managerUser, List.of("二级部门负责人"));
        gm = auth(gmUser, List.of("执行总经理"));
        outsider = auth(outsiderUser, List.of());

        SysUser adminUser = user("系统管理员", "admin1", "总经办", null);
        userRepository.saveAndFlush(adminUser);
        admin = new AuthenticatedUserResponse(adminUser.getId(), adminUser.getAccount(), adminUser.getName(),
                adminUser.getDepartment(), adminUser.getPost(), List.of(),
                List.of("CONFIGURE_FLOW_PERMISSION"), List.of("ALL"));

        config = new FlowConfig();
        config.setType("采购申请");
        config.setCategory(FlowCategoryEnum.DAILY);
        config.addNode(new FlowNodeConfig("发起人", FlowNodeTypeEnum.START));
        FlowNodeConfig managerNode = new FlowNodeConfig("直属主管", FlowNodeTypeEnum.APPROVAL);
        managerNode.setAssigneeRole("二级部门负责人");
        config.addNode(managerNode);
        FlowNodeConfig gmNode = new FlowNodeConfig("执行总经理审批", FlowNodeTypeEnum.APPROVAL);
        gmNode.setAssigneeRole("执行总经理");
        config.addNode(gmNode);
        config.addNode(new FlowNodeConfig("抄送财务", FlowNodeTypeEnum.CC));
        flowConfigRepository.saveAndFlush(config);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContext.clear();
    }

    private void login(AuthenticatedUserResponse user) {
        TestSecurityContext.mock(user);
    }

    @Test
    void shouldApproveThroughAllNodesAndArchive() {
        OaDocument document = submittedDocument();
        ApprovalRequest request = new ApprovalRequest("同意", evidenceId(document), null, null, null, null, null);

        login(manager);
        ApprovalResultResponse first = actionService.approve(document.getId(), request);
        assertThat(first.status()).isEqualTo(DocumentStatusEnum.APPROVING);
        assertThat(first.currentNode()).isEqualTo("执行总经理审批");

        login(gm);
        ApprovalResultResponse second = actionService.approve(document.getId(), request);
        assertThat(second.status()).isEqualTo(DocumentStatusEnum.APPROVED);
        assertThat(second.archived()).isTrue();
        assertThat(archiveLedgerRepository.findByDocumentId(document.getId())).isPresent();
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(document.getProcessInstanceId()).singleResult()).isNull();
    }

    @Test
    void shouldRejectToSpecifiedNodeAndRestoreTask() {
        OaDocument document = submittedDocument();

        login(manager);
        actionService.approve(document.getId(),
                new ApprovalRequest("同意", evidenceId(document), null, null, null, null, null));

        login(gm);
        ApprovalResultResponse result = actionService.reject(document.getId(),
                new ApprovalRequest("金额有误，退回修改", null, "直属主管", "请补充报价单", null, null, null));

        assertThat(result.status()).isEqualTo(DocumentStatusEnum.REJECTED);
        assertThat(result.currentNode()).isEqualTo("直属主管");
        assertThat(workflowService.tasksForProcess(document.getProcessInstanceId()))
                .extracting(org.flowable.task.api.Task::getName)
                .containsExactly("直属主管");
        assertThat(approvalRepository.findByDocumentIdOrderByCreatedAtAsc(document.getId()))
                .extracting(ApprovalRecord::getRejectMaterials)
                .contains("请补充报价单");
    }

    @Test
    void shouldRejectToSubmitterAndEndProcess() {
        OaDocument document = submittedDocument();
        login(manager);
        ApprovalResultResponse result = actionService.reject(document.getId(),
                new ApprovalRequest("材料不齐，退回发起人", null, "提交人", null, null, null, null));

        assertThat(result.status()).isEqualTo(DocumentStatusEnum.REJECTED);
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(document.getProcessInstanceId()).singleResult()).isNull();
    }

    @Test
    void shouldHoldClosureUntilAfterPayMaterialsReturned() {
        OaDocument document = submittedDocument();
        ApprovalRequest request = new ApprovalRequest(null, evidenceId(document), null, null, null, null, null);

        login(manager);
        actionService.approve(document.getId(), request);

        login(gm);
        ApprovalResultResponse supplemented = actionService.supplement(document.getId(),
                new ApprovalRequest("付款后再补合同原件", evidenceId(document), null, null,
                        SupplementModeEnum.AFTER_PAY, "核算会计", "合同原件"));
        // 付款后补充：流程继续但未补齐材料前不可闭环
        assertThat(supplemented.status()).isEqualTo(DocumentStatusEnum.SUPPLEMENT_REQUIRED);
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(document.getProcessInstanceId()).singleResult()).isNull();

        ApprovalResultResponse result = actionService.submitSupplementMaterials(document.getId(),
                List.of(new MockMultipartFile("files", "contract.pdf", "application/pdf", new byte[]{1, 2, 3})));
        assertThat(result.status()).isEqualTo(DocumentStatusEnum.APPROVED);
        assertThat(result.archived()).isTrue();
    }

    @Test
    void shouldPauseAndResumeBeforePaySupplement() {
        OaDocument document = submittedDocument();

        login(manager);
        actionService.supplement(document.getId(),
                new ApprovalRequest("付款前补发票", evidenceId(document), null, null,
                        SupplementModeEnum.BEFORE_PAY, "提交人", "增值税发票"));
        assertThat(documentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(DocumentStatusEnum.SUPPLEMENT_REQUIRED);
        assertThat(workflowService.tasksForProcess(document.getProcessInstanceId())).hasSize(1);

        login(manager);
        ApprovalResultResponse result = actionService.submitSupplementMaterials(document.getId(),
                List.of(new MockMultipartFile("files", "invoice.pdf", "application/pdf", new byte[]{4})));
        // 付款前补充完成：跳过当前审批人进入下一节点
        assertThat(result.status()).isEqualTo(DocumentStatusEnum.APPROVING);
        assertThat(result.currentNode()).isEqualTo("执行总经理审批");
    }

    @Test
    void shouldDelegateTaskToSignUserAndReturnAfterComment() {
        OaDocument document = submittedDocument();
        SysUser signer = userRepository.findByAccount("signer1").orElseThrow();

        login(manager);
        ApprovalResultResponse signed = actionService.sign(document.getId(),
                new SignRequest(signer.getAccount(), "请协助核对金额"));
        assertThat(signed.status()).isEqualTo(DocumentStatusEnum.APPROVING);
        assertThat(workflowService.tasksForProcess(document.getProcessInstanceId()))
                .extracting(org.flowable.task.api.Task::getAssignee)
                .containsExactly("signer1");

        login(auth(signer, List.of("二级部门负责人")));
        ApprovalResultResponse commented = actionService.signComment(
                document.getId(), "金额核对无误");
        assertThat(commented.status()).isEqualTo(DocumentStatusEnum.APPROVING);
        // 归还后原审批人可继续处理
        assertThat(workflowService.pendingTasksForUser("manager1", List.of("二级部门负责人")))
                .anyMatch(task -> document.getProcessInstanceId().equals(task.getProcessInstanceId()));
    }

    @Test
    void shouldRejectOperationFromNonApprover() {
        OaDocument document = submittedDocument();
        login(outsider);
        assertThatThrownBy(() -> actionService.approve(document.getId(),
                new ApprovalRequest("同意", 1L, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权查看");
    }

    @Test
    void shouldRequireEvidenceOnApprove() {
        OaDocument document = submittedDocument();
        login(manager);
        assertThatThrownBy(() -> actionService.approve(document.getId(),
                new ApprovalRequest("同意", null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("凭证");
    }

    @Test
    void shouldHoldSealClosureUntilStampedFileReturned() {
        OaDocument document = submittedSealDocument();
        ApprovalRequest request = new ApprovalRequest("同意用印", evidenceId(document), null, null, null, null, null);

        // 末节点审批通过，但未回传盖章文件 → 用印流程不可完结
        login(manager);
        ApprovalResultResponse approved = actionService.approve(document.getId(), request);
        assertThat(approved.status()).isEqualTo(DocumentStatusEnum.SUPPLEMENT_REQUIRED);
        assertThat(approved.archived()).isFalse();
        assertThat(archiveLedgerRepository.findByDocumentId(document.getId())).isEmpty();

        // 回传盖章文件后归档完结
        ApprovalResultResponse returned = actionService.returnStampedFile(document.getId(),
                new MockMultipartFile("file", "stamped.pdf", "application/pdf", new byte[]{9, 9}));
        assertThat(returned.status()).isEqualTo(DocumentStatusEnum.APPROVED);
        assertThat(returned.archived()).isTrue();
        assertThat(archiveLedgerRepository.findByDocumentId(document.getId())).isPresent();
        assertThat(attachmentRepository.findAll().stream()
                .filter(attachment -> document.getId().equals(attachment.getDocument().getId())))
                .anyMatch(attachment -> "盖章文件".equals(attachment.getNodeName()));
    }

    @Test
    void shouldRejectStampedFileForNonSealDocument() {
        OaDocument document = submittedDocument();
        login(manager);
        assertThatThrownBy(() -> actionService.returnStampedFile(document.getId(),
                new MockMultipartFile("file", "stamped.pdf", "application/pdf", new byte[]{1})))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅用印申请");
    }

    @Test
    void shouldRequireStampedFileOnReturn() {
        OaDocument document = submittedSealDocument();
        login(manager);
        assertThatThrownBy(() -> actionService.returnStampedFile(document.getId(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("盖章文件");
    }

    @Test
    void shouldMergeBusinessAndFlowableHistory() {
        OaDocument document = submittedDocument();

        login(manager);
        actionService.reject(document.getId(),
                new ApprovalRequest("退回修改", null, "提交人", null, null, null, null));

        login(manager);
        List<ApprovalHistoryItemResponse> history = actionService.history(document.getId());
        assertThat(history).extracting(ApprovalHistoryItemResponse::source)
                .contains("BUSINESS", "FLOWABLE");
        assertThat(history).extracting(ApprovalHistoryItemResponse::nodeName).contains("直属主管");
    }

    @Test
    void shouldTransferPendingTasksOnOffboarding() {
        OaDocument document = submittedDocument();
        SysUser managerUser = userRepository.findByAccount("manager1").orElseThrow();

        int transferred = offboardingService.transferAll(managerUser.getId(), "gm1", "admin1");

        assertThat(transferred).isGreaterThanOrEqualTo(1);
        assertThat(workflowService.tasksForProcess(document.getProcessInstanceId()))
                .extracting(org.flowable.task.api.Task::getAssignee)
                .contains("gm1");
        assertThat(offboardingService.pendingCount(managerUser.getId())).isZero();
        assertThat(approvalRepository.findByDocumentIdOrderByCreatedAtAsc(document.getId()))
                .extracting(ApprovalRecord::getAction)
                .contains(ApprovalActionEnum.TRANSFER);
    }

    @Test
    void shouldRejectPendingDocumentsOnOffboarding() {
        OaDocument document = submittedDocument();
        SysUser managerUser = userRepository.findByAccount("manager1").orElseThrow();

        int rejected = offboardingService.rejectAll(managerUser.getId(), "admin1");

        assertThat(rejected).isGreaterThanOrEqualTo(1);
        OaDocument reloaded = documentRepository.findById(document.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DocumentStatusEnum.REJECTED);
        assertThat(reloaded.getCurrentNode()).isEqualTo("提交人");
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(document.getProcessInstanceId()).singleResult()).isNull();
        assertThat(approvalRepository.findByDocumentIdOrderByCreatedAtAsc(document.getId()))
                .extracting(ApprovalRecord::getAction)
                .contains(ApprovalActionEnum.REJECT);
    }

    @Test
    void shouldBlockResignUntilOffboardingCompleted() {
        OaDocument document = submittedDocument();
        SysUser managerUser = userRepository.findByAccount("manager1").orElseThrow();

        // 有在途待办：离职被阻止
        assertThatThrownBy(() -> employeeService.update(managerUser.getId(), new UpdateEmployeeRequest(
                managerUser.getName(), "M001", managerUser.getDepartmentId(), managerUser.getPostId(),
                null, UserStatusEnum.RESIGNED, null, List.of("二级部门负责人"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("在途待办");

        // 全部退回提交人后离职放行
        offboardingService.rejectAll(managerUser.getId(), "admin1");
        login(admin);
        EmployeeResponse resp = employeeService.update(managerUser.getId(), new UpdateEmployeeRequest(
                managerUser.getName(), "M001", managerUser.getDepartmentId(), managerUser.getPostId(),
                null, UserStatusEnum.RESIGNED, null, List.of("二级部门负责人")));
        assertThat(resp.status()).isEqualTo(UserStatusEnum.RESIGNED);
    }

    private OaDocument submittedDocument() {
        sequence++;
        FlowNodeConfig firstNode = config.getNodes().stream()
                .filter(node -> node.getNodeType() == FlowNodeTypeEnum.APPROVAL)
                .findFirst().orElseThrow();
        SysUser applicant = userRepository.findByAccount("manager1").orElseThrow();
        OaDocument document = new OaDocument();
        document.setDocCode("BX20260902" + String.format("%03d", sequence));
        document.setBusinessType(BusinessTypeEnum.DAILY_PAYMENT);
        document.setProjectName(config.getType());
        document.setApplicant(applicant);
        document.setCompany(CompanyEnum.HAI_XIA_JIN);
        document.setDepartment(applicant.getDepartment());
        document.setAmount(new BigDecimal("1000"));
        document.setNeedPostMaterial(false);
        document.setStatus(DocumentStatusEnum.APPROVING);
        document.setCurrentNode(firstNode.getName());
        document = documentRepository.saveAndFlush(document);
        String processInstanceId = workflowService.startProcess(config.getId(), document.getId(), java.util.Map.of());
        document.setProcessInstanceId(processInstanceId);
        return documentRepository.saveAndFlush(document);
    }

    private OaDocument submittedSealDocument() {
        sequence++;
        FlowConfig sealConfig = new FlowConfig();
        sealConfig.setType("非标合同审批及用印");
        sealConfig.setCategory(FlowCategoryEnum.SEAL);
        sealConfig.addNode(new FlowNodeConfig("发起人", FlowNodeTypeEnum.START));
        FlowNodeConfig sealNode = new FlowNodeConfig("内控专员用印", FlowNodeTypeEnum.APPROVAL);
        sealNode.setAssigneeRole("二级部门负责人");
        sealConfig.addNode(sealNode);
        flowConfigRepository.saveAndFlush(sealConfig);

        FlowNodeConfig firstNode = sealConfig.getNodes().stream()
                .filter(node -> node.getNodeType() == FlowNodeTypeEnum.APPROVAL)
                .findFirst().orElseThrow();
        SysUser applicant = userRepository.findByAccount("manager1").orElseThrow();
        OaDocument document = new OaDocument();
        document.setDocCode("YY20260902" + String.format("%03d", sequence));
        document.setBusinessType(BusinessTypeEnum.SEAL_APPLICATION);
        document.setProjectName(sealConfig.getType());
        document.setApplicant(applicant);
        document.setDepartment(applicant.getDepartment());
        document.setSealProject("经销商合同用印");
        document.setSealDepartment(applicant.getDepartment());
        document.setSealTime(java.time.LocalDateTime.of(2026, 9, 2, 10, 0));
        document.setSealFileName("经销协议.pdf");
        document.setSealType(SealTypeEnum.CONTRACT_SEAL);
        document.setSealReason("签订年度经销协议");
        document.setNeedPostMaterial(false);
        document.setStatus(DocumentStatusEnum.APPROVING);
        document.setCurrentNode(firstNode.getName());
        document = documentRepository.saveAndFlush(document);
        String processInstanceId = workflowService.startProcess(sealConfig.getId(), document.getId(), java.util.Map.of());
        document.setProcessInstanceId(processInstanceId);
        return documentRepository.saveAndFlush(document);
    }

    private Long evidenceId(OaDocument document) {
        OaAttachment attachment = new OaAttachment("evidence.png", "target/test-uploads/evidence.png", "image/png", 3L);
        document.addAttachment(attachment);
        document = documentRepository.saveAndFlush(document);
        return document.getAttachments().iterator().next().getId();
    }

    private SysUser user(String name, String account, String department, SysRole role) {
        SysUser user = new SysUser();
        user.setName(name);
        user.setAccount(account);
        user.setPassword("password");
        com.hxj.support.DictionaryTestSupport.applyDictionary(user,
                com.hxj.support.DictionaryTestSupport.ensureDepartment(departmentRepository, department),
                com.hxj.support.DictionaryTestSupport.ensurePost(postRepository, "员工"));
        if (role != null) {
            user.addRole(role);
        }
        return user;
    }

    private AuthenticatedUserResponse auth(SysUser user, List<String> roles) {
        return new AuthenticatedUserResponse(user.getId(), user.getAccount(), user.getName(),
                user.getDepartment(), user.getPost(), roles, List.of(), List.of("OWN"));
    }

    @Test
    void shouldAssignManagerLineNodeToApplicantsManager() {
        // 直属主管节点流程（钉钉式汇报线：assigneeRole=直属主管 → ${managerAccount} 动态指派）
        FlowConfig managerFlow = new FlowConfig();
        managerFlow.setType("直属主管审批流程");
        managerFlow.setCategory(FlowCategoryEnum.DAILY);
        managerFlow.addNode(new FlowNodeConfig("发起人", FlowNodeTypeEnum.START));
        FlowNodeConfig managerNode = new FlowNodeConfig("直属主管审批", FlowNodeTypeEnum.APPROVAL);
        managerNode.setAssigneeRole("直属主管");
        managerFlow.addNode(managerNode);
        flowConfigRepository.saveAndFlush(managerFlow);

        sequence++;
        SysUser applicant = userRepository.findByAccount("manager1").orElseThrow();
        OaDocument document = new OaDocument();
        document.setDocCode("BX20260903" + String.format("%03d", sequence));
        document.setBusinessType(BusinessTypeEnum.DAILY_PAYMENT);
        document.setProjectName(managerFlow.getType());
        document.setApplicant(applicant);
        document.setCompany(CompanyEnum.HAI_XIA_JIN);
        document.setDepartment(applicant.getDepartment());
        document.setAmount(new BigDecimal("500"));
        document.setNeedPostMaterial(false);
        document.setStatus(DocumentStatusEnum.APPROVING);
        document.setCurrentNode("直属主管审批");
        document = documentRepository.saveAndFlush(document);
        String pid = workflowService.startProcess(managerFlow.getId(), document.getId(),
                java.util.Map.of("initiator", "manager1", "managerAccount", "gm1"));
        document.setProcessInstanceId(pid);
        documentRepository.saveAndFlush(document);

        // 直属主管节点自动指派给申请人的汇报线主管（gm1）
        assertThat(workflowService.tasksForProcess(pid))
                .extracting(org.flowable.task.api.Task::getAssignee)
                .containsExactly("gm1");

        // 主管审批通过后流程完结归档
        login(gm);
        ApprovalResultResponse result = actionService.approve(document.getId(),
                new ApprovalRequest("同意", evidenceId(document), null, null, null, null, null));
        assertThat(result.status()).isEqualTo(DocumentStatusEnum.APPROVED);
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(pid).singleResult()).isNull();
    }

    @Test
    void shouldWithdrawByApplicantBeforeAnyApproval() {
        OaDocument document = submittedDocument();

        login(manager);
        ApprovalResultResponse result = actionService.withdraw(document.getId());

        assertThat(result.status()).isEqualTo(DocumentStatusEnum.VOIDED);
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(document.getProcessInstanceId()).singleResult()).isNull();
        assertThat(documentRepository.findById(document.getId()).orElseThrow().getCurrentNode()).isNull();
        assertThat(approvalRepository.findByDocumentIdOrderByCreatedAtAsc(document.getId()))
                .extracting(ApprovalRecord::getAction)
                .containsExactly(ApprovalActionEnum.VOID);
    }

    @Test
    void shouldRejectWithdrawByNonApplicantOrAfterApproval() {
        OaDocument document = submittedDocument();

        login(outsider);
        assertThatThrownBy(() -> actionService.withdraw(document.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅申请人本人可撤回单据");

        login(gm);
        assertThatThrownBy(() -> actionService.withdraw(document.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅申请人本人可撤回单据");

        login(manager);
        actionService.approve(document.getId(),
                new ApprovalRequest("同意", evidenceId(document), null, null, null, null, null));
        assertThatThrownBy(() -> actionService.withdraw(document.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("审批已开始，无法撤回");
    }

    @Test
    void shouldVoidByConfiguratorAndLeaveTrace() {
        OaDocument document = submittedDocument();
        login(manager);
        actionService.approve(document.getId(),
                new ApprovalRequest("同意", evidenceId(document), null, null, null, null, null));

        login(admin);
        ApprovalResultResponse result = actionService.voidDocument(document.getId(),
                new VoidDocumentRequest("重复提交，予以作废"));

        assertThat(result.status()).isEqualTo(DocumentStatusEnum.VOIDED);
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(document.getProcessInstanceId()).singleResult()).isNull();
        assertThat(approvalRepository.findByDocumentIdOrderByCreatedAtAsc(document.getId()))
                .extracting(ApprovalRecord::getAction)
                .contains(ApprovalActionEnum.APPROVE, ApprovalActionEnum.VOID);

        // 已办结单据不可再作废
        login(admin);
        assertThatThrownBy(() -> actionService.voidDocument(document.getId(),
                new VoidDocumentRequest("再次作废")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("单据已办结或已作废，不可再作废");
    }

    @Test
    void shouldRejectVoidWithoutPermissionOrReason() {
        OaDocument document = submittedDocument();

        login(manager);
        assertThatThrownBy(() -> actionService.voidDocument(document.getId(),
                new VoidDocumentRequest("尝试作废")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅管理员可作废单据");

        login(admin);
        assertThatThrownBy(() -> actionService.voidDocument(document.getId(),
                new VoidDocumentRequest("  ")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请填写作废原因");
    }
}
