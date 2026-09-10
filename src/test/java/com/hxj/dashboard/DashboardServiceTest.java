package com.hxj.dashboard;

import com.hxj.enums.ApprovalActionEnum;
import com.hxj.entity.ApprovalRecord;
import com.hxj.enums.BusinessTypeEnum;
import com.hxj.enums.CompanyEnum;
import com.hxj.enums.DocumentStatusEnum;
import com.hxj.entity.OaDocument;
import com.hxj.entity.SysUser;
import com.hxj.repository.ApprovalRecordRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.security.AuthenticatedUserResponse;
import com.hxj.security.DocumentAccessPolicy;
import com.hxj.security.TestSecurityContext;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 8.1–8.5 统计看板与风险预警。 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({DashboardService.class, DocumentAccessPolicy.class, DashboardServiceTest.Config.class})
class DashboardServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 当前日期（测试时间戳均基于它动态计算，避免跨天失败）。 */
    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    @Autowired private DashboardService dashboardService;
    @Autowired private OaDocumentRepository documentRepository;
    @Autowired private ApprovalRecordRepository approvalRepository;
    @Autowired private SysUserRepository userRepository;
    @Autowired private com.hxj.repository.SysDepartmentRepository departmentRepository;
    @Autowired private com.hxj.repository.SysPostRepository postRepository;
    @Autowired private FakeWorkflowPort workflowPort;

    private SysUser applicant;
    private AuthenticatedUserResponse principal;

    @BeforeEach
    void setUp() {
        approvalRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
        workflowPort.reset();

        applicant = new SysUser();
        applicant.setName("张三");
        applicant.setJobNo("JOB-zhangsan");
        applicant.setAccount("zhangsan");
        applicant.setPassword("encoded");
        com.hxj.support.DictionaryTestSupport.applyDictionary(applicant,
                com.hxj.support.DictionaryTestSupport.ensureDepartment(departmentRepository, "财务部"),
                com.hxj.support.DictionaryTestSupport.ensurePost(postRepository, "员工"));
        applicant = userRepository.save(applicant);
        principal = new AuthenticatedUserResponse(applicant.getId(), applicant.getAccount(), applicant.getName(),
                applicant.getDepartment(), applicant.getPost(), List.of(), List.of(), List.of("OWN"));
    }

    @AfterEach
    void tearDown() {
        TestSecurityContext.clear();
    }

    @Test
    void shouldReturnHomeStatsWithMomComplianceAndBadge() {
        TestSecurityContext.mock(principal);
        OaDocument rejected = document("BX202609010001", DocumentStatusEnum.REJECTED, new BigDecimal("100"));
        OaDocument compliant = document("BX202609010002", DocumentStatusEnum.APPROVED, new BigDecimal("200"));
        OaDocument nonCompliant = document("BX202609010003", DocumentStatusEnum.APPROVED, new BigDecimal("90000"));
        approvalRepository.save(record(nonCompliant, ApprovalActionEnum.REJECT));
        workflowPort.pendingTaskIds = List.of(compliant.getId());

        DashboardViews.Home stats = dashboardService.homeStats();

        assertThat(stats.rejectedCount()).isEqualTo(1);
        assertThat(stats.pendingMyApprovalCount()).isEqualTo(1);
        assertThat(stats.monthlyCompletedCount()).isEqualTo(2);
        assertThat(stats.complianceRatePercent()).isEqualTo(50.0);
        assertThat(stats.badge().pendingApprovalCount()).isEqualTo(1);
        assertThat(stats.badge().riskCount()).isEqualTo(1);
    }

    @Test
    void shouldReturnTodoListSortedByNearestTimeout() {
        TestSecurityContext.mock(principal);
        OaDocument urgent = document("BX202609010001", DocumentStatusEnum.APPROVING, new BigDecimal("100"));
        OaDocument later = document("BX202609010002", DocumentStatusEnum.APPROVING, new BigDecimal("200"));
        // urgent 更新时间更早 → 更临近超时
        touch(urgent, today().minusDays(1).atTime(8, 0));
        touch(later, today().atTime(6, 0));
        workflowPort.pendingTaskIds = List.of(later.getId(), urgent.getId());

        List<DashboardViews.Todo> todos = dashboardService.todoList();

        assertThat(todos).extracting(DashboardViews.Todo::docCode)
                .containsExactly("BX202609010001", "BX202609010002");
    }

    @Test
    void shouldReturnBoardStatsWithNodeEfficiencyAndDistribution() {
        TestSecurityContext.mock(principal);
        document("BX202609010001", DocumentStatusEnum.APPROVING, new BigDecimal("100"));
        document("BX202609010002", DocumentStatusEnum.APPROVED, new BigDecimal("200"));
        document("BX202609010003", DocumentStatusEnum.REJECTED, new BigDecimal("300"));
        workflowPort.nodeStats = List.of(new WorkflowNodeStatResponse("直属主管", 8, 2, 3.5));

        DashboardViews.Board board = dashboardService.boardStats();

        assertThat(board.totalCount()).isEqualTo(3);
        assertThat(board.approvingCount()).isEqualTo(1);
        assertThat(board.approvedCount()).isEqualTo(1);
        assertThat(board.statusDistribution())
                .extracting(DashboardViews.StatusDistribution::status, DashboardViews.StatusDistribution::count)
                .contains(org.assertj.core.groups.Tuple.tuple(DocumentStatusEnum.APPROVING, 1L),
                        org.assertj.core.groups.Tuple.tuple(DocumentStatusEnum.APPROVED, 1L),
                        org.assertj.core.groups.Tuple.tuple(DocumentStatusEnum.REJECTED, 1L));
        assertThat(board.nodeEfficiencies()).hasSize(1);
        assertThat(board.nodeEfficiencies().get(0).nodeName()).isEqualTo("直属主管");
        assertThat(board.nodeEfficiencies().get(0).completionRatePercent()).isEqualTo(80.0);
    }

    @Test
    void shouldReturnWeeklyTrendWithSevenPoints() {
        TestSecurityContext.mock(principal);
        document("BX202609010001", DocumentStatusEnum.APPROVED, new BigDecimal("100"));

        List<DashboardViews.TrendPoint> trend = dashboardService.weeklyTrend();

        assertThat(trend).hasSize(7);
        assertThat(trend.get(6).submittedCount()).isEqualTo(1);
        assertThat(trend.get(6).completedCount()).isEqualTo(1);
        assertThat(trend.get(0).submittedCount()).isZero();
    }

    @Test
    void shouldReturnRiskListForHighAmountDocuments() {
        TestSecurityContext.mock(principal);
        document("BX202609010001", DocumentStatusEnum.APPROVING, new BigDecimal("90000"));
        document("BX202609010002", DocumentStatusEnum.APPROVING, new BigDecimal("100"));

        List<DashboardViews.Risk> risks = dashboardService.riskList();

        assertThat(risks).hasSize(1);
        assertThat(risks.get(0).docCode()).isEqualTo("BX202609010001");
        assertThat(risks.get(0).amount()).isEqualByComparingTo(new BigDecimal("90000"));
    }

    private OaDocument document(String docCode, DocumentStatusEnum status, BigDecimal amount) {
        OaDocument doc = new OaDocument();
        doc.setDocCode(docCode);
        doc.setBusinessType(docCode.startsWith("YY") ? BusinessTypeEnum.SEAL_APPLICATION
                : docCode.startsWith("FK") ? BusinessTypeEnum.BUSINESS_PAYMENT : BusinessTypeEnum.DAILY_PAYMENT);
        doc.setProjectName("费用报销");
        doc.setApplicant(applicant);
        doc.setCompany(CompanyEnum.HAI_XIA_JIN);
        doc.setDepartment("财务部");
        doc.setAmount(amount);
        doc.setNeedPostMaterial(false);
        doc.setStatus(status);
        doc.setCurrentNode("直属主管");
        doc.setRiskFlag(amount != null && amount.compareTo(new BigDecimal("80000")) >= 0);
        OaDocument saved = documentRepository.saveAndFlush(doc);
        saved.setProcessInstanceId("process-" + saved.getId());
        documentRepository.saveAndFlush(saved);
        touch(saved, today().atTime(7, 30));
        return saved;
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    /** 时间戳由数据库生成，测试中通过原生 SQL 指定。 */
    private void touch(OaDocument doc, LocalDateTime updatedAt) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update oa_document set created_at = :t, updated_at = :t where id = :id")
                .setParameter("t", updatedAt)
                .setParameter("id", doc.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private ApprovalRecord record(OaDocument doc, ApprovalActionEnum action) {
        ApprovalRecord record = new ApprovalRecord();
        record.setDocument(doc);
        record.setNodeName("直属主管");
        record.setApprover(applicant);
        record.setAction(action);
        return record;
    }

    @TestConfiguration
    static class Config {
        @Bean
        FakeWorkflowPort workflowPort() {
            return new FakeWorkflowPort();
        }
    }

    static class FakeWorkflowPort implements WorkflowPort {
        List<Long> pendingTaskIds = List.of();
        List<WorkflowNodeStatResponse> nodeStats = List.of();

        void reset() {
            pendingTaskIds = List.of();
            nodeStats = List.of();
        }

        @Override public String startProcess(Long configId, Long documentId, Map<String, Object> variables) {
            return "process-" + documentId;
        }
        @Override public void completeTask(String taskId, Map<String, Object> variables) {}
        @Override public List<Task> pendingTasksForUser(String account, List<String> roleNames) {
            return pendingTaskIds.stream().map(this::task).toList();
        }
        @Override public List<Task> tasksForProcess(String processInstanceId) { return List.of(); }
        @Override public List<Task> allActiveTasks() { return List.of(); }
        @Override public List<Task> delegatedTasks() { return List.of(); }
        @Override public void moveTaskToActivity(String processInstanceId, String taskId, String targetActivityId) {}
        @Override public void endProcess(String processInstanceId, String reason) {}
        @Override public void setAssignee(String taskId, String account) {}
        @Override public void delegateTask(String taskId, String account) {}
        @Override public void resolveTask(String taskId) {}
        @Override public List<WorkflowHistoryItemResponse> history(String processInstanceId) { return List.of(); }
        @Override public List<WorkflowNodeStatResponse> nodeStatistics() { return nodeStats; }

        private Task task(Long documentId) {
            Task task = org.mockito.Mockito.mock(Task.class);
            org.mockito.Mockito.when(task.getId()).thenReturn("task-" + documentId);
            org.mockito.Mockito.when(task.getName()).thenReturn("直属主管");
            org.mockito.Mockito.when(task.getAssignee()).thenReturn("zhangsan");
            org.mockito.Mockito.when(task.getProcessInstanceId()).thenReturn("process-" + documentId);
            return task;
        }
    }
}
