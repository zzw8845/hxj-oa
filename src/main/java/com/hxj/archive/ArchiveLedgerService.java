package com.hxj.archive;

import com.alibaba.excel.EasyExcel;
import com.hxj.common.PageResponse;
import com.hxj.entity.ArchiveLedger;
import com.hxj.repository.ArchiveLedgerRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 归档台账查询与导出：仅已通过单据的归档快照可查。 */
@Service
public class ArchiveLedgerService {

    private static final String[] EXPORT_HEADERS = {
            "单据编号", "项目名称", "业务类型", "单据类型", "所属公司", "申请人", "申请部门", "金额", "归档时间"};
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArchiveLedgerRepository ledgerRepository;

    public ArchiveLedgerService(ArchiveLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional(readOnly = true)
    public List<ArchiveLedgerItem> search(ArchiveLedgerQuery query) {
        return ledgerRepository.findAll(specification(query)).stream().map(this::toItem).toList();
    }

    /** 分页版归档查询：分页参数见 {@link ArchiveLedgerPageRequest}，按归档时间倒序。 */
    @Transactional(readOnly = true)
    public PageResponse<ArchiveLedgerItem> searchPaged(ArchiveLedgerPageRequest request) {
        return PageResponse.of(ledgerRepository
                .findAll(specification(request.toQuery()),
                        request.toPageable(Sort.by(Sort.Direction.DESC, "archivedAt")))
                .map(this::toItem));
    }

    /** 台账导出：xlsx 使用 EasyExcel，csv 使用 UTF-8（带 BOM）文本。 */
    @Transactional(readOnly = true)
    public ExportFile export(ArchiveLedgerQuery query, ExportFormat format) {
        List<ArchiveLedgerItem> items = search(query);
        return switch (format) {
            case XLSX -> new ExportFile("archive-ledger.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    toExcel(items));
            case CSV -> new ExportFile("archive-ledger.csv", "text/csv", toCsv(items));
        };
    }

    private Specification<ArchiveLedger> specification(ArchiveLedgerQuery query) {
        return (root, cq, builder) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (query != null) {
                if (StringUtils.hasText(query.applicant())) {
                    predicates.add(builder.like(builder.lower(root.get("applicant")), contains(query.applicant())));
                }
                if (StringUtils.hasText(query.department())) {
                    predicates.add(builder.like(builder.lower(root.get("department")), contains(query.department())));
                }
                if (StringUtils.hasText(query.docCode())) {
                    predicates.add(builder.like(builder.lower(root.get("docCode")), contains(query.docCode())));
                }
                if (query.archivedFrom() != null) {
                    predicates.add(builder.greaterThanOrEqualTo(root.get("archivedAt"), query.archivedFrom()));
                }
                if (query.archivedTo() != null) {
                    predicates.add(builder.lessThanOrEqualTo(root.get("archivedAt"), query.archivedTo()));
                }
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private String contains(String value) {
        return "%" + value.toLowerCase() + "%";
    }

    private ArchiveLedgerItem toItem(ArchiveLedger ledger) {
        return new ArchiveLedgerItem(
                ledger.getId(), ledger.getDocument().getId(), ledger.getDocCode(), ledger.getProjectName(),
                ledger.getBusinessType(), ledger.getDocumentType(), ledger.getCompany(),
                ledger.getApplicant(), ledger.getDepartment(), ledger.getAmount(), ledger.getArchivedAt());
    }

    private byte[] toExcel(List<ArchiveLedgerItem> items) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(EXPORT_HEADERS));
        items.forEach(item -> rows.add(row(item)));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out).sheet("归档台账").doWrite(rows);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成台账 Excel 失败", e);
        }
    }

    private byte[] toCsv(List<ArchiveLedgerItem> items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             Writer writer = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
            writer.write('\ufeff');
            writer.write(String.join(",", EXPORT_HEADERS) + System.lineSeparator());
            for (ArchiveLedgerItem item : items) {
                writer.write(String.join(",", csvRow(item)) + System.lineSeparator());
            }
            writer.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成台账 CSV 失败", e);
        }
    }

    private List<String> row(ArchiveLedgerItem item) {
        return List.of(
                nvl(item.docCode()), nvl(item.projectName()), nvl(item.businessType()),
                nvl(item.documentType()), nvl(item.company()), nvl(item.applicant()),
                nvl(item.department()), item.amount() == null ? "" : item.amount().toPlainString(),
                item.archivedAt() == null ? "" : TIME_FORMAT.format(item.archivedAt()));
    }

    private List<String> csvRow(ArchiveLedgerItem item) {
        return row(item).stream().map(this::escape).toList();
    }

    private String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains(System.lineSeparator())) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    public enum ExportFormat { XLSX, CSV }

    public record ExportFile(String fileName, String contentType, byte[] content) {}
}
