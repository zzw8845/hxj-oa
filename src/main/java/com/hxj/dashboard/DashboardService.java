package com.hxj.dashboard;

import com.hxj.entity.ApprovalAction;
import com.hxj.entity.DocumentStatus;
import com.hxj.entity.OaDocument;
import com.hxj.repository.ApprovalRecordRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.security.AuthenticatedUser;
import com.hxj.security.CurrentUser;
import com.hxj.security.DocumentAccessPolicy;
import com.hxj.workflow.WorkflowPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 8.1–8.5 统计看板与风险预警：首页统计卡片、待办列表、
 * 工作看板、近7天趋势与风险单据列表。
 */
@Service
public class DashboardService {

    /** 待办超时阈值（小时），用于“临近超时优先”排序。 */
    static final long TIMEOUT_HOURS = 48;

    private final OaDocumentRepository documentRepository;
    private final ApprovalRecordRepository approvalRepository;
    private final DocumentAccessPolicy accessPolicy;
    private final WorkflowPort workflowPort;
    private final ZoneId zone;
    private final long timeoutHours;

    public DashboardService(
            OaDocumentRepository documentRepository,
            ApprovalRecordRepository approvalRepository,
            DocumentAccessPolicy accessPolicy,
            WorkflowPort workflowPort,
            @Value("${app.dashboard.zone:Asia/Shanghai}") String zoneId,
            @Value("${app.dashboard.timeout-hours:48}") long timeoutHours) {
        this.documentRepository = documentRepository;
        this.approvalRepository = approvalRepository;
        this.accessPolicy = accessPolicy;
        this.workflowPort = workflowPort;
        this.zone = ZoneId.of(zoneId);
        this.timeoutHours = timeoutHours;
    }

    /** 8.1 首页统计：已驳回/待我审批/本月已办结（含环比）、合规率与菜单角标。 */
    @Transactional(readOnly = true)
    public DashboardViews.HomeStats homeStats() {
        AuthenticatedUser currentUser = CurrentUser.require();
        Specification<OaDocument> visible = accessPolicy.visibleTo(currentUser);
        long rejected = documentRepository.count(visible
                .and((root, cq, builder) -> builder.equal(root.get("status"), DocumentStatus.REJECTED)));
        long pendingMyApproval = workflowPort
                .pendingTasksForUser(currentUser.account(), currentUser.roles()).size();

        YearMonth month = YearMonth.from(LocalDate.now(zone));
        long monthlyCompleted = completedIn(month, visible);
        long lastMonthCompleted = completedIn(month.minusMonths(1), visible);
        double mom = lastMonthCompleted == 0
                ? (monthlyCompleted == 0 ? 0.0 : 100.0)
                : DashboardViews.round((monthlyCompleted - lastMonthCompleted) * 100.0 / lastMonthCompleted);

        long approvedTotal = documentRepository.count(visible
                .and((root, cq, builder) -> builder.equal(root.get("status"), DocumentStatus.APPROVED)));
        long compliant = 0;
        if (approvedTotal > 0) {
            List<OaDocument> approvedDocs = documentRepository.findAll(visible.and(
                    (root, cq, builder) -> builder.equal(root.get("status"), DocumentStatus.APPROVED)));
            compliant = approvedDocs.stream()
                    .filter(doc -> approvalRepository
                            .findByDocumentIdOrderByCreatedAtAsc(doc.getId()).stream()
                            .noneMatch(record -> record.getAction() == ApprovalAction.REJECT))
                    .count();
        }
        double complianceRate = approvedTotal == 0
                ? 100.0 : DashboardViews.round(compliant * 100.0 / approvedTotal);

        long riskCount = documentRepository.count(visible
                .and((root, cq, builder) -> builder.isTrue(root.get("riskFlag"))));
        return new DashboardViews.HomeStats(
                rejected, pendingMyApproval, monthlyCompleted, mom, complianceRate,
                new DashboardViews.Badge(pendingMyApproval, riskCount));
    }

    /** 8.2 首页待办审批：按临近超时（剩余时间最短）优先排序。 */
    @Transactional(readOnly = true)
    public List<DashboardViews.TodoItem> todoList() {
        AuthenticatedUser currentUser = CurrentUser.require();
        List<OaDocument> pending = workflowPort
                .pendingTasksForUser(currentUser.account(), currentUser.roles()).stream()
                .map(task -> documentRepository.findByProcessInstanceId(task.getProcessInstanceId()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .distinct()
                .toList();
        LocalDateTime now = LocalDateTime.now(zone);
        return pending.stream()
                .sorted(Comparator.comparingLong(doc -> remainingHours(doc, now)))
                .map(doc -> new DashboardViews.TodoItem(
                        doc.getId(), doc.getDocCode(), doc.getProjectName(), doc.getApplicantName(),
                        doc.getDepartment(), doc.getCurrentNode(), doc.getAmount(), doc.getUpdatedAt()))
                .toList();
    }

    /** 8.3 工作看板：总量/审批中/已办结/平均时长/节点效率/状态分布。 */
    @Transactional(readOnly = true)
    public DashboardViews.BoardStats boardStats() {
        AuthenticatedUser currentUser = CurrentUser.require();
        Specification<OaDocument> visible = accessPolicy.visibleTo(currentUser);
        long total = documentRepository.count(visible);
        long approving = documentRepository.count(visible
                .and((root, cq, builder) -> builder.equal(root.get("status"), DocumentStatus.APPROVING)));
        long approved = documentRepository.count(visible
                .and((root, cq, builder) -> builder.equal(root.get("status"), DocumentStatus.APPROVED)));

        List<OaDocument> approvedDocs = documentRepository.findAll(visible.and(
                (root, cq, builder) -> builder.equal(root.get("status"), DocumentStatus.APPROVED)));
        double avgHours = approvedDocs.stream()
                .filter(doc -> doc.getCreatedAt() != null && doc.getUpdatedAt() != null)
                .mapToLong(doc -> ChronoUnit.MINUTES.between(doc.getCreatedAt(), doc.getUpdatedAt()))
                .average()
                .orElse(0) / 60.0;

        List<DashboardViews.NodeEfficiency> nodes = workflowPort.nodeStatistics().stream()
                .map(DashboardViews.NodeEfficiency::from).toList();

        List<DashboardViews.StatusDistribution> distribution = new ArrayList<>();
        for (DocumentStatus status : List.of(DocumentStatus.APPROVING, DocumentStatus.APPROVED,
                DocumentStatus.REJECTED, DocumentStatus.PENDING, DocumentStatus.SUPPLEMENT_REQUIRED)) {
            distribution.add(new DashboardViews.StatusDistribution(status,
                    documentRepository.count(visible
                            .and((root, cq, builder) -> builder.equal(root.get("status"), status)))));
        }
        return new DashboardViews.BoardStats(
                total, approving, approved, DashboardViews.round(avgHours), nodes, distribution);
    }

    /** 8.4 近7天流程趋势：每日发起与办结数量。 */
    @Transactional(readOnly = true)
    public List<DashboardViews.TrendPoint> weeklyTrend() {
        AuthenticatedUser currentUser = CurrentUser.require();
        Specification<OaDocument> visible = accessPolicy.visibleTo(currentUser);
        LocalDate today = LocalDate.now(zone);
        List<DashboardViews.TrendPoint> points = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            long submitted = documentRepository.count(visible.and((root, cq, builder) -> builder.and(
                    builder.greaterThanOrEqualTo(root.get("createdAt"), start),
                    builder.lessThan(root.get("createdAt"), end))));
            long completed = documentRepository.count(visible.and((root, cq, builder) -> builder.and(
                    builder.equal(root.get("status"), DocumentStatus.APPROVED),
                    builder.greaterThanOrEqualTo(root.get("updatedAt"), start),
                    builder.lessThan(root.get("updatedAt"), end))));
            points.add(new DashboardViews.TrendPoint(start, submitted, completed));
        }
        return points;
    }

    /** 8.5 风险预警：金额达到阈值（risk_flag）的单据列表。 */
    @Transactional(readOnly = true)
    public List<DashboardViews.RiskItem> riskList() {
        AuthenticatedUser currentUser = CurrentUser.require();
        Specification<OaDocument> visible = accessPolicy.visibleTo(currentUser)
                .and((root, cq, builder) -> builder.isTrue(root.get("riskFlag")));
        return documentRepository.findAll(visible).stream()
                .map(doc -> new DashboardViews.RiskItem(
                        doc.getId(), doc.getDocCode(), doc.getProjectName(), doc.getBusinessType(),
                        doc.getApplicantName(), doc.getDepartment(), doc.getAmount(),
                        doc.getStatus(), doc.getUpdatedAt()))
                .toList();
    }

    private long completedIn(YearMonth month, Specification<OaDocument> visible) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        return documentRepository.count(visible.and((root, cq, builder) -> builder.and(
                builder.equal(root.get("status"), DocumentStatus.APPROVED),
                builder.greaterThanOrEqualTo(root.get("updatedAt"), start),
                builder.lessThan(root.get("updatedAt"), end))));
    }

    /** 剩余超时小时数：越小越临近超时，排序越靠前。 */
    private long remainingHours(OaDocument doc, LocalDateTime now) {
        if (doc.getUpdatedAt() == null) {
            return Long.MAX_VALUE;
        }
        long elapsed = ChronoUnit.HOURS.between(doc.getUpdatedAt(), now);
        return Math.max(0, timeoutHours - elapsed);
    }
}
