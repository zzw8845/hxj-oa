package com.hxj.workflow;

import com.hxj.entity.FormField;
import com.hxj.repository.FormFieldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 条件变量目录测试（两档：模板启用字段 + 系统字段；无保留键兜底）。
 */
@ExtendWith(MockitoExtension.class)
class ConditionVariableCatalogTest {

    @Mock
    private FormFieldRepository fieldRepository;

    private ConditionVariableCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new ConditionVariableCatalog(fieldRepository);
    }

    private static FormField field(String key, String label, String controlType) {
        FormField f = new FormField();
        f.setFieldKey(key);
        f.setLabel(label);
        f.setControlType(controlType);
        f.setEnabled(true);
        return f;
    }

    @Test
    void shouldExposeAllEnabledTemplateFieldsWithSystemVariables() {
        when(fieldRepository.findByTemplateIdOrderBySortOrderAsc(1L)).thenReturn(List.of(
                field("company", "所属公司", "SELECT"),
                field("amount", "金额", "NUMBER")));

        List<FlowConfigItems.VariableOption> options = catalog.describe(1L);

        assertThat(options).first().satisfies(o -> {
            assertThat(o.value()).isEqualTo("company");
            assertThat(o.label()).isEqualTo("所属公司");
            assertThat(o.source()).isEqualTo(ConditionVariableCatalog.SOURCE_FORM_FIELD);
        });
        assertThat(options).extracting(FlowConfigItems.VariableOption::value)
                .contains("company", "amount", "sysInitiator", "sysInitiatorDeptId");
        // 两档化：保留键不再天然可做判据（模板没有对应字段就不存在）
        assertThat(options).extracting(FlowConfigItems.VariableOption::value)
                .doesNotContain("needPostMaterial", "businessMode");
    }

    @Test
    void shouldSkipDisabledFields() {
        FormField disabled = field("contractNo", "合同编号", "TEXT");
        disabled.setEnabled(false);
        when(fieldRepository.findByTemplateIdOrderBySortOrderAsc(2L)).thenReturn(List.of(disabled));

        assertThat(catalog.describe(2L)).extracting(FlowConfigItems.VariableOption::value)
                .doesNotContain("contractNo");
    }

    @Test
    void shouldReturnSystemVariablesOnlyWithoutTemplate() {
        assertThat(catalog.describe(null)).extracting(FlowConfigItems.VariableOption::value)
                .containsExactly("sysInitiator", "sysInitiatorDeptId",
                        "sysInitiatorDeptName", "sysInitiatorPost");
    }

    @Test
    void availableShouldMapVariableNameToValueType() {
        when(fieldRepository.findByTemplateIdOrderBySortOrderAsc(1L)).thenReturn(List.of(
                field("contractAmount", "合同金额", "NUMBER"),
                field("urgent", "加急", "BOOLEAN")));

        var available = catalog.available(1L);

        assertThat(available)
                .containsEntry("contractAmount", WorkflowVariables.ValueTypeEnum.NUMERIC)
                .containsEntry("urgent", WorkflowVariables.ValueTypeEnum.BOOLEAN)
                .containsEntry("sysInitiatorDeptId", WorkflowVariables.ValueTypeEnum.NUMERIC);
    }
}
