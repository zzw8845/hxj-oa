package com.hxj.approval;

import com.hxj.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端单据任务操作：转办兜底。
 *
 * <p>动态指派（直属主管/核算会计）解析为空或候选组无人认领时，
 * 由管理员将待办任务转给指定审批人，与离职交接共用 TRANSFER 留痕机制。
 */
@Tag(name = "管理端-单据任务", description = "任务转办兜底：待办无主时管理员指派审批人")
@RestController
@RequestMapping("/api/admin/documents")
@PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
public class AdminDocumentTaskController {

    private final ApprovalActionService service;

    public AdminDocumentTaskController(ApprovalActionService service) {
        this.service = service;
    }

    @Operation(summary = "转办待办任务",
            description = "将单据当前待办任务指派给指定账号（动态指派为空/候选组无人认领的兜底），TRANSFER 留痕")
    @PostMapping("/{documentId}/transfer")
    public ApiResponse<ApprovalResultResponse> transfer(
            @PathVariable Long documentId,
            @RequestBody TransferTaskRequest request) {
        return ApiResponse.success(service.transfer(documentId, request));
    }
}
