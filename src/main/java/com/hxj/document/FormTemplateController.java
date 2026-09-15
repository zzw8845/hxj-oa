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

/** 表单模板：管理端 CRUD + 提交渲染读取。 */
@Tag(name = "表单模板")
@RestController
public class FormTemplateController {

    private final FormTemplateManagementService templateService;
    private final com.hxj.workflow.FlowConfigManagementService flowConfigService;
    private final com.hxj.workflow.ConditionVariableCatalog conditionVariableCatalog;

    public FormTemplateController(FormTemplateManagementService templateService,
                                  com.hxj.workflow.FlowConfigManagementService flowConfigService,
                                  com.hxj.workflow.ConditionVariableCatalog conditionVariableCatalog) {
        this.templateService = templateService;
        this.flowConfigService = flowConfigService;
        this.conditionVariableCatalog = conditionVariableCatalog;
    }

    @Operation(summary = "启用模板列表", description = "登录即可读取：提交表单按业务类型渲染字段")
    @GetMapping("/api/form-templates/enabled")
    public ApiResponse<List<FormTemplateManagementService.TemplateView>> enabled() {
        return ApiResponse.success(templateService.listEnabled());
    }

    @Operation(summary = "模板详情（含字段清单）", description = "登录即可读取：驳回重提/详情渲染用")
    @GetMapping("/api/form-templates/{id}")
    public ApiResponse<FormTemplateManagementService.TemplateView> get(@PathVariable Long id) {
        return ApiResponse.success(templateService.get(id));
    }

    @Operation(summary = "模板列表（管理）")
    @GetMapping("/api/admin/form-templates")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<List<FormTemplateManagementService.TemplateView>> list() {
        return ApiResponse.success(templateService.list());
    }

    @Operation(summary = "模板详情（管理）", description = "编辑回填用，含字段清单")
    @GetMapping("/api/admin/form-templates/{id}")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<FormTemplateManagementService.TemplateView> getAdmin(@PathVariable Long id) {
        return ApiResponse.success(templateService.get(id));
    }

    @Operation(summary = "创建模板",
            description = "钉钉同构聚合提交：基本信息 + 字段清单 + 自带流程（节点/转移边）一体落库")
    @PostMapping("/api/admin/form-templates")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<FormTemplateManagementService.TemplateView> create(
            @RequestBody SaveFormTemplateRequest request) {
        return ApiResponse.success(templateService.create(request));
    }

    @Operation(summary = "更新模板",
            description = "字段清单整体替换，自带流程整体重建并回草稿；提升字段不可删除")
    @PutMapping("/api/admin/form-templates/{id}")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<FormTemplateManagementService.TemplateView> update(
            @PathVariable Long id, @RequestBody SaveFormTemplateRequest request) {
        return ApiResponse.success(templateService.update(id, request));
    }

    @Operation(summary = "发布模板",
            description = "部署自带流程的 BPMN 定义并推进版本号；仅已发布模板的流程可被新单据使用")
    @PostMapping("/api/admin/form-templates/{id}/publish")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<FormTemplateManagementService.TemplateView> publish(@PathVariable Long id) {
        FormTemplateManagementService.TemplateView template = templateService.get(id);
        if (template.flowConfigId() != null) {
            flowConfigService.publish(template.flowConfigId());
        }
        return ApiResponse.success(templateService.get(id));
    }

    @Operation(summary = "条件变量目录",
            description = "该模板的流程分支可用判据：全部启用字段 + 系统字段（钉钉同构两档）")
    @GetMapping("/api/admin/form-templates/{id}/condition-variables")
    public ApiResponse<List<com.hxj.workflow.FlowConfigItems.VariableOption>> conditionVariables(
            @PathVariable Long id) {
        return ApiResponse.success(conditionVariableCatalog.describe(id));
    }

    @Operation(summary = "删除模板", description = "与流程删除同规则：被单据引用（审计回溯）或被工作台事项承接（入口悬空）时拒绝")
    @DeleteMapping("/api/admin/form-templates/{id}")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return ApiResponse.success(null);
    }
}
