package com.hxj.document;

import com.hxj.common.ApiResponse;
import com.hxj.entity.WorkbenchEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台事项入口管理：事项（员工视角的申请事由）到承接模板映射的增删改。
 *
 * <p>分区由承接模板的业务类型派生，调用方不可指定——"事项出现在哪张卡"
 * 与"单据归入哪个台账"同源一致，不存在配置失配空间。
 */
@Tag(name = "管理端-工作台事项", description = "事项快捷入口的增删改（映射为后端业务配置）")
@RestController
@RequestMapping("/api/admin/workbench/entries")
@PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
public class WorkbenchAdminController {

    private final WorkbenchEntryManagementService service;

    public WorkbenchAdminController(WorkbenchEntryManagementService service) {
        this.service = service;
    }

    @Operation(summary = "新增事项", description = "分区由承接模板的业务类型派生；sortOrder 缺省排在本区末尾")
    @PostMapping
    public ApiResponse<WorkbenchEntry> create(@RequestBody WorkbenchEntryManagementService.WorkbenchEntryPayload payload) {
        return ApiResponse.success(service.create(payload));
    }

    @Operation(summary = "修改事项", description = "可改文案与承接模板；分区随之重派生")
    @PutMapping("/{id}")
    public ApiResponse<WorkbenchEntry> update(@PathVariable Long id,
            @RequestBody WorkbenchEntryManagementService.WorkbenchEntryPayload payload) {
        return ApiResponse.success(service.update(id, payload));
    }

    @Operation(summary = "删除事项")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
