package com.hxj.document;

import com.hxj.entity.*;
import com.hxj.enums.*;
import com.hxj.repository.CcRecordRepository;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.OaAttachmentRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.security.AuthenticatedUserResponse;
import com.hxj.security.DocumentAccessPolicy;
import com.hxj.security.TestSecurityContext;
import com.hxj.service.DocumentCodeGenerator;
import com.hxj.service.DocumentSequenceAllocator;
import com.hxj.workflow.WorkflowHistoryItemResponse;
import com.hxj.workflow.WorkflowNodeStatResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.upload-dir=${java.io.tmpdir}/oa-document-test",
        "app.risk-threshold=80000"
})
@Import({
        DocumentApplicationService.class,
        DocumentAccessPolicy.class,
        LocalAttachmentStorage.class,
        com.hxj.workflow.DeptAccountantResolver.class,
        FormTemplateManagementService.class,
        DocumentApplicationServiceTest.Config.class
})
class DocumentApplicationServiceTest {

    @Autowired private DocumentApplicationService service;
    @Autowired private FormTemplateManagementService templateService;
    @Autowired private OaDocumentRepository documentRepository;
    @Autowired private OaAttachmentRepository attachmentRepository;
    @Autowired private CcRecordRepository ccRepository;
    @Autowired private SysUserRepository userRepository;
    @Autowired private com.hxj.repository.SysDepartmentRepository departmentRepository;
    @Autowired private com.hxj.repository.SysPostRepository postRepository;
    @Autowired private FlowConfigRepository flowConfigRepository;
    @Autowired private FakeWorkflowPort workflow;

    private SysUser applicant;
    private SysUser ccUser;
    private AuthenticatedUserResponse principal;
    private Long paymentTemplateId;
    private Long sealTemplateId;

    @BeforeEach
    void setUp() {
        applicant = saveUser("applicant", "业务部");
        ccUser = saveUser("cc-user", "财务部");
        principal = new AuthenticatedUserResponse(
                applicant.getId(), applicant.getAccount(), applicant.getName(), applicant.getDepartment(),
                applicant.getPost(), List.of(), List.of("VIEW_OWN_FORMS"), List.of("OWN"));

        FlowConfig config = new FlowConfig();
        config.setType("合作方退款");
        config.setCategory(FlowCategoryEnum.BUSINESS);
        config.addNode(new FlowNodeConfig("发起人", FlowNodeTypeEnum.START));
        config.addNode(new FlowNodeConfig("直属主管", FlowNodeTypeEnum.APPROVAL));
        flowConfigRepository.save(config);

        FlowConfig sealConfig = new FlowConfig();
        sealConfig.setType("非标合同审批及用印");
        sealConfig.setCategory(FlowCategoryEnum.SEAL);
        sealConfig.addNode(new FlowNodeConfig("发起人", FlowNodeTypeEnum.START));
        sealConfig.addNode(new FlowNodeConfig("直属主管", FlowNodeTypeEnum.APPROVAL));
        sealConfig.addNode(new FlowNodeConfig("内控专员用印", FlowNodeTypeEnum.HANDLER));
        flowConfigRepository.save(sealConfig);

        paymentTemplateId = templateService.create(new SaveFormTemplateRequest(
                "合作方退款", "合作方退款", "FK", config.getId(), "BUSINESS_PAYMENT",
                List.of("关联前置单据", "业务证明资料", "发票", "收款信息"),
                List.of(
                        new SaveFormTemplateRequest.FieldPayload("title", "单据标题", "TEXT", true, null, false, 1, true),
                        new SaveFormTemplateRequest.FieldPayload("company", "所属公司", "SELECT", true, List.of("海峡金", "海峡金供应链"), false, 2, true),
                        new SaveFormTemplateRequest.FieldPayload("amount", "金额", "NUMBER", true, null, true, 3, true),
                        new SaveFormTemplateRequest.FieldPayload("invoiceSummary", "发票摘要", "TEXT", false, null, false, 4, true),
                        new SaveFormTemplateRequest.FieldPayload("reason", "事由明细", "TEXTAREA", true, null, false, 5, true),
                        new SaveFormTemplateRequest.FieldPayload("contractNo", "合同编号", "TEXT", false, null, false, 6, true),
                        new SaveFormTemplateRequest.FieldPayload("involvesFunds", "是否涉及资金", "BOOLEAN", false, null, true, 7, true),
                        new SaveFormTemplateRequest.FieldPayload("requiresAdminReview", "是否需行政复核", "BOOLEAN", false, null, true, 8, true),
                        new SaveFormTemplateRequest.FieldPayload("needPostMaterial", "是否后置补材料", "BOOLEAN", false, null, true, 9, true)))).id();

        sealTemplateId = templateService.create(new SaveFormTemplateRequest(
                "非标合同审批及用印", "非标合同审批及用印", "YY", sealConfig.getId(), "SEAL_APPLICATION",
                List.of("用印文件附件"),
                List.of(
                        new SaveFormTemplateRequest.FieldPayload("title", "单据标题", "TEXT", true, null, false, 1, true),
                        new SaveFormTemplateRequest.FieldPayload("sealProject", "用印项目", "TEXT", true, null, false, 2, true),
                        new SaveFormTemplateRequest.FieldPayload("sealType", "印章类型", "SELECT", true, List.of("公章", "合同章", "法人章", "财务章"), false, 3, true),
                        new SaveFormTemplateRequest.FieldPayload("sealDepartment", "用印部门", "TEXT", true, null, false, 4, true),
                        new SaveFormTemplateRequest.FieldPayload("sealTime", "用印时间", "DATE", true, null, false, 5, true),
                        new SaveFormTemplateRequest.FieldPayload("sealFileName", "用印文件名", "TEXT", true, null, false, 6, true),
                        new SaveFormTemplateRequest.FieldPayload("sealReason", "用印事由", "TEXTAREA", true, null, false, 7, true)))).id();
    }

    @AfterEach
    void tearDown() {
        TestSecurityContext.clear();
    }

    @Test
    void shouldSubmitStartWorkflowCreateCcAndMarkRisk() {
        TestSecurityContext.mock(principal);
        DocumentSummaryResponse result = service.submit(paymentRequest(null, List.of(ccUser.getId())));

        assertThat(result.docCode()).startsWith("FK20260829");
        assertThat(result.businessType()).isEqualTo(BusinessTypeEnum.BUSINESS_PAYMENT);
        assertThat(result.status()).isEqualTo(DocumentStatusEnum.PENDING);
        assertThat(result.currentNode()).isEqualTo("直属主管");
        assertThat(result.riskFlag()).isTrue();
        assertThat(documentRepository.findById(result.id()).orElseThrow().getProcessInstanceId())
                .isEqualTo("process-" + result.id());
        assertThat(workflow.startedVariables).containsEntry("amount", new BigDecimal("90000"));
        assertThat(workflow.startedVariables).containsEntry("involvesFunds", true);
        assertThat(ccRepository.findByDocumentIdOrderByCreatedAtAsc(result.id()))
                .extracting(record -> record.getTargetUser().getAccount())
                .containsExactly("cc-user");
    }

    @Test
    void shouldFreezeFormSnapshotAndMergeFieldsInDetail() {
        TestSecurityContext.mock(principal);
        DocumentSummaryResponse result = service.submit(paymentRequest(null, List.of()));

        com.hxj.entity.OaDocument entity = documentRepository.findById(result.id()).orElseThrow();
        assertThat(entity.getFormVersion()).isEqualTo(1);
        assertThat(entity.getFormSnapshot()).contains("单据标题");
        assertThat(entity.getFieldValues()).contains("合作方退款");

        DocumentDetailResponse detail = service.detail(result.id());
        assertThat(detail.formFields()).isNotEmpty();
        assertThat(detail.formFields())
                .extracting(FormTemplateManagementService.FieldValueView::fieldKey)
                .contains("title", "amount");
        assertThat(detail.formFields())
                .filteredOn(f -> f.fieldKey().equals("amount"))
                .allSatisfy(f -> assertThat(String.valueOf(f.value())).isEqualTo("90000"));
    }

    @Test
    void shouldSearchDetailLinkRepeatQuickAndHandleAttachment() throws Exception {
        TestSecurityContext.mock(principal);
        DocumentSummaryResponse source = service.submit(paymentRequest(null, List.of()));
        com.hxj.entity.OaDocument sourceEntity = documentRepository.findById(source.id()).orElseThrow();
        sourceEntity.setStatus(DocumentStatusEnum.APPROVED);
        documentRepository.flush();

        assertThat(service.search(
                new DocumentSearchCondition("合作", DocumentStatusEnum.APPROVED,
                        BusinessTypeEnum.BUSINESS_PAYMENT, null, applicant.getId())))
                .extracting(DocumentSummaryResponse::id).containsExactly(source.id());
        assertThat(service.detail(source.id()).projectName()).isEqualTo("合作方退款");
        assertThat(service.findLinkCandidates("HT-001", true))
                .extracting(DocumentSummaryResponse::id).containsExactly(source.id());
        assertThat(service.findLinkCandidates("applicant", false))
                .extracting(DocumentSummaryResponse::id).containsExactly(source.id());
        assertThat(service.quickDocuments())
                .extracting(QuickDocumentItemResponse::name).contains("合作方退款");

        DocumentSummaryResponse repeated = service.repeat(source.id());
        assertThat(repeated.id()).isNotEqualTo(source.id());
        assertThat(repeated.docCode()).isNotEqualTo(source.docCode());

        MockMultipartFile file = new MockMultipartFile(
                "file", "proof.txt", "text/plain", "proof".getBytes());
        DocumentDetailResponse.Attachment uploaded = service.upload(source.id(), "发起人", file);
        assertThat(uploaded.fileName()).isEqualTo("proof.txt");
        assertThat(service.download(uploaded.id()).resource().getInputStream().readAllBytes())
                .isEqualTo("proof".getBytes());
        assertThat(service.attachmentRequirements(paymentTemplateId))
                .contains("发票", "收款信息");
        assertThat(attachmentRepository.findById(uploaded.id())).isPresent();
    }

    @Test
    void shouldSubmitSealApplicationWithoutAmountAndIndependentFlow() {
        TestSecurityContext.mock(principal);
        DocumentSummaryResponse result = service.submit(sealRequest(null));

        assertThat(result.docCode()).startsWith("YY20260829");
        assertThat(result.businessType()).isEqualTo(BusinessTypeEnum.SEAL_APPLICATION);
        assertThat(result.documentType()).isEqualTo(DocumentTypeEnum.SEAL_APPLICATION);
        assertThat(result.amount()).isNull();
        assertThat(result.currentNode()).isEqualTo("直属主管");
        assertThat(workflow.startedVariables).containsEntry("amount", BigDecimal.ZERO);

        com.hxj.entity.OaDocument entity = documentRepository.findById(result.id()).orElseThrow();
        assertThat(entity.getFieldValues()).contains("经销商合同用印");
        assertThat(service.detail(result.id()).formFields())
                .filteredOn(f -> f.fieldKey().equals("sealProject"))
                .allSatisfy(f -> assertThat(f.value()).isEqualTo("经销商合同用印"));
    }

    @Test
    void shouldSearchPagedWithPageAndSize() {
        TestSecurityContext.mock(principal);
        service.submit(paymentRequest(null, List.of()));
        service.submit(paymentRequest(null, List.of()));
        service.submit(paymentRequest(null, List.of()));

        DocumentPageRequest request1 = new DocumentPageRequest(null, null, null, null, null, null, 1, 2);
        com.hxj.common.PageResponse<DocumentSummaryResponse> page1 = service.searchPaged(request1);
        assertThat(page1.content()).hasSize(2);
        assertThat(page1.totalElements()).isEqualTo(3);
        assertThat(page1.totalPages()).isEqualTo(2);

        DocumentPageRequest request2 = new DocumentPageRequest(null, null, null, null, null, null, 2, 2);
        com.hxj.common.PageResponse<DocumentSummaryResponse> page2 = service.searchPaged(request2);
        assertThat(page2.content()).hasSize(1);
        assertThat(page2.page()).isEqualTo(2);
    }

    @Test
    void shouldRejectRequiredFieldMissing() {
        TestSecurityContext.mock(principal);
        Map<String, Object> incomplete = new java.util.HashMap<>(Map.of(
                "title", "非标合同审批及用印", "sealType", "合同章", "sealTime", "2026-09-01",
                "sealFileName", "经销协议.pdf", "sealReason", "签订年度经销协议"));
        incomplete.remove("sealProject");

        assertThatThrownBy(() -> service.submit(
                new SubmitDocumentRequest(sealTemplateId, incomplete, List.of(), null)))
                .isInstanceOf(com.hxj.exception.BusinessException.class)
                .hasMessageContaining("必填字段未填写：用印项目");
    }

    @Test
    void shouldRejectUnknownInvalidAndOptionIllegalFieldValues() {
        TestSecurityContext.mock(principal);

        assertThatThrownBy(() -> service.submit(new SubmitDocumentRequest(
                paymentTemplateId, Map.of("title", "t", "company", "海峡金", "amount", 1, "reason", "r",
                "unknownKey", "x"), List.of(), null)))
                .hasMessageContaining("未知表单字段：unknownKey");

        assertThatThrownBy(() -> service.submit(new SubmitDocumentRequest(
                paymentTemplateId, Map.of("title", "t", "company", "海峡金", "amount", "abc", "reason", "r"),
                List.of(), null)))
                .hasMessageContaining("字段必须为数字");

        assertThatThrownBy(() -> service.submit(new SubmitDocumentRequest(
                paymentTemplateId, Map.of("title", "t", "company", "外星公司", "amount", 1, "reason", "r"),
                List.of(), null)))
                .hasMessageContaining("选项非法：所属公司");
    }

    private SubmitDocumentRequest sealRequest(Long linkedId) {
        return new SubmitDocumentRequest(sealTemplateId, Map.of(
                "title", "非标合同审批及用印",
                "sealProject", "经销商合同用印",
                "sealType", "合同章",
                "sealDepartment", "业务部",
                "sealTime", "2026-09-01",
                "sealFileName", "经销协议.pdf",
                "sealReason", "签订年度经销协议"), List.of(), linkedId);
    }

    private SubmitDocumentRequest paymentRequest(Long linkedId, List<Long> ccIds) {
        return new SubmitDocumentRequest(paymentTemplateId, Map.of(
                "title", "合作方退款",
                "company", "海峡金",
                "amount", 90000,
                "invoiceSummary", "专票1张",
                "reason", "合作方退款",
                "contractNo", "HT-001",
                "involvesFunds", true), ccIds, linkedId);
    }

    private SysUser saveUser(String account, String department) {
        SysUser user = new SysUser();
        user.setName(account);
        user.setJobNo("JOB-" + account);
        user.setAccount(account);
        user.setPassword("encoded");
        com.hxj.support.DictionaryTestSupport.applyDictionary(user,
                com.hxj.support.DictionaryTestSupport.ensureDepartment(departmentRepository, department),
                com.hxj.support.DictionaryTestSupport.ensurePost(postRepository, "员工"));
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
        @Override public List<Task> allActiveTasks() { return List.of(); }
        @Override public List<Task> delegatedTasks() { return List.of(); }
        @Override public void moveTaskToActivity(String processInstanceId, String taskId, String targetActivityId) {}
        @Override public void endProcess(String processInstanceId, String reason) {}
        @Override public void setAssignee(String taskId, String account) {}
        @Override public void delegateTask(String taskId, String account) {}
        @Override public void resolveTask(String taskId) {}
        @Override public List<WorkflowHistoryItemResponse> history(String processInstanceId) { return List.of(); }
        @Override public List<WorkflowNodeStatResponse> nodeStatistics() { return List.of(); }
    }
}
