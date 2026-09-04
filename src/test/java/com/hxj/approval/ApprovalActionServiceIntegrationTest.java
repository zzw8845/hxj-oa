package com.hxj.approval;

import com.hxj.entity.ApprovalRecord;
import com.hxj.entity.BusinessType;
import com.hxj.entity.Company;
import com.hxj.entity.DocumentStatus;
import com.hxj.entity.FlowCategory;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.FlowNodeType;
import com.hxj.entity.OaAttachment;
import com.hxj.entity.OaDocument;
import com.hxj.entity.SupplementMode;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.exception.BusinessException;
import com.hxj.repository.ApprovalRecordRepository;
import com.hxj.repository.ArchiveLedgerRepository;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.OaAttachmentRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysDataScopeRepository;
import com.hxj.repository.SysRoleRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.security.AuthenticatedUser;
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
    @Autowired private RuntimeService runtimeService;

    private AuthenticatedUser manager;
    private AuthenticatedUser gm;
    private AuthenticatedUser outsider;
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

        config = new FlowConfig();
        config.setType("采购申请");
        config.setCategory(FlowCategory.DAILY);
        config.addNode(new FlowNodeConfig("发起人", FlowNodeType.START));
        FlowNodeConfig managerNode = new FlowNodeConfig("直属主管", FlowNodeType.APPROVAL);
        managerNode.setAssigneeRole("二级部门负责人");
        config.addNode(managerNode);
        FlowNodeConfig gmNode = new FlowNodeConfig("执行总经理审批", FlowNodeType.APPROVAL);
        gmNode.setAssigneeRole("执行总经理");
        config.addNode(gmNode);
        config.addNode(new FlowNodeConfig("抄送财务", FlowNodeType.CC));
        flowConfigRepository.saveAndFlush(config);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContext.clear();
    }

    private void login(AuthenticatedUser user) {
        TestSecurityContext.mock(user);
    }

    @Test
    void shouldApproveThroughAllNodesAndArchive() {
        OaDocument document = submittedDocument();
        ApprovalRequest request = new ApprovalRequest("同意", evidenceId(document), null, null, null, null, null);

        login(manager);
        ApprovalResult first = actionService.approve(document.getId(), request);
        assertThat(first.status()).isEqualTo(DocumentStatus.APPROVING);
        assertThat(first.currentNode()).isEqualTo("执行总经理审批");

        login(gm);
        ApprovalResult second = actionService.approve(document.getId(), request);
        assertThat(second.status()).isEqualTo(DocumentStatus.APPROVED);
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
        ApprovalResult result = actionService.reject(document.getId(),
                new ApprovalRequest("金额有误，退回修改", null, "直属主管", "请补充报价单", null, null, null));

        assertThat(result.status()).isEqualTo(DocumentStatus.REJECTED);
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
        ApprovalResult result = actionService.reject(document.getId(),
                new ApprovalRequest("材料不齐，退回发起人", null, "提交人", null, null, null, null));

        assertThat(result.status()).isEqualTo(DocumentStatus.REJECTED);
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
        ApprovalResult supplemented = actionService.supplement(document.getId(),
                new ApprovalRequest("付款后再补合同原件", evidenceId(document), null, null,
                        SupplementMode.AFTER_PAY, "核算会计", "合同原件"));
        // 付款后补充：流程继续但未补齐材料前不可闭环
        assertThat(supplemented.status()).isEqualTo(DocumentStatus.SUPPLEMENT_REQUIRED);
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(document.getProcessInstanceId()).singleResult()).isNull();

        ApprovalResult result = actionService.submitSupplementMaterials(document.getId(),
                List.of(new MockMultipartFile("files", "contract.pdf", "application/pdf", new byte[]{1, 2, 3})));
        assertThat(result.status()).isEqualTo(DocumentStatus.APPROVED);
        assertThat(result.archived()).isTrue();
    }

    @Test
    void shouldPauseAndResumeBeforePaySupplement() {
        OaDocument document = submittedDocument();

        login(manager);
        actionService.supplement(document.getId(),
                new ApprovalRequest("付款前补发票", evidenceId(document), null, null,
                        SupplementMode.BEFORE_PAY, "提交人", "增值税发票"));
        assertThat(documentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.SUPPLEMENT_REQUIRED);
        assertThat(workflowService.tasksForProcess(document.getProcessInstanceId())).hasSize(1);

        login(manager);
        ApprovalResult result = actionService.submitSupplementMaterials(document.getId(),
                List.of(new MockMultipartFile("files", "invoice.pdf", "application/pdf", new byte[]{4})));
        // 付款前补充完成：跳过当前审批人进入下一节点
        assertThat(result.status()).isEqualTo(DocumentStatus.APPROVING);
        assertThat(result.currentNode()).isEqualTo("执行总经理审批");
    }

    @Test
    void shouldDelegateTaskToSignUserAndReturnAfterComment() {
        OaDocument document = submittedDocument();
        SysUser signer = userRepository.findByAccount("signer1").orElseThrow();

        login(manager);
        ApprovalResult signed = actionService.sign(document.getId(),
                new SignRequest(signer.getId(), "请协助核对金额"));
        assertThat(signed.status()).isEqualTo(DocumentStatus.APPROVING);
        assertThat(workflowService.tasksForProcess(document.getProcessInstanceId()))
                .extracting(org.flowable.task.api.Task::getAssignee)
                .containsExactly("signer1");

        login(auth(signer, List.of("二级部门负责人")));
        ApprovalResult commented = actionService.signComment(
                document.getId(), "金额核对无误");
        assertThat(commented.status()).isEqualTo(DocumentStatus.APPROVING);
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
        ApprovalResult approved = actionService.approve(document.getId(), request);
        assertThat(approved.status()).isEqualTo(DocumentStatus.SUPPLEMENT_REQUIRED);
        assertThat(approved.archived()).isFalse();
        assertThat(archiveLedgerRepository.findByDocumentId(document.getId())).isEmpty();

        // 回传盖章文件后归档完结
        ApprovalResult returned = actionService.returnStampedFile(document.getId(),
                new MockMultipartFile("file", "stamped.pdf", "application/pdf", new byte[]{9, 9}));
        assertThat(returned.status()).isEqualTo(DocumentStatus.APPROVED);
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
        List<ApprovalHistoryItem> history = actionService.history(document.getId());
        assertThat(history).extracting(ApprovalHistoryItem::source)
                .contains("BUSINESS", "FLOWABLE");
        assertThat(history).extracting(ApprovalHistoryItem::nodeName).contains("直属主管");
    }

    private OaDocument submittedDocument() {
        sequence++;
        FlowNodeConfig firstNode = config.getNodes().stream()
                .filter(node -> node.getNodeType() == FlowNodeType.APPROVAL)
                .findFirst().orElseThrow();
        SysUser applicant = userRepository.findByAccount("manager1").orElseThrow();
        OaDocument document = new OaDocument();
        document.setDocCode("BX20260902" + String.format("%03d", sequence));
        document.setBusinessType(BusinessType.DAILY_PAYMENT);
        document.setProjectName(config.getType());
        document.setApplicant(applicant);
        document.setCompany(Company.HAI_XIA_JIN);
        document.setDepartment(applicant.getDepartment());
        document.setAmount(new BigDecimal("1000"));
        document.setNeedPostMaterial(false);
        document.setStatus(DocumentStatus.APPROVING);
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
        sealConfig.setCategory(FlowCategory.SEAL);
        sealConfig.addNode(new FlowNodeConfig("发起人", FlowNodeType.START));
        FlowNodeConfig sealNode = new FlowNodeConfig("内控专员用印", FlowNodeType.APPROVAL);
        sealNode.setAssigneeRole("二级部门负责人");
        sealConfig.addNode(sealNode);
        flowConfigRepository.saveAndFlush(sealConfig);

        FlowNodeConfig firstNode = sealConfig.getNodes().stream()
                .filter(node -> node.getNodeType() == FlowNodeType.APPROVAL)
                .findFirst().orElseThrow();
        SysUser applicant = userRepository.findByAccount("manager1").orElseThrow();
        OaDocument document = new OaDocument();
        document.setDocCode("YY20260902" + String.format("%03d", sequence));
        document.setBusinessType(BusinessType.SEAL_APPLICATION);
        document.setProjectName(sealConfig.getType());
        document.setApplicant(applicant);
        document.setDepartment(applicant.getDepartment());
        document.setSealProject("经销商合同用印");
        document.setSealDepartment(applicant.getDepartment());
        document.setSealTime(java.time.LocalDateTime.of(2026, 9, 2, 10, 0));
        document.setSealFileName("经销协议.pdf");
        document.setSealType(com.hxj.entity.SealType.CONTRACT_SEAL);
        document.setSealReason("签订年度经销协议");
        document.setNeedPostMaterial(false);
        document.setStatus(DocumentStatus.APPROVING);
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
        user.setDepartment(department);
        if (role != null) {
            user.addRole(role);
        }
        return user;
    }

    private AuthenticatedUser auth(SysUser user, List<String> roles) {
        return new AuthenticatedUser(user.getId(), user.getAccount(), user.getName(),
                user.getDepartment(), user.getPost(), roles, List.of(), List.of("OWN_DOCUMENTS"));
    }
}
