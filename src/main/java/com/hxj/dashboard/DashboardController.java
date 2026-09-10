package com.hxj.dashboard;

import com.hxj.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 统计看板与风险预警接口。 */
@Tag(name = "统计看板与风险预警", description = "首页统计、待办列表、工作看板、近7天趋势与风险预警")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 8.1 首页统计卡片与菜单角标。 */
    @Operation(summary = "首页统计", description = "已驳回/待我审批/本月已办结数量及环比、流程合规率、菜单角标数量")
    @GetMapping("/home")
    public ApiResponse<DashboardViews.Home> home() {
        return ApiResponse.success(dashboardService.homeStats());
    }

    /** 8.2 首页待办审批列表（临近超时优先）。 */
    @Operation(summary = "首页待办审批列表", description = "按临近超时优先排序返回待办审批单据")
    @GetMapping("/todos")
    public ApiResponse<List<DashboardViews.Todo>> todos() {
        return ApiResponse.success(dashboardService.todoList());
    }

    /** 8.3 工作看板统计。 */
    @Operation(summary = "工作看板统计", description = "申请总量/审批中/已办结/平均审批时长/节点处理效率/状态分布")
    @GetMapping("/board")
    public ApiResponse<DashboardViews.Board> board() {
        return ApiResponse.success(dashboardService.boardStats());
    }

    /** 8.4 近7天流程趋势。 */
    @Operation(summary = "近7天流程趋势", description = "每日发起/办结数量与状态分布")
    @GetMapping("/trend")
    public ApiResponse<List<DashboardViews.TrendPoint>> trend() {
        return ApiResponse.success(dashboardService.weeklyTrend());
    }

    /** 8.5 风险预警列表。 */
    @Operation(summary = "风险预警列表", description = "金额 ≥ 8 万元的风险单据列表与数量")
    @GetMapping("/risks")
    public ApiResponse<List<DashboardViews.Risk>> risks() {
        return ApiResponse.success(dashboardService.riskList());
    }
}