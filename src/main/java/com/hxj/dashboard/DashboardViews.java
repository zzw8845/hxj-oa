package com.hxj.dashboard;

import com.hxj.enums.BusinessTypeEnum;
import com.hxj.enums.DocumentStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/** 统计看板与风险预警的视图模型集合。 */
public final class DashboardViews {
    private DashboardViews() {}


    /** 8.1 首页统计卡片：数量、环比、合规率与菜单角标。 */
    public record Home(
            @Schema(description = "本月驳回单据数") long rejectedCount,
            @Schema(description = "待我审批的单据数") long pendingMyApprovalCount,
            @Schema(description = "本月办结单据数") long monthlyCompletedCount,
            @Schema(description = "本月办结数环比，单位 %（正为上升）") double monthlyCompletedMomPercent,
            @Schema(description = "合规率，单位 %") double complianceRatePercent,
            @Schema(description = "菜单角标数量") Badge badge) {
    }

    /** 菜单角标数量。 */
    public record Badge(
            @Schema(description = "待审批单据数") long pendingApprovalCount,
            @Schema(description = "风险预警单据数") long riskCount) {
    }

    /** 8.2 首页待办审批条目（按临近超时优先排序）。 */
    public record Todo(
            @Schema(description = "单据ID") Long documentId,
            @Schema(description = "单据编号") String docCode,
            @Schema(description = "项目名称") String projectName,
            @Schema(description = "申请人姓名") String applicantName,
            @Schema(description = "申请部门") String department,
            @Schema(description = "当前审批节点") String currentNode,
            @Schema(description = "单据金额") BigDecimal amount,
            @Schema(description = "更新时间") LocalDateTime updatedAt) {
    }

    /** 8.3 工作看板统计。 */
    public record Board(
            @Schema(description = "单据总数") long totalCount,
            @Schema(description = "审批中单据数") long approvingCount,
            @Schema(description = "已通过单据数") long approvedCount,
            @Schema(description = "平均审批时长（小时）") double avgApprovalHours,
            @Schema(description = "各节点处理效率列表") List<NodeEfficiency> nodeEfficiencies,
            @Schema(description = "单据状态分布列表") List<StatusDistribution> statusDistribution) {

        /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
        public Board {
            nodeEfficiencies = nodeEfficiencies == null ? List.of() : List.copyOf(nodeEfficiencies);
            statusDistribution = statusDistribution == null ? List.of() : List.copyOf(statusDistribution);
        }
    }

    /** 节点处理效率。 */
    public record NodeEfficiency(
            @Schema(description = "节点名称") String nodeName,
            @Schema(description = "已完成单据数") long completedCount,
            @Schema(description = "进行中单据数") long activeCount,
            @Schema(description = "平均完成时长（小时）") double avgCompletionHours,
            @Schema(description = "完成率，单位 %") double completionRatePercent) {

        static NodeEfficiency from(com.hxj.workflow.WorkflowNodeStatResponse stat) {
            long total = stat.completedCount() + stat.activeCount();
            double rate = total == 0 ? 0.0 : stat.completedCount() * 100.0 / total;
            return new NodeEfficiency(
                    stat.nodeName(), stat.completedCount(), stat.activeCount(),
                    round(stat.avgCompletionHours()), round(rate));
        }
    }

    /** 单据状态分布。 */
    public record StatusDistribution(
            @Schema(description = "单据状态（枚举）") DocumentStatusEnum status,
            @Schema(description = "该状态单据数") long count) {
    }

    /** 8.4 近7天流程趋势（每日发起/办结数量）。 */
    public record TrendPoint(
            @Schema(description = "日期") LocalDateTime date,
            @Schema(description = "当日发起数") long submittedCount,
            @Schema(description = "当日办结数") long completedCount) {
    }

    /** 8.5 风险预警条目。 */
    public record Risk(
            @Schema(description = "单据ID") Long documentId,
            @Schema(description = "单据编号") String docCode,
            @Schema(description = "项目名称") String projectName,
            @Schema(description = "业务类型（枚举）") BusinessTypeEnum businessType,
            @Schema(description = "申请人姓名") String applicantName,
            @Schema(description = "申请部门") String department,
            @Schema(description = "单据金额") BigDecimal amount,
            @Schema(description = "单据状态（枚举）") DocumentStatusEnum status,
            @Schema(description = "更新时间") LocalDateTime updatedAt) {
    }

    static double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
