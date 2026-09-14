package com.hxj.document;

import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.FormTemplate;
import com.hxj.entity.WorkbenchEntry;
import com.hxj.exception.BusinessException;
import com.hxj.repository.FormTemplateRepository;
import com.hxj.repository.WorkbenchEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 工作台事项入口管理：事项（员工视角的申请事由）到承接模板的映射增删改。
 *
 * <p>分区（zone）不由调用方指定——它由承接模板的业务类型唯一派生
 * （DAILY_PAYMENT→DAILY，BUSINESS_PAYMENT→BUSINESS，SEAL_APPLICATION→SEAL），
 * 保证"事项出现在哪张卡"与"单据归入哪个台账"同源一致。
 */
@Service
public class WorkbenchEntryManagementService {

    private static final Map<String, String> ZONE_BY_BUSINESS_TYPE =
            Map.of("DAILY_PAYMENT", "DAILY", "BUSINESS_PAYMENT", "BUSINESS", "SEAL_APPLICATION", "SEAL");

    private final WorkbenchEntryRepository entryRepository;
    private final FormTemplateRepository templateRepository;

    public WorkbenchEntryManagementService(WorkbenchEntryRepository entryRepository,
                                           FormTemplateRepository templateRepository) {
        this.entryRepository = entryRepository;
        this.templateRepository = templateRepository;
    }

    /** 保存请求：调用方只声明事项文案与承接模板，分区与排序可派生。 */
    public record WorkbenchEntryPayload(String label, String hint, Long templateId, Integer sortOrder) {}

    @Transactional
    public WorkbenchEntry create(WorkbenchEntryPayload payload) {
        validate(payload);
        WorkbenchEntry entry = new WorkbenchEntry();
        apply(entry, payload);
        entry.setSortOrder(payload.sortOrder() == null
                ? nextSortOrder(entry.getZone()) : payload.sortOrder());
        return entryRepository.save(entry);
    }

    @Transactional
    public WorkbenchEntry update(Long id, WorkbenchEntryPayload payload) {
        validate(payload);
        WorkbenchEntry entry = entryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.WORKBENCH_ENTRY_NOT_FOUND,
                        "工作台事项不存在"));
        apply(entry, payload);
        if (payload.sortOrder() != null) {
            entry.setSortOrder(payload.sortOrder());
        }
        return entryRepository.save(entry);
    }

    @Transactional
    public void delete(Long id) {
        if (!entryRepository.existsById(id)) {
            throw new BusinessException(ErrorCodeEnum.WORKBENCH_ENTRY_NOT_FOUND, "工作台事项不存在");
        }
        entryRepository.deleteById(id);
    }

    private void validate(WorkbenchEntryPayload payload) {
        if (payload == null || !StringUtils.hasText(payload.label())) {
            throw new BusinessException(ErrorCodeEnum.WORKBENCH_ENTRY_INVALID, "事项名称不能为空");
        }
        if (payload.templateId() == null) {
            throw new BusinessException(ErrorCodeEnum.WORKBENCH_ENTRY_INVALID, "必须指定承接模板");
        }
        // 承接模板必须启用且已绑定流程：否则事项按钮点开填完单，提交时才报"未配置对应审批流程"
        FormTemplate template = templateRepository.findById(payload.templateId())
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.WORKBENCH_ENTRY_INVALID,
                        "承接模板不存在：" + payload.templateId()));
        if (!"ENABLED".equals(template.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.WORKBENCH_ENTRY_INVALID,
                    "承接模板已停用：" + template.getName());
        }
        if (template.getFlowConfigId() == null) {
            throw new BusinessException(ErrorCodeEnum.WORKBENCH_ENTRY_INVALID,
                    "承接模板未绑定审批流程：" + template.getName());
        }
    }

    private void apply(WorkbenchEntry entry, WorkbenchEntryPayload payload) {
        FormTemplate template = templateRepository.findById(payload.templateId()).orElseThrow();
        entry.setLabel(payload.label().trim());
        entry.setHint(StringUtils.hasText(payload.hint()) ? payload.hint().trim() : null);
        entry.setTemplateId(template.getId());
        entry.setZone(ZONE_BY_BUSINESS_TYPE.get(template.getBusinessType()));
    }

    private int nextSortOrder(String zone) {
        return entryRepository.findAllGroupedByZone().stream()
                .filter(e -> zone.equals(e.getZone()))
                .mapToInt(WorkbenchEntry::getSortOrder)
                .max().orElse(0) + 1;
    }
}
