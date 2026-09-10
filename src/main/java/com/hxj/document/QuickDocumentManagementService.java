package com.hxj.document;

import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.QuickDocument;
import com.hxj.exception.BusinessException;
import com.hxj.repository.QuickDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 快捷单据目录管理服务：业务可自行维护常用单据名目（增删改），
 * 不再依赖数据库预置快照。同业务类型下名称唯一。
 */
@Service
public class QuickDocumentManagementService {

    private final QuickDocumentRepository quickDocumentRepository;

    public QuickDocumentManagementService(QuickDocumentRepository quickDocumentRepository) {
        this.quickDocumentRepository = quickDocumentRepository;
    }

    /** 新增快捷单据条目。 */
    @Transactional
    public QuickDocumentViews.Entry create(SaveQuickDocumentRequest request) {
        ensureNameAvailable(request.businessType(), request.name(), null);
        QuickDocument document = new QuickDocument();
        apply(document, request);
        return toView(quickDocumentRepository.save(document));
    }

    /** 编辑快捷单据条目（改名/换类目/调排序）。 */
    @Transactional
    public QuickDocumentViews.Entry update(Long id, SaveQuickDocumentRequest request) {
        QuickDocument document = quickDocumentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.QUICK_DOCUMENT_NOT_FOUND, "快捷单据不存在"));
        ensureNameAvailable(request.businessType(), request.name(), id);
        apply(document, request);
        return toView(quickDocumentRepository.save(document));
    }

    /** 删除快捷单据条目。 */
    @Transactional
    public void delete(Long id) {
        QuickDocument document = quickDocumentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.QUICK_DOCUMENT_NOT_FOUND, "快捷单据不存在"));
        quickDocumentRepository.delete(document);
    }

    /** 全量列表（按业务类型、排序号升序）。 */
    @Transactional(readOnly = true)
    public List<QuickDocumentViews.Entry> list() {
        return quickDocumentRepository.findAll().stream()
                .sorted(Comparator.comparing(QuickDocument::getBusinessType)
                        .thenComparing(QuickDocument::getSortOrder)
                        .thenComparing(QuickDocument::getId))
                .map(this::toView)
                .toList();
    }

    /** 同业务类型下名称唯一（编辑时排除自身）。 */
    private void ensureNameAvailable(
            com.hxj.enums.BusinessTypeEnum businessType, String name, Long excludeId) {
        quickDocumentRepository
                .findByBusinessTypeAndName(businessType, name)
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCodeEnum.QUICK_DOCUMENT_EXISTS, "该业务类型下单据名称已存在");
                });
    }

    private void apply(QuickDocument document, SaveQuickDocumentRequest request) {
        document.setBusinessType(request.businessType());
        document.setName(request.name());
        document.setSortOrder(request.sortOrder());
    }

    private QuickDocumentViews.Entry toView(QuickDocument document) {
        return new QuickDocumentViews.Entry(document.getId(), document.getBusinessType(),
                document.getName(), document.getSortOrder());
    }
}
