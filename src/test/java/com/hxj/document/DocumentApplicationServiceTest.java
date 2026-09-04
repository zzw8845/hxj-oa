package com.hxj.document;

import com.hxj.entity.BusinessType;
import com.hxj.entity.Company;
import com.hxj.entity.DocumentStatus;
import com.hxj.entity.FlowCategory;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.FlowNodeType;
import com.hxj.entity.QuickDocument;
import com.hxj.entity.SysUser;
import com.hxj.repository.CcRecordRepository;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.OaAttachmentRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.QuickDocumentRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.security.AuthenticatedUser;
import com.hxj.security.DocumentAccessPolicy;
import com.hxj.security.TestSecurityContext;
import com.hxj.service.DocumentCodeGenerator;
import com.hxj.service.DocumentSequenceAllocator;
import com.hxj.workflow.WorkflowHistoryItem;
import com.hxj.workflow.WorkflowNodeStat;
import com.hxj.workflow.WorkflowPort;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.upload-dir=${java.io.tmpdir}/oa-document-test",
        "app.risk-threshold=80000"
})
@Import({
        DocumentApplicationService.class,
        DocumentClassifier.class,
        DocumentAccessPolicy.class,
        AttachmentRequirementService.class,
        LocalAttachmentStorage.class,
        DocumentApplicationServiceTest.Config.class
})
class DocumentApplicationServiceTest {

    @Autowired private DocumentApplicationService service;
    @Autowired private OaDocumentRepository documentRepository;
    @Autowired private OaAttachmentRepository attachmentRepository;
    @Autowired private CcRecordRepository ccRepository;
    @Autowired private QuickDocumentRepository quickRepository;
    @Autowired private SysUserRepository userRepository;
    @Autowired private FlowConfigRepository flowConfigRepository;
    @Autowired private FakeWorkflowPort workflow;

    private SysUser applicant;
    private SysUser ccUser;
    private AuthenticatedUser principal;

    @BeforeEach
    void setUp() {
        applicant = saveUser("applicant", "业务部");
        ccUser = saveUser("cc-user", "财务部");
        principal = new AuthenticatedUser(
                applicant.getId(), applicant.getAccount(), applicant.getName(), applicant.getDepartment(),
                applicant.getPost(), List.of(), List.of("VIEW_OWN_FORMS"), List.of("OWN_DOCUMENTS"));

        FlowConfig config = new FlowConfig();
        config.setType("合作方退款");
        config.setCategory(FlowCategory.BUSINESS);
        config.addNode(new FlowNodeConfig("发起人", FlowNodeType.START));
        config.addNode(new FlowNodeConfig("直属主管", FlowNodeType.APPROVAL));
        flowConfigRepository.save(config);

        FlowConfig sealConfig = new FlowConfig();
        sealConfig.setType("非标合同审批及用印");
        sealConfig.setCategory(FlowCategory.SEAL);
        sealConfig.addNode(new FlowNodeConfig("发起人", FlowNodeType.START));
        sealConfig.addNode(new FlowNodeConfig("直属主管", FlowNodeType.APPROVAL));
        sealConfig.addNode(new FlowNodeConfig("内控专员用印", FlowNodeType.HANDLER));
        flowConfigRepository.save(sealConfig);

        QuickDocument quick = new QuickDocument();
        quick.setBusinessType(BusinessType.BUSINESS_PAYMENT);
        quick.setName("合作方退款");
        quick.setSortOrder(1);
        quickRepository.save(quick);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContext.clear();
    }

    @Test
    void shouldSubmitClassifyStartWorkflowCreateCcAndMarkRisk() {
        TestSecurityContext.mock(principal);
        DocumentSummary result = service.submit(paymentRequest(null, List.of(ccUser.getId())));

        assertThat(result.docCode()).startsWith("FK20260829");
        assertThat(result.businessType()).isEqualTo(BusinessType.BUSINESS_PAYMENT);
        assertThat(result.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(result.currentNode()).isEqualTo("直属主管");
        assertThat(result.riskFlag()).isTrue();
        assertThat(documentRepository.findById(result.id()).orElseThrow().getProcessInstanceId())
                .isEqualTo("process-" + result.id());
        assertThat(workflow.startedVariables).containsEntry("amount", new BigDecimal("90000"));
        assertThat(ccRepository.findByDocumentIdOrderByCreatedAtAsc(result.id()))
                .extracting(record -> record.getTargetUser().getAccount())
                .containsExactly("cc-user");
    }

    @Test
    void shouldSearchDetailLinkRepeatQuickAndHandleAttachment() throws Exception {
        TestSecurityContext.mock(principal);
        DocumentSummary source = service.submit(paymentRequest(null, List.of()));
        com.hxj.entity.OaDocument sourceEntity = documentRepository.findById(source.id()).orElseThrow();
        sourceEntity.setStatus(DocumentStatus.APPROVED);
        sourceEntity.setContractNo("HT-001");
        documentRepository.flush();

        assertThat(service.search(
                new DocumentSearchCriteria("合作", DocumentStatus.APPROVED,
                        BusinessType.BUSINESS_PAYMENT, null, applicant.getId())))
                .extracting(DocumentSummary::id).containsExactly(source.id());
        assertThat(service.detail(source.id()).projectName()).isEqualTo("合作方退款");
        assertThat(service.findLinkCandidates("HT-001", true))
                .extracting(DocumentSummary::id).containsExactly(source.id());
        assertThat(service.findLinkCandidates("applicant", false))
                .extracting(DocumentSummary::id).containsExactly(source.id());
        assertThat(service.quickDocuments(BusinessType.BUSINESS_PAYMENT))
                .extracting(QuickDocumentItem::name).containsExactly("合作方退款");

        DocumentSummary repeated = service.repeat(source.id());
        assertThat(repeated.id()).isNotEqualTo(source.id());
        assertThat(repeated.docCode()).isNotEqualTo(source.docCode());

        MockMultipartFile file = new MockMultipartFile(
                "file", "proof.txt", "text/plain", "proof".getBytes());
        DocumentDetail.AttachmentItem uploaded = service.upload(source.id(), "发起人", file);
        assertThat(uploaded.fileName()).isEqualTo("proof.txt");
        assertThat(service.download(uploaded.id()).resource().getInputStream().readAllBytes())
                .isEqualTo("proof".getBytes());
        assertThat(service.attachmentRequirements(BusinessType.BUSINESS_PAYMENT, "供应商货款"))
                .contains("原始明细账单", "对账单", "发票");
        assertThat(attachmentRepository.findById(uploaded.id())).isPresent();
    }

    @Test
    void shouldSubmitSealApplicationWithoutAmountAndIndependentFlow() {
        TestSecurityContext.mock(principal);
        DocumentSummary result = service.submit(sealRequest(null));

        assertThat(result.docCode()).startsWith("YY20260829");
        assertThat(result.businessType()).isEqualTo(BusinessType.SEAL_APPLICATION);
        assertThat(result.documentType()).isEqualTo(com.hxj.entity.DocumentType.SEAL_APPLICATION);
        assertThat(result.amount()).isNull();
        assertThat(result.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(result.currentNode()).isEqualTo("直属主管");

        com.hxj.entity.OaDocument entity = documentRepository.findById(result.id()).orElseThrow();
        assertThat(entity.getAmount()).isNull();
        assertThat(entity.getSealProject()).isEqualTo("经销商合同用印");
        assertThat(entity.getSealDepartment()).isEqualTo("业务部");
        assertThat(entity.getSealFileName()).isEqualTo("经销协议.pdf");
        assertThat(entity.getSealType()).isEqualTo(com.hxj.entity.SealType.CONTRACT_SEAL);
        assertThat(entity.getSealReason()).isEqualTo("签订年度经销协议");
        assertThat(workflow.startedVariables).containsEntry("amount", BigDecimal.ZERO);
    }

    @Test
    void shouldSearchPagedWithPageAndSize() {
        TestSecurityContext.mock(principal);
        service.submit(paymentRequest(null, List.of()));
        service.submit(paymentRequest(null, List.of()));
        service.submit(paymentRequest(null, List.of()));

        DocumentPageRequest request1 = new DocumentPageRequest(null, null, null, null, null, 1, 2);
        com.hxj.common.PageResponse<DocumentSummary> page1 = service.searchPaged(request1);
        assertThat(page1.content()).hasSize(2);
        assertThat(page1.totalElements()).isEqualTo(3);
        assertThat(page1.totalPages()).isEqualTo(2);
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.size()).isEqualTo(2);

        DocumentPageRequest request2 = new DocumentPageRequest(null, null, null, null, null, 2, 2);
        com.hxj.common.PageResponse<DocumentSummary> page2 = service.searchPaged(request2);
        assertThat(page2.content()).hasSize(1);
        assertThat(page2.page()).isEqualTo(2);
    }

    @Test
    void shouldRejectIncompleteSealApplication() {
        TestSecurityContext.mock(principal);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.submit(new SubmitDocumentRequest(
                                BusinessType.SEAL_APPLICATION, "非标合同审批及用印", null, null,
                                null, null, false, null, null, false, false, null,
                                null, "业务部", null, null, null, null, List.of())))
                .isInstanceOf(com.hxj.exception.BusinessException.class)
                .hasMessageContaining("请完整填写用印申请信息");
    }

    private SubmitDocumentRequest sealRequest(Long linkedId) {
        return new SubmitDocumentRequest(
                BusinessType.SEAL_APPLICATION, "非标合同审批及用印", null, null,
                null, null, false, null, linkedId, false, false, null,
                "经销商合同用印", "业务部", java.time.LocalDateTime.of(2026, 9, 1, 10, 0),
                "经销协议.pdf", com.hxj.entity.SealType.CONTRACT_SEAL, "签订年度经销协议", List.of());
    }

    private SubmitDocumentRequest paymentRequest(Long linkedId, List<Long> ccIds) {
        return new SubmitDocumentRequest(
                BusinessType.DAILY_PAYMENT, "合作方退款", Company.HAI_XIA_JIN,
                new BigDecimal("90000"), "专票1张", "合作方退款", false,
                "HT-001", linkedId, true, false, null,
                null, null, null, null, null, null, ccIds);
    }

    private SysUser saveUser(String account, String department) {
        SysUser user = new SysUser();
        user.setName(account);
        user.setJobNo("JOB-" + account);
        user.setAccount(account);
        user.setPassword("encoded");
        user.setDepartment(department);
        user.setPost("员工");
        return userRepository.save(user);
    }

    @TestConfiguration
    static class Config {
        @Bean
        DocumentCodeGenerator documentCodeGenerator() {
            AtomicLong sequence = new AtomicLong();
            DocumentSequenceAllocator allocator = date -> sequence.incrementAndGet();
            return new DocumentCodeGenerator(allocator,
                    Clock.fixed(Instant.parse("2026-08-29T08:00:00Z"), ZoneId.of("Asia/Shanghai")));
        }

        @Bean
        FakeWorkflowPort workflowPort() {
            return new FakeWorkflowPort();
        }
    }

    static class FakeWorkflowPort implements WorkflowPort {
        Map<String, Object> startedVariables;
        @Override public String startProcess(Long configId, Long documentId, Map<String, Object> variables) {
            startedVariables = variables;
            return "process-" + documentId;
        }
        @Override public void completeTask(String taskId, Map<String, Object> variables) {}
        @Override public List<Task> pendingTasksForUser(String account, List<String> roleNames) { return List.of(); }
        @Override public List<Task> tasksForProcess(String processInstanceId) { return List.of(); }
        @Override public void moveTaskToActivity(String processInstanceId, String taskId, String targetActivityId) {}
        @Override public void endProcess(String processInstanceId, String reason) {}
        @Override public void setAssignee(String taskId, String account) {}
        @Override public void delegateTask(String taskId, String account) {}
        @Override public void resolveTask(String taskId) {}
        @Override public List<WorkflowHistoryItem> history(String processInstanceId) { return List.of(); }
        @Override public List<WorkflowNodeStat> nodeStatistics() { return List.of(); }
    }
}
