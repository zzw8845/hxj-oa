package com.hxj.document;

import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.FormTemplate;
import com.hxj.entity.WorkbenchEntry;
import com.hxj.enums.FlowCategoryEnum;
import com.hxj.exception.BusinessException;
import com.hxj.repository.FormTemplateRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.WorkbenchEntryRepository;
import com.hxj.workflow.FlowConfigItems;
import com.hxj.workflow.FlowConfigManagementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 审批事项聚合管理（对齐钉钉一体式设计器）：表单字段 + 审批流程 + 工作台入口
 * 在一个事务内建齐/改齐/删净——管理员面对的是"一个审批事项"，而不是
 * 先建流程、再建模板、再绑定的三段式。
 *
 * <p>内部仍复用流程与模板两个领域的既有服务（各自保留独立端点），本层只做
 * 编排与一致性：流程名 = 模板名，流程分类由业务类型派生，事项入口自动生成。
 */
@Service
public class ApprovalTemplateManagementService {

    private static final Map<String, FlowCategoryEnum> CATEGORY_BY_BUSINESS_TYPE = Map.of(
            "DAILY_PAYMENT", FlowCategoryEnum.DAILY,
            "BUSINESS_PAYMENT", FlowCategoryEnum.BUSINESS,
            "SEAL_APPLICATION", FlowCategoryEnum.SEAL);

    private final FlowConfigManagementService flowConfigService;
    private final FormTemplateManagementService templateService;
    private final FormTemplateRepository templateRepository;
    private final OaDocumentRepository documentRepository;
    private final WorkbenchEntryRepository entryRepository;

    public ApprovalTemplateManagementService(
            FlowConfigManagementService flowConfigService,
            FormTemplateManagementService templateService,
            FormTemplateRepository templateRepository,
            OaDocumentRepository documentRepository,
            WorkbenchEntryRepository entryRepository) {
        this.flowConfigService = flowConfigService;
        this.templateService = templateService;
        this.templateRepository = templateRepository;
        this.documentRepository = documentRepository;
        this.entryRepository = entryRepository;
    }

    /** 保存请求：name 同时充当流程名与模板名（一个事项一个名字，钉钉同款）。 */
    public record SaveApprovalTemplateRequest(
            String name,
            String businessType,
            List<String> attachmentRequirements,
            List<SaveFormTemplateRequest.FieldPayload> fields,
            FlowPayload flow) {

        public record FlowPayload(
                List<FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload> nodes,
                List<FlowConfigItems.SaveFlowConfigRequest.FlowTransitionPayload> transitions) {}
    }

    /** 聚合视图：模板（含字段清单）+ 流程节点链 + 条件规则，管理端一屏全览。 */
    public record ApprovalTemplateView(FormTemplateManagementService.TemplateView template,
                                       FlowConfigItems.Config flow) {}

    @Transactional
    public FormTemplateManagementService.TemplateView create(SaveApprovalTemplateRequest request) {
        validateFlowPayload(request);
        FlowCategoryEnum category = requireCategory(request);
        // 同一事务内：流程（含 BPMN 部署）→ 模板（含字段清单）→ 自动事项入口；
        // 任一环节失败整体回滚，不存在"流程建了模板没建"的半拉子状态
        // 条件判据按模板字段校验（两档：模板启用字段 + 系统字段）——模板与字段先落库，流程校验才可查
        FormTemplateManagementService.TemplateView tv = templateService.create(new SaveFormTemplateRequest(
                request.businessType(), request.name(), null,
                request.attachmentRequirements(), request.fields()));
        FlowConfigItems.Config flow = flowConfigService.create(new FlowConfigItems.SaveFlowConfigRequest(
                request.name(), category, request.flow().nodes(), request.flow().transitions()), tv.id());
        templateRepository.findById(tv.id()).ifPresent(t -> {
            t.setFlowConfigId(flow.id());
            templateRepository.save(t);
        });
        return templateService.get(tv.id());
    }

    @Transactional
    public FormTemplateManagementService.TemplateView update(Long templateId, SaveApprovalTemplateRequest request) {
        validateFlowPayload(request);
        FormTemplate template = requireTemplate(templateId);
        FlowCategoryEnum category = requireCategory(request);
        if (template.getFlowConfigId() == null) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID, "模板未绑定流程，请走创建接口");
        }
        templateService.update(templateId, new SaveFormTemplateRequest(
                request.businessType(), request.name(), template.getFlowConfigId(),
                request.attachmentRequirements(), request.fields()));
        flowConfigService.update(template.getFlowConfigId(), new FlowConfigItems.SaveFlowConfigRequest(
                request.name(), category, request.flow().nodes(), request.flow().transitions()), templateId);
        return templateService.get(templateId);
    }

    /** 删除：单据引用（按模板与流程双向核查）时拒绝；事项入口、字段清单、节点链、流程部署级联清除。 */
    @Transactional
    public void delete(Long templateId) {
        FormTemplate template = requireTemplate(templateId);
        long docRefs = documentRepository.countByFormTemplateIdOrFlowConfigId(
                templateId, template.getFlowConfigId());
        if (docRefs > 0) {
            throw new BusinessException(ErrorCodeEnum.FORM_TEMPLATE_IN_USE,
                    "该审批事项已被 " + docRefs + " 张单据引用，无法删除");
        }
        templateService.delete(templateId);
        if (template.getFlowConfigId() != null) {
            flowConfigService.delete(template.getFlowConfigId());
        }
    }

    @Transactional(readOnly = true)
    public List<ApprovalTemplateView> list() {
        return templateService.list().stream()
                .map(tv -> new ApprovalTemplateView(tv,
                        tv.flowConfigId() == null ? null : flowConfigService.detail(tv.flowConfigId())))
                .toList();
    }

    private FormTemplate requireTemplate(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.FORM_TEMPLATE_NOT_FOUND, "审批事项不存在"));
    }

    /** 流程负载防御：空/缺节点链的聚合请求在保存关口拒绝，不落入半拉子状态。 */
    private void validateFlowPayload(SaveApprovalTemplateRequest request) {
        if (request.flow() == null || request.flow().nodes() == null || request.flow().nodes().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID, "审批流程节点链不能为空");
        }
    }

    private FlowCategoryEnum requireCategory(SaveApprovalTemplateRequest request) {
        FlowCategoryEnum category = CATEGORY_BY_BUSINESS_TYPE.get(request.businessType());
        if (category == null) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "业务类型非法（可用：DAILY_PAYMENT / BUSINESS_PAYMENT / SEAL_APPLICATION）："
                            + request.businessType());
        }
        return category;
    }
}
