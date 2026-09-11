package com.hxj.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static com.hxj.workflow.NodeAssigneeRuleEnum.DEPT_ROLE;
import static com.hxj.workflow.NodeAssigneeRuleEnum.INITIATOR;
import static com.hxj.workflow.NodeAssigneeRuleEnum.MANAGER;
import static com.hxj.workflow.NodeAssigneeRuleEnum.STATIC_ROLE;

/** 节点指派规则归类测试。 */
class NodeAssigneeRuleEnumTest {

    @Test
    void classifiesInitiatorByNodeNamePrefix() {
        assertThat(NodeAssigneeRuleEnum.of("发起人签收", null)).isEqualTo(INITIATOR);
        assertThat(NodeAssigneeRuleEnum.of("发起人归还", "任意串")).isEqualTo(INITIATOR);
    }

    @Test
    void classifiesManagerByExactAssigneeRole() {
        assertThat(NodeAssigneeRuleEnum.of("直属主管", "直属主管")).isEqualTo(MANAGER);
    }

    @Test
    void classifiesDeptRoleByExactAssigneeRole() {
        assertThat(NodeAssigneeRuleEnum.of("会计（按部门）", "会计（按部门）")).isEqualTo(DEPT_ROLE);
    }

    @Test
    void classifiesStaticRoleOtherwise() {
        assertThat(NodeAssigneeRuleEnum.of("核算会计审批", "核算会计")).isEqualTo(STATIC_ROLE);
        assertThat(NodeAssigneeRuleEnum.of("会计主管&内控", "会计主管&内控/内控专员")).isEqualTo(STATIC_ROLE);
    }

    @Test
    void nullAssigneeIsStatic() {
        assertThat(NodeAssigneeRuleEnum.of("某节点", null)).isEqualTo(STATIC_ROLE);
    }

    @Test
    void staticRoleFlagOnlyForStatic() {
        assertThat(NodeAssigneeRuleEnum.of("直属主管", "直属主管").isStaticRole()).isFalse();
        assertThat(NodeAssigneeRuleEnum.of("核算", "核算会计").isStaticRole()).isTrue();
    }
}
