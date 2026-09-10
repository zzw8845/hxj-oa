package com.hxj.archive;

import com.hxj.common.ApiResponse;
import com.hxj.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/** 归档台账接口：多条件组合查询与 Excel/CSV 导出。 */
@Tag(name = "归档台账", description = "已通过单据的归档查询与 Excel/CSV 导出")
@RestController
@RequestMapping("/api/archive-ledger")
public class ArchiveLedgerController {

    private final ArchiveLedgerService ledgerService;

    public ArchiveLedgerController(ArchiveLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }


    @Operation(summary = "分页归档查询", description = "仅已通过单据，按申请人、部门、单据编号、归档时间组合查询")
    @GetMapping("/page")
    public ApiResponse<PageResponse<ArchiveLedgerItemResponse>> searchPaged(@Valid ArchiveLedgerPageRequest request) {
        return ApiResponse.success(ledgerService.searchPaged(request));
    }

    @Operation(summary = "台账导出", description = "导出归档台账为 Excel(XLSX) 或 CSV 文件")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @Parameter(description = "申请人姓名（模糊匹配）") @RequestParam(required = false) String applicant,
            @Parameter(description = "申请部门（模糊匹配）") @RequestParam(required = false) String department,
            @Parameter(description = "单据编号（模糊匹配）") @RequestParam(required = false) String docCode,
            @Parameter(description = "归档时间起（含）") @RequestParam(required = false) LocalDateTime archivedFrom,
            @Parameter(description = "归档时间止（含）") @RequestParam(required = false) LocalDateTime archivedTo,
            @Parameter(description = "导出格式：XLSX 或 CSV（默认 XLSX）") @RequestParam(defaultValue = "XLSX") ArchiveLedgerService.ExportFormatEnum format) {
        ArchiveLedgerService.ExportFile file = ledgerService.export(
                new ArchiveLedgerQueryRequest(applicant, department, docCode, archivedFrom, archivedTo), format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodeFileName(file.fileName()))
                .body(file.content());
    }

    /** 按 RFC 5987 对下载文件名做百分号编码（空格转 %20，避免浏览器截断）。 */
    private static String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
