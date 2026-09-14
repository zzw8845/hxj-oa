package com.hxj.document;

import com.hxj.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批事项聚合管理端点（对齐钉钉一体式设计器）：表单 + 流程 + 工作台入口
 * 作为一个"审批事项"整体管理——管理员一次配置，一个事务生效。
 */
@Tag(name = "管理端-审批事项", description = "表单字段 + 审批流程 + 工作台入口一体式管理")
@RestController
@RequestMapping("/api/admin/approval-templates")
@PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
public class ApprovalTemplateController {

    private final ApprovalTemplateManagementService service;

    public ApprovalTemplateController(ApprovalTemplateManagementService service) {
        this.service = service;
    }

    @Operation(summary = "审批事项清单", description = "模板（含字段）+ 流程节点链 + 条件规则一屏全览")
    @GetMapping
    public ApiResponse<List<ApprovalTemplateManagementService.ApprovalTemplateView>> list() {
        return ApiResponse.success(service.list());
    }

    @Operation(summary = "创建审批事项",
            description = "一个事务内：创建并部署流程 → 创建模板（绑定流程）→ 自动生成工作台事项；任一环节失败整体回滚")
    @PostMapping
    public ApiResponse<FormTemplateManagementService.TemplateView> create(
            @RequestBody ApprovalTemplateManagementService.SaveApprovalTemplateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @Operation(summary = "更新审批事项",
            description = "同步更新流程（重新部署 BPMN）与模板（版本 +1）；流程名与模板名保持一致")
    @PutMapping("/{templateId}")
    public ApiResponse<FormTemplateManagementService.TemplateView> update(
            @PathVariable Long templateId,
            @RequestBody ApprovalTemplateManagementService.SaveApprovalTemplateRequest request) {
        return ApiResponse.success(service.update(templateId, request));
    }

    @Operation(summary = "删除审批事项",
            description = "级联清除：事项入口 + 模板字段 + 节点链 + 流程部署；被单据引用时拒绝")
    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> delete(@PathVariable Long templateId) {
        service.delete(templateId);
        return ApiResponse.success(null);
    }
}
