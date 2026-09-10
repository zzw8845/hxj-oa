package com.hxj.workflow;

import com.hxj.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程配置管理接口：查询对已认证用户开放（供流程管理页/链条可视化展示），
 * 新增与修改仅限拥有配置权限的管理员。
 */
@Tag(name = "流程配置", description = "流程配置查询、新增、修改与可视化链条数据（新增/修改需 CONFIGURE_FLOW_PERMISSION 权限）")
@RestController
public class FlowConfigController {

    private final FlowConfigManagementService service;

    public FlowConfigController(FlowConfigManagementService service) {
        this.service = service;
    }

    @Operation(summary = "流程配置列表", description = "查询全部流程配置摘要")
    @GetMapping("/api/flow-configs")
    public ApiResponse<List<FlowConfigItems.Brief>> list() {
        return ApiResponse.success(service.list());
    }

    /** 5.12 按业务单据类型返回完整节点链（可视化链条数据）。 */
    @Operation(summary = "按类型查询流程配置", description = "按业务单据类型返回完整节点链（可视化链条数据）")
    @GetMapping("/api/flow-configs/by-type")
    public ApiResponse<FlowConfigItems.Config> detailByType(
            @Parameter(description = "业务单据类型名称") @RequestParam String type) {
        return ApiResponse.success(service.detailByType(type));
    }

    @Operation(summary = "流程配置详情", description = "按 ID 查询流程配置详情（含节点链与条件分支）")
    @GetMapping("/api/flow-configs/{id}")
    public ApiResponse<FlowConfigItems.Config> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @Operation(summary = "新增流程配置", description = "新增流程配置（仅管理员）")
    @PostMapping("/api/admin/flow-configs")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<FlowConfigItems.Config> create(@RequestBody FlowConfigItems.SaveFlowConfigRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @Operation(summary = "修改流程配置", description = "修改流程配置节点链与条件分支（仅管理员），新提交单据按新流程流转")
    @PutMapping("/api/admin/flow-configs/{id}")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<FlowConfigItems.Config> update(
            @PathVariable Long id, @RequestBody FlowConfigItems.SaveFlowConfigRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @Operation(summary = "删除流程配置", description = "删除流程配置（仅管理员；已被单据引用的禁止删除）")
    @DeleteMapping("/api/admin/flow-configs/{id}")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }
}
