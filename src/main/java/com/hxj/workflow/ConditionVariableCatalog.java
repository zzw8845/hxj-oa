package com.hxj.workflow;

import com.hxj.entity.FormField;
import com.hxj.entity.FormTemplate;
import com.hxj.repository.FormFieldRepository;
import com.hxj.repository.FormTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 条件变量目录——回答"这条流程的分支可以用哪些字段做判据"。
 *
 * <p>钉钉同构的落点：判据集合 = <b>该流程绑定模板中声明"参与流程条件"的字段</b>
 * （{@link FormField#isProcessVariable()}），类型由控件类型推导。
 * 管理员在模板里勾选一个字段，流程分支就多一个可用判据——<b>不改代码、不上白名单</b>。
 *
 * <p>保留键（amount / involvesFunds / requiresAdminReview / businessMode / needPostMaterial）
 * 作为兜底始终可用：新建流程尚未绑定模板时仍能配置条件，存量流程也不因模板调整而失效。
 */
@Component
public class ConditionVariableCatalog {

    private final FormTemplateRepository templateRepository;
    private final FormFieldRepository fieldRepository;

    public ConditionVariableCatalog(FormTemplateRepository templateRepository,
                                    FormFieldRepository fieldRepository) {
        this.templateRepository = templateRepository;
        this.fieldRepository = fieldRepository;
    }

    /** 该流程可用的条件变量（变量名 → 类型，保持插入顺序）；{@code flowConfigId} 为空仅返回保留键兜底。 */
    @Transactional(readOnly = true)
    public Map<String, WorkflowVariables.ValueTypeEnum> available(Long flowConfigId) {
        Map<String, WorkflowVariables.ValueTypeEnum> variables = new LinkedHashMap<>();
        if (flowConfigId != null) {
            for (FormTemplate template : templateRepository.findByFlowConfigId(flowConfigId)) {
                for (FormField field : fieldRepository
                        .findByTemplateIdOrderBySortOrderAsc(template.getId())) {
                    if (field.isProcessVariable() && field.isEnabled()) {
                        variables.putIfAbsent(field.getFieldKey(),
                                WorkflowVariables.valueTypeOfControlType(field.getControlType()));
                    }
                }
            }
        }
        // 系统字段（钉钉同款）：发起人/发起人部门/岗位——可直接用于"按发起人部门分流"型条件
        WorkflowVariables.SYSTEM_VARIABLES.forEach(key -> variables.putIfAbsent(
                key, WorkflowVariables.systemVariableType(key)));
        WorkflowVariables.RESERVED_KEY_VARIABLES.forEach(key -> variables.putIfAbsent(
                key, WorkflowVariables.reservedKeyType(key)));
        return variables;
    }
}
