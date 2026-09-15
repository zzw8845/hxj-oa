package com.hxj.document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.FormField;
import com.hxj.entity.FormTemplate;
import com.hxj.entity.WorkbenchEntry;
import com.hxj.exception.BusinessException;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.FormFieldRepository;
import com.hxj.repository.FormTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 表单模板管理：模板/字段的元数据维护 + 提交链路的模板支撑
 * （字段值校验、快照冻结、值序列化、必附材料清单）。
 */
@Service
public class FormTemplateManagementService {

    /** 提升字段保留键：值写结构化列或流程变量，模板中不可删除。 */
    public static final Set<String> RESERVED_KEYS = Set.of(
            "amount", "title", "involvesFunds", "requiresAdminReview", "businessMode", "needPostMaterial");

    /** 业务类型 → 单号前缀：前缀由业务类型唯一决定，不接受调用方自传。 */
    private static final Map<String, String> PREFIX_BY_BUSINESS_TYPE =
            Map.of("DAILY_PAYMENT", "BX", "BUSINESS_PAYMENT", "FK", "SEAL_APPLICATION", "YY");

    /** 系统属性保留字：表单字段 key 不得占用。 */
    private static final Set<String> SYSTEM_KEYS = Set.of(
            "docCode", "applicant", "department", "status", "processInstanceId",
            "currentNode", "linkedDocument", "projectName", "formTemplateId");

    public static final Set<String> CONTROL_TYPES = Set.of(
            "TEXT", "TEXTAREA", "NUMBER", "DATE", "SELECT", "BOOLEAN", "IMAGE", "ATTACHMENT", "TABLE");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FormTemplateRepository templateRepository;
    private final FormFieldRepository fieldRepository;
    private final com.hxj.repository.OaDocumentRepository documentRepository;
    private final com.hxj.repository.WorkbenchEntryRepository workbenchEntryRepository;
    private final FlowConfigRepository flowConfigRepository;

    public FormTemplateManagementService(FormTemplateRepository templateRepository,
                                         FormFieldRepository fieldRepository,
                                         com.hxj.repository.OaDocumentRepository documentRepository,
                                         com.hxj.repository.WorkbenchEntryRepository workbenchEntryRepository,
                                         FlowConfigRepository flowConfigRepository) {
        this.templateRepository = templateRepository;
        this.fieldRepository = fieldRepository;
        this.documentRepository = documentRepository;
        this.workbenchEntryRepository = workbenchEntryRepository;
        this.flowConfigRepository = flowConfigRepository;
    }

    // ==================== 管理端 ====================

    @Transactional(readOnly = true)
    public List<TemplateView> list() {
        return templateRepository.findAll().stream()
                .sorted(java.util.Comparator.comparingInt(FormTemplate::getSortOrder))
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateView get(Long id) {
        return toView(require(id));
    }

    @Transactional(readOnly = true)
    public List<TemplateView> listEnabled() {
        return templateRepository.findByStatusOrderBySortOrderAsc("ENABLED").stream()
                .filter(t -> t.getFlowConfigId() != null)
                .map(this::toView)
                .toList();
    }

    private TemplateView toView(FormTemplate template) {
        List<FormField> fields = fieldRepository.findByTemplateIdOrderBySortOrderAsc(template.getId());
        // 钉钉同构：发布状态与版本挂在模板自带流程上，列表直接透出
        com.hxj.entity.FlowConfig flow = template.getFlowConfigId() == null ? null
                : flowConfigRepository.findById(template.getFlowConfigId()).orElse(null);
        return new TemplateView(
                template.getId(), template.getBusinessType(), template.getName(),
                template.getDocPrefix(), template.getFlowConfigId(), template.getVersion(),
                template.getStatus(), template.getCategory(),
                flow == null ? null : flow.getStatus().name(),
                flow == null ? 0 : flow.getVersion(),
                template.getSortOrder(),
                attachmentRequirements(template),
                fields.stream()
                        .map(f -> new FieldView(f.getFieldKey(), f.getLabel(), f.getControlType(),
                                f.isRequired(), parseStringList(f.getOptions()), f.isReserved(),
                                f.getSortOrder(), f.isEnabled()))
                        .toList());
    }

    @Transactional
    public TemplateView create(SaveFormTemplateRequest request) {
        validateTemplatePayload(request);
        // 模板唯一身份 = 模板名（入口展示名）；businessType 是台账分类维度（3 类），
        // 多条流程可同属一类——V27 时代的"业务类型键唯一"会把模板锁死在 3 个，
        // 与 20 条流程的多入口模型冲突，已随模板化 v2 移除
        if (templateRepository.findByName(request.name()).isPresent()) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "模板名称已存在：" + request.name());
        }
        FormTemplate template = new FormTemplate();
        applyTemplate(template, request);
        templateRepository.save(template);
        replaceFields(template, request.fields());
        // 钉钉式生命周期：建模板即自动出现在工作台（事项文案跟随模板名，可由管理员改名细化）
        autoCreateEntry(template);
        return get(template.getId());
    }

    /** 建模板自动生成同名片事项（对齐钉钉"模板上线即出现在审批中心"）。 */
    private void autoCreateEntry(FormTemplate template) {
        WorkbenchEntry entry = new WorkbenchEntry();
        entry.setZone(com.hxj.document.WorkbenchEntryManagementService.zoneFor(template.getBusinessType()));
        entry.setLabel(template.getName());
        entry.setTemplateId(template.getId());
        entry.setAutoCreated(true);
        entry.setSortOrder(workbenchEntryRepository.findAllGroupedByZone().stream()
                .filter(e -> entry.getZone().equals(e.getZone()))
                .mapToInt(WorkbenchEntry::getSortOrder)
                .max().orElse(0) + 1);
        workbenchEntryRepository.save(entry);
    }

    @Transactional
    public TemplateView update(Long id, SaveFormTemplateRequest request) {
        FormTemplate template = require(id);
        validateTemplatePayload(request);
        // 改名冲突检查（排除自身）：与其他模板重名会让工作台入口无法区分
        templateRepository.findByName(request.name())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                            "模板名称已存在：" + request.name());
                });
        Set<String> existingReserved = fieldRepository.findByTemplateIdOrderBySortOrderAsc(id).stream()
                .filter(FormField::isReserved)
                .map(FormField::getFieldKey)
                .collect(Collectors.toSet());
        Set<String> nextKeys = request.fields() == null ? Set.of() : request.fields().stream()
                .map(SaveFormTemplateRequest.FieldPayload::fieldKey)
                .collect(Collectors.toSet());
        Set<String> missing = new HashSet<>(existingReserved);
        missing.removeAll(nextKeys);
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "提升字段不可删除：" + String.join(",", missing));
        }
        applyTemplate(template, request);
        template.setVersion(template.getVersion() + 1);
        templateRepository.save(template);
        replaceFields(template, request.fields());
        // 自动事项文案跟随模板名（读时已按模板名覆盖，此处同步存量值保持一致）
        workbenchEntryRepository.findByTemplateIdAndAutoCreatedTrue(id)
                .forEach(entry -> {
                    entry.setLabel(template.getName());
                    entry.setZone(com.hxj.document.WorkbenchEntryManagementService.zoneFor(template.getBusinessType()));
                    workbenchEntryRepository.save(entry);
                });
        return get(template.getId());
    }

    /**
     * 删除模板：与流程删除同规则——被单据引用（历史单据按模板快照回溯）时拒绝；
     * 工作台事项（自动生成的入口 + 指向本模板的事由快捷）随之级联清除——
     * 模板没了它们就是死链，对齐钉钉"删模板即从审批中心消失"。
     */
    @Transactional
    public void delete(Long id) {
        FormTemplate template = require(id);
        long docRefs = documentRepository.countByFormTemplateId(id);
        if (docRefs > 0) {
            throw new BusinessException(ErrorCodeEnum.FORM_TEMPLATE_IN_USE,
                    "模板已被 " + docRefs + " 张单据引用，无法删除");
        }
        workbenchEntryRepository.deleteByTemplateId(id);
        fieldRepository.deleteByTemplateId(id);
        templateRepository.delete(template);
    }

    // ==================== 提交支撑 ====================

    @Transactional(readOnly = true)
    public FormTemplate requireEnabled(Long templateId) {
        FormTemplate template = require(templateId);
        if (!"ENABLED".equals(template.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.FORM_TEMPLATE_NOT_FOUND, "表单模板已停用");
        }
        return template;
    }

    @Transactional(readOnly = true)
    public List<FormField> fields(Long templateId) {
        return fieldRepository.findByTemplateIdOrderBySortOrderAsc(templateId).stream()
                .filter(FormField::isEnabled)
                .toList();
    }

    /** 按模板校验并归一化字段值：必填缺失/未知 key/类型/SELECT 选项/TABLE 递归。 */
    public Map<String, Object> validateFieldValues(List<FormField> fields, Map<String, Object> raw) {
        Map<String, Object> rawValues = raw == null ? Map.of() : raw;
        Map<String, FormField> byKey = fields.stream()
                .collect(Collectors.toMap(FormField::getFieldKey, Function.identity()));
        for (String key : rawValues.keySet()) {
            if (!byKey.containsKey(key)) {
                throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID, "未知表单字段：" + key);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (FormField field : fields) {
            Object value = rawValues.get(field.getFieldKey());
            boolean blank = value == null || (value instanceof String s && s.isBlank())
                    || (value instanceof List<?> list && list.isEmpty());
            if (blank) {
                if (field.isRequired()) {
                    throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                            "必填字段未填写：" + field.getLabel());
                }
                continue;
            }
            result.put(field.getFieldKey(), normalize(field, value));
        }
        return result;
    }

    /** 提交时冻结的字段定义快照 JSON（仅启用字段）。 */
    public String snapshotJson(List<FormField> fields) {
        try {
            List<Map<String, Object>> snapshot = fields.stream()
                    .map(f -> Map.<String, Object>of(
                            "fieldKey", f.getFieldKey(),
                            "label", f.getLabel(),
                            "controlType", f.getControlType(),
                            "required", f.isRequired()))
                    .toList();
            return MAPPER.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("表单快照序列化失败", e);
        }
    }

    public String valuesJson(Map<String, Object> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("表单值序列化失败", e);
        }
    }

    /** 必附材料清单（模板配置），无配置时返回通用兜底清单。 */
    @Transactional(readOnly = true)
    public List<String> attachmentRequirements(FormTemplate template) {
        if (template.getAttachmentRequirements() == null || template.getAttachmentRequirements().isBlank()) {
            return List.of("关联前置单据", "业务证明资料", "发票", "收款信息");
        }
        try {
            return MAPPER.readValue(template.getAttachmentRequirements(), new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of("关联前置单据", "业务证明资料", "发票", "收款信息");
        }
    }

    /** 详情渲染合并：快照定义 ∪ 提交值 → 有序字段视图。 */
    public List<FieldValueView> mergeFields(String snapshotJson, String valuesJson) {
        List<Map<String, Object>> snapshot = parseMapList(snapshotJson);
        Map<String, Object> values = parseValues(valuesJson);
        List<FieldValueView> result = new ArrayList<>();
        for (Map<String, Object> def : snapshot) {
            String key = String.valueOf(def.get("fieldKey"));
            result.add(new FieldValueView(
                    key,
                    String.valueOf(def.getOrDefault("label", key)),
                    String.valueOf(def.getOrDefault("controlType", "TEXT")),
                    Boolean.TRUE.equals(def.get("required")),
                    values.get(key)));
        }
        return result;
    }

    /** 解析单据的 field_values JSON（无值时返回空 Map）。 */
    public Map<String, Object> parseValues(String valuesJson) {
        if (valuesJson == null || valuesJson.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(valuesJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ==================== 内部 ====================

    private FormTemplate require(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.FORM_TEMPLATE_NOT_FOUND, "表单模板不存在"));
    }

    private void validateTemplatePayload(SaveFormTemplateRequest request) {
        // 业务类型是模板的唯一分类输入：必须为合法枚举——它决定单号前缀（派生）、台账归类与工作台卡片
        boolean validBusinessType = request.businessType() != null && java.util.Arrays
                .stream(com.hxj.enums.BusinessTypeEnum.values())
                .anyMatch(e -> e.name().equals(request.businessType()));
        if (!validBusinessType) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "业务类型非法（可用：DAILY_PAYMENT / BUSINESS_PAYMENT / SEAL_APPLICATION）："
                            + request.businessType());
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID, "模板名称不能为空");
        }
        if (request.fields() == null || request.fields().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID, "模板至少需要一个字段");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (SaveFormTemplateRequest.FieldPayload field : request.fields()) {
            String key = field.fieldKey();
            if (key == null || key.isBlank()) {
                throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID, "字段键不能为空");
            }
            if (SYSTEM_KEYS.contains(key)) {
                throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                        "字段键与系统属性冲突：" + key);
            }
            if (!seen.add(key)) {
                throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID, "字段键重复：" + key);
            }
            if (field.controlType() == null || !CONTROL_TYPES.contains(field.controlType())) {
                throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                        "控件类型非法：" + field.controlType());
            }
            if ("SELECT".equals(field.controlType())
                    && (field.options() == null || field.options().isEmpty())) {
                throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                        "下拉字段必须提供选项：" + field.label());
            }
        }
        // 绑定流程必须真实存在：否则入口会出现、员工填得完单，提交时才报"未配置对应审批流程"
        if (request.flowConfigId() != null && flowConfigRepository.findById(request.flowConfigId()).isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "绑定的流程配置不存在：" + request.flowConfigId());
        }
    }

    private void applyTemplate(FormTemplate template, SaveFormTemplateRequest request) {
        template.setBusinessType(request.businessType());
        template.setName(request.name());
        // 分类与前缀由业务类型唯一派生（收敛"双分类字段/手工前缀"的失配空间）
        template.setCategory(request.businessType());
        template.setDocPrefix(PREFIX_BY_BUSINESS_TYPE.get(request.businessType()));
        template.setFlowConfigId(request.flowConfigId());
        try {
            template.setAttachmentRequirements(request.attachmentRequirements() == null
                    ? null : MAPPER.writeValueAsString(request.attachmentRequirements()));
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID, "必附材料清单序列化失败");
        }
    }

    private void replaceFields(FormTemplate template, List<SaveFormTemplateRequest.FieldPayload> fields) {
        fieldRepository.deleteByTemplateId(template.getId());
        fieldRepository.flush();
        int order = 0;
        for (SaveFormTemplateRequest.FieldPayload payload : fields) {
            FormField field = new FormField();
            field.setFieldKey(payload.fieldKey());
            field.setLabel(payload.label() == null ? payload.fieldKey() : payload.label());
            field.setControlType(payload.controlType());
            field.setRequired(Boolean.TRUE.equals(payload.required()));
            // 提升字段由服务端白名单标记，客户端不感知；排序由数组顺序决定
            field.setReserved(RESERVED_KEYS.contains(payload.fieldKey()));
            field.setSortOrder(++order);
            field.setEnabled(payload.enabled() == null || payload.enabled());
            if (payload.options() != null) {
                try {
                    field.setOptions(MAPPER.writeValueAsString(payload.options()));
                } catch (Exception e) {
                    throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                            "选项序列化失败：" + payload.fieldKey());
                }
            }
            field.setTemplate(template);
            fieldRepository.save(field);
            order++;
        }
    }

    /** 单字段按控件类型归一化与校验。 */
    private Object normalize(FormField field, Object value) {
        return switch (field.getControlType()) {
            case "NUMBER" -> toNumber(field, value);
            case "DATE" -> toDate(field, value);
            case "BOOLEAN" -> toBoolean(field, value);
            case "SELECT" -> toOption(field, value);
            case "IMAGE", "ATTACHMENT" -> toIdList(field, value);
            case "TABLE" -> toTable(field, value);
            default -> String.valueOf(value);
        };
    }

    private BigDecimal toNumber(FormField field, Object value) {
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "字段必须为数字：" + field.getLabel());
        }
    }

    private String toDate(FormField field, Object value) {
        String text = String.valueOf(value);
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text).toString();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "日期格式非法（应为 yyyy-MM-dd）：" + field.getLabel());
        }
    }

    private boolean toBoolean(FormField field, Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        String text = String.valueOf(value);
        if ("true".equalsIgnoreCase(text) || "是".equals(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "否".equals(text) || "0".equals(text)) {
            return false;
        }
        throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                "布尔字段非法：" + field.getLabel());
    }

    private String toOption(FormField field, Object value) {
        String option = String.valueOf(value);
        List<String> options = parseStringList(field.getOptions());
        if (!options.contains(option)) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "选项非法：" + field.getLabel() + "（" + option + "）");
        }
        return option;
    }

    private List<Long> toIdList(FormField field, Object value) {
        if (!(value instanceof List<?> list)) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "附件字段必须为 ID 数组：" + field.getLabel());
        }
        try {
            return list.stream().map(v -> Long.valueOf(String.valueOf(v))).toList();
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "附件 ID 非法：" + field.getLabel());
        }
    }

    /** 明细表：options 为列结构 [{key,label,type,required}]，值为行数组，校验递归到单元格。 */
    private List<Map<String, Object>> toTable(FormField field, Object value) {
        if (!(value instanceof List<?> rows) || rows.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "明细表至少需要一行：" + field.getLabel());
        }
        List<Map<String, Object>> columns = parseMapList(field.getOptions());
        Map<String, Map<String, Object>> columnByKey = columns.stream()
                .collect(Collectors.toMap(c -> String.valueOf(c.get("key")), Function.identity()));
        List<Map<String, Object>> normalized = new ArrayList<>();
        int rowIndex = 1;
        for (Object rowObject : rows) {
            if (!(rowObject instanceof Map<?, ?> rawRow)) {
                throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                        "明细表行格式非法：" + field.getLabel() + " 第 " + rowIndex + " 行");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawRow.entrySet()) {
                String cellKey = String.valueOf(entry.getKey());
                Map<String, Object> column = columnByKey.get(cellKey);
                if (column == null) {
                    throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                            "明细表未知列：" + field.getLabel() + " 第 " + rowIndex + " 行 [" + cellKey + "]");
                }
                String cellType = String.valueOf(column.getOrDefault("type", "TEXT"));
                Object cell = entry.getValue();
                boolean cellBlank = cell == null || (cell instanceof String cs && cs.isBlank());
                if (cellBlank) {
                    if (Boolean.TRUE.equals(column.get("required"))) {
                        throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                                "明细表必填列未填写：" + field.getLabel() + " 第 " + rowIndex + " 行 [" + cellKey + "]");
                    }
                    row.put(cellKey, null);
                    continue;
                }
                row.put(cellKey, "NUMBER".equals(cellType) ? toNumber(field, cell) : String.valueOf(cell));
            }
            for (Map<String, Object> column : columns) {
                if (Boolean.TRUE.equals(column.get("required")) && !row.containsKey(column.get("key"))) {
                    throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                            "明细表必填列未填写：" + field.getLabel() + " 第 " + rowIndex + " 行 [" + column.get("key") + "]");
                }
            }
            normalized.add(row);
            rowIndex++;
        }
        return normalized;
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> parseMapList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 模板视图（flowStatus/flowVersion 为自带流程的发布状态与版本——钉钉同构整体透出）。 */
    public record TemplateView(Long id, String businessType, String name, String docPrefix,
                               Long flowConfigId, int version, String status, String category,
                               String flowStatus, int flowVersion,
                               int sortOrder, List<String> attachmentRequirements,
                               List<FieldView> fields) {
    }

    /** 字段视图（SELECT 选项已解析为数组）。 */
    public record FieldView(String fieldKey, String label, String controlType, boolean required,
                            List<String> options, boolean reserved,
                            int sortOrder, boolean enabled) {
    }

    /** 详情渲染的字段视图（定义 ∪ 值）。 */
    public record FieldValueView(String fieldKey, String label, String controlType,
                                 boolean required, Object value) {
    }
}
