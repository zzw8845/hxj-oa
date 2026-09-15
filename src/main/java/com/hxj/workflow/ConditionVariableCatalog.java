package com.hxj.workflow;

import com.hxj.entity.FormField;
import com.hxj.repository.FormFieldRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件变量目录——两档（钉钉同构）：模板启用字段 + 系统字段。
 *
 * <p>钉钉的条件分支可以引用表单里的任意字段——这里同样放开为<b>模板全部启用字段</b>
 * （类型由控件类型推导），不再需要"参与流程条件"的白名单勾选，也没有保留键兜底
 * （保留键的"值写结构化列"提升身份与条件判据无关，保留在模板层）。
 * 保存关口据此校验条件边，保证「校验通过 ⟹ 运行时可求值」。
 */
@Component
public class ConditionVariableCatalog {

    /** 来源标记：模板字段。 */
    public static final String SOURCE_FORM_FIELD = "FORM_FIELD";
    /** 来源标记：系统字段。 */
    public static final String SOURCE_SYSTEM = "SYSTEM";

    /** 系统字段展示顺序（常量集合本身无序，展示与错误提示都需稳定顺序）。 */
    private static final List<String> SYSTEM_ORDER = List.of(
            WorkflowVariables.SYS_INITIATOR,
            WorkflowVariables.SYS_INITIATOR_DEPT_ID,
            WorkflowVariables.SYS_INITIATOR_DEPT_NAME,
            WorkflowVariables.SYS_INITIATOR_POST);

    /** 系统字段中文展示名。 */
    private static final Map<String, String> SYSTEM_LABELS = Map.of(
            WorkflowVariables.SYS_INITIATOR, "发起人账号（系统）",
            WorkflowVariables.SYS_INITIATOR_DEPT_ID, "发起人部门ID（系统）",
            WorkflowVariables.SYS_INITIATOR_DEPT_NAME, "发起人部门名（系统）",
            WorkflowVariables.SYS_INITIATOR_POST, "发起人岗位（系统）");

    private final FormFieldRepository fieldRepository;

    public ConditionVariableCatalog(FormFieldRepository fieldRepository) {
        this.fieldRepository = fieldRepository;
    }

    /** 该模板可用的条件变量（变量名 → 类型，稳定顺序）；{@code templateId} 为空仅返回系统字段。 */
    @Transactional(readOnly = true)
    public Map<String, WorkflowVariables.ValueTypeEnum> available(Long templateId) {
        Map<String, WorkflowVariables.ValueTypeEnum> variables = new LinkedHashMap<>();
        for (FormField field : enabledFields(templateId)) {
            variables.putIfAbsent(field.getFieldKey(),
                    WorkflowVariables.valueTypeOfControlType(field.getControlType()));
        }
        SYSTEM_ORDER.forEach(key -> variables.putIfAbsent(key, WorkflowVariables.systemVariableType(key)));
        return variables;
    }

    /** 条件变量目录（带展示名与来源），供管理界面下拉直接渲染。 */
    @Transactional(readOnly = true)
    public List<FlowConfigItems.VariableOption> describe(Long templateId) {
        Map<String, FlowConfigItems.VariableOption> options = new LinkedHashMap<>();
        for (FormField field : enabledFields(templateId)) {
            options.putIfAbsent(field.getFieldKey(), new FlowConfigItems.VariableOption(
                    field.getFieldKey(),
                    field.getLabel() == null || field.getLabel().isBlank()
                            ? field.getFieldKey() : field.getLabel(),
                    WorkflowVariables.valueTypeOfControlType(field.getControlType()).name(),
                    SOURCE_FORM_FIELD));
        }
        for (String key : SYSTEM_ORDER) {
            WorkflowVariables.ValueTypeEnum type = WorkflowVariables.systemVariableType(key);
            options.putIfAbsent(key, new FlowConfigItems.VariableOption(
                    key,
                    SYSTEM_LABELS.getOrDefault(key, key),
                    type == null ? WorkflowVariables.ValueTypeEnum.STRING.name() : type.name(),
                    SOURCE_SYSTEM));
        }
        return List.copyOf(options.values());
    }

    /** 模板的全部启用字段（钉钉：条件可引用表单任意字段）。 */
    private List<FormField> enabledFields(Long templateId) {
        if (templateId == null) {
            return List.of();
        }
        return new ArrayList<>(fieldRepository.findByTemplateIdOrderBySortOrderAsc(templateId).stream()
                .filter(FormField::isEnabled)
                .toList());
    }
}
