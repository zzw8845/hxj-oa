package com.hxj.workflow;

import com.hxj.entity.FormField;
import com.hxj.entity.FormTemplate;
import com.hxj.repository.FormFieldRepository;
import com.hxj.repository.FormTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件变量目录——回答"这条流程的分支可以用哪些字段做判据"。
 *
 * <p>钉钉同构的落点：判据集合 = <b>该流程绑定模板中声明"参与流程条件"的字段</b>
 * （{@link FormField#isProcessVariable()}），类型由控件类型推导。
 * 管理员在模板里勾选一个字段，流程分支就多一个可用判据——<b>不改代码、不上白名单</b>。
 *
 * <p>来源三档（{@link #describe} 按此顺序返回，业务字段优先）：
 * <ol>
 *   <li>{@code FORM_FIELD}：模板勾选字段（业务判据，最常用）</li>
 *   <li>{@code SYSTEM}：发起人/发起人部门/岗位——任何流程都可用</li>
 *   <li>{@code RESERVED_KEY}：保留键兜底（模板未定义同名字段时仍可配置）</li>
 * </ol>
 */
@Component
public class ConditionVariableCatalog {

    /** 来源标记：模板勾选字段。 */
    public static final String SOURCE_FORM_FIELD = "FORM_FIELD";
    /** 来源标记：系统字段。 */
    public static final String SOURCE_SYSTEM = "SYSTEM";
    /** 来源标记：保留键兜底。 */
    public static final String SOURCE_RESERVED_KEY = "RESERVED_KEY";

    /** 系统字段展示顺序（常量集合本身无序，展示与错误提示都需稳定顺序）。 */
    private static final List<String> SYSTEM_ORDER = List.of(
            WorkflowVariables.SYS_INITIATOR,
            WorkflowVariables.SYS_INITIATOR_DEPT_ID,
            WorkflowVariables.SYS_INITIATOR_DEPT_NAME,
            WorkflowVariables.SYS_INITIATOR_POST);

    /** 保留键展示顺序。 */
    private static final List<String> RESERVED_ORDER = List.of(
            "amount", "involvesFunds", "requiresAdminReview", "businessMode", "needPostMaterial");

    /** 非模板字段的中文展示名（模板字段优先用字段自身的 label）。 */
    private static final Map<String, String> FALLBACK_LABELS = Map.ofEntries(
            Map.entry(WorkflowVariables.SYS_INITIATOR, "发起人账号（系统）"),
            Map.entry(WorkflowVariables.SYS_INITIATOR_DEPT_ID, "发起人部门ID（系统）"),
            Map.entry(WorkflowVariables.SYS_INITIATOR_DEPT_NAME, "发起人部门名（系统）"),
            Map.entry(WorkflowVariables.SYS_INITIATOR_POST, "发起人岗位（系统）"),
            Map.entry("amount", "金额（保留键）"),
            Map.entry("involvesFunds", "涉及资金（保留键）"),
            Map.entry("requiresAdminReview", "需行政复核（保留键）"),
            Map.entry("businessMode", "业务模式（保留键）"),
            Map.entry("needPostMaterial", "后置补材料（保留键）"));

    private final FormTemplateRepository templateRepository;
    private final FormFieldRepository fieldRepository;

    public ConditionVariableCatalog(FormTemplateRepository templateRepository,
                                    FormFieldRepository fieldRepository) {
        this.templateRepository = templateRepository;
        this.fieldRepository = fieldRepository;
    }

    /** 该流程可用的条件变量（变量名 → 类型，保持稳定顺序）；{@code flowConfigId} 为空仅返回兜底部分。 */
    @Transactional(readOnly = true)
    public Map<String, WorkflowVariables.ValueTypeEnum> available(Long flowConfigId) {
        Map<String, WorkflowVariables.ValueTypeEnum> variables = new LinkedHashMap<>();
        for (FormField field : processFields(flowConfigId)) {
            variables.putIfAbsent(field.getFieldKey(),
                    WorkflowVariables.valueTypeOfControlType(field.getControlType()));
        }
        SYSTEM_ORDER.forEach(key -> variables.putIfAbsent(key, WorkflowVariables.systemVariableType(key)));
        RESERVED_ORDER.forEach(key -> variables.putIfAbsent(key, WorkflowVariables.reservedKeyType(key)));
        return variables;
    }

    /** 条件变量目录（带展示名与来源），供管理界面下拉直接渲染。 */
    @Transactional(readOnly = true)
    public List<FlowConfigItems.VariableOption> describe(Long flowConfigId) {
        Map<String, FlowConfigItems.VariableOption> options = new LinkedHashMap<>();
        for (FormField field : processFields(flowConfigId)) {
            options.putIfAbsent(field.getFieldKey(), new FlowConfigItems.VariableOption(
                    field.getFieldKey(),
                    field.getLabel() == null || field.getLabel().isBlank()
                            ? field.getFieldKey() : field.getLabel(),
                    WorkflowVariables.valueTypeOfControlType(field.getControlType()).name(),
                    SOURCE_FORM_FIELD));
        }
        for (String key : SYSTEM_ORDER) {
            options.putIfAbsent(key, option(key, WorkflowVariables.systemVariableType(key), SOURCE_SYSTEM));
        }
        for (String key : RESERVED_ORDER) {
            if (WorkflowVariables.RESERVED_KEY_VARIABLES.contains(key)) {
                options.putIfAbsent(key,
                        option(key, WorkflowVariables.reservedKeyType(key), SOURCE_RESERVED_KEY));
            }
        }
        return List.copyOf(options.values());
    }

    /** 该流程绑定模板中声明"参与流程条件"且已启用的字段（按模板与字段排序）。 */
    private List<FormField> processFields(Long flowConfigId) {
        if (flowConfigId == null) {
            return List.of();
        }
        List<FormField> fields = new ArrayList<>();
        for (FormTemplate template : templateRepository.findByFlowConfigId(flowConfigId)) {
            for (FormField field : fieldRepository
                    .findByTemplateIdOrderBySortOrderAsc(template.getId())) {
                if (field.isProcessVariable() && field.isEnabled()) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    /** 兜底变量的目录项（展示名取固定中文名，类型缺失按字符串处理）。 */
    private FlowConfigItems.VariableOption option(
            String key, WorkflowVariables.ValueTypeEnum type, String source) {
        return new FlowConfigItems.VariableOption(
                key,
                FALLBACK_LABELS.getOrDefault(key, key),
                type == null ? WorkflowVariables.ValueTypeEnum.STRING.name() : type.name(),
                source);
    }
}
