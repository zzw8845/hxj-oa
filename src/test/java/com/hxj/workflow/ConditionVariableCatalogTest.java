package com.hxj.workflow;

import com.hxj.entity.FormField;
import com.hxj.entity.FormTemplate;
import com.hxj.repository.FormFieldRepository;
import com.hxj.repository.FormTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 条件变量目录测试：字段即变量的判据集合来源与优先级。
 */
@ExtendWith(MockitoExtension.class)
class ConditionVariableCatalogTest {

    @Mock
    private FormTemplateRepository templateRepository;

    @Mock
    private FormFieldRepository fieldRepository;

    private ConditionVariableCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new ConditionVariableCatalog(templateRepository, fieldRepository);
    }

    private static FormField field(String key, String label, String controlType) {
        FormField f = new FormField();
        f.setFieldKey(key);
        f.setLabel(label);
        f.setControlType(controlType);
        f.setProcessVariable(true);
        f.setEnabled(true);
        return f;
    }

    /** FormTemplate 的 ID 只有 getter（无 setter），反射注入主键。 */
    private static FormTemplate template(long id) {
        FormTemplate t = new FormTemplate();
        org.springframework.test.util.ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    @Test
    void shouldExposeTemplateProcessVariablesFirstWithFieldLabel() {
        when(templateRepository.findByFlowConfigId(1L)).thenReturn(List.of(template(7L)));
        when(fieldRepository.findByTemplateIdOrderBySortOrderAsc(7L)).thenReturn(List.of(
                field("company", "所属公司", "SELECT"),
                field("amount", "金额", "NUMBER")));

        List<FlowConfigItems.VariableOption> options = catalog.describe(1L);

        // 业务字段排最前，展示名用字段 label，来源标记为模板字段
        assertThat(options).first().satisfies(o -> {
            assertThat(o.value()).isEqualTo("company");
            assertThat(o.label()).isEqualTo("所属公司");
            assertThat(o.source()).isEqualTo(ConditionVariableCatalog.SOURCE_FORM_FIELD);
        });
        // 同名字段以控件类型为准：amount 覆盖保留键兜底，来源是模板字段而非 RESERVED_KEY
        assertThat(options).filteredOn(o -> o.value().equals("amount")).first().satisfies(o -> {
            assertThat(o.valueType()).isEqualTo("NUMERIC");
            assertThat(o.source()).isEqualTo(ConditionVariableCatalog.SOURCE_FORM_FIELD);
        });
        // 系统字段与保留键兜底始终存在
        assertThat(options).extracting(FlowConfigItems.VariableOption::value)
                .contains("sysInitiator", "sysInitiatorDeptId", "involvesFunds", "needPostMaterial");
    }

    @Test
    void shouldAlwaysExposeSystemVariablesAndReservedKeysWithoutBoundTemplate() {
        List<FlowConfigItems.VariableOption> options = catalog.describe(null);

        assertThat(options).extracting(FlowConfigItems.VariableOption::value)
                .contains("sysInitiator", "sysInitiatorDeptId", "sysInitiatorDeptName",
                        "sysInitiatorPost", "amount", "involvesFunds",
                        "requiresAdminReview", "businessMode", "needPostMaterial");
        assertThat(options).allSatisfy(o ->
                assertThat(o.source()).isNotEqualTo(ConditionVariableCatalog.SOURCE_FORM_FIELD));
    }

    @Test
    void shouldSkipDisabledOrNonProcessFields() {
        FormField disabled = field("contractNo", "合同编号", "TEXT");
        disabled.setEnabled(false);
        FormField notProcess = field("reason", "事由明细", "TEXTAREA");
        notProcess.setProcessVariable(false);

        when(templateRepository.findByFlowConfigId(2L)).thenReturn(List.of(template(7L)));
        when(fieldRepository.findByTemplateIdOrderBySortOrderAsc(7L))
                .thenReturn(List.of(disabled, notProcess));

        List<FlowConfigItems.VariableOption> options = catalog.describe(2L);

        assertThat(options).extracting(FlowConfigItems.VariableOption::value)
                .doesNotContain("contractNo", "reason");
    }

    @Test
    void availableShouldMapVariableNameToValueTypeConsistently() {
        when(templateRepository.findByFlowConfigId(1L)).thenReturn(List.of(template(7L)));
        when(fieldRepository.findByTemplateIdOrderBySortOrderAsc(7L)).thenReturn(List.of(
                field("contractAmount", "合同金额", "NUMBER"),
                field("urgent", "加急", "BOOLEAN")));

        var available = catalog.available(1L);

        assertThat(available)
                .containsEntry("contractAmount", WorkflowVariables.ValueTypeEnum.NUMERIC)
                .containsEntry("urgent", WorkflowVariables.ValueTypeEnum.BOOLEAN)
                .containsEntry("amount", WorkflowVariables.ValueTypeEnum.NUMERIC)
                .containsEntry("sysInitiatorDeptId", WorkflowVariables.ValueTypeEnum.NUMERIC)
                .containsEntry("businessMode", WorkflowVariables.ValueTypeEnum.STRING);
    }
}
