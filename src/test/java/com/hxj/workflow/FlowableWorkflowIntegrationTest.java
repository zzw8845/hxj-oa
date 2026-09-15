package com.hxj.workflow;

import com.hxj.enums.ConditionOperatorEnum;
import com.hxj.enums.FlowCategoryEnum;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.repository.FlowConfigRepository;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flowable-oa;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "flowable.database-schema-update=create-drop",
        "flowable.async-executor-activate=false",
        "flowable.async-history-executor-activate=false",
        "flowable.app.enabled=false",
        "flowable.cmmn.enabled=false",
        "flowable.dmn.enabled=false",
        "flowable.idm.enabled=false",
        "flowable.eventregistry.enabled=false"
})
class FlowableWorkflowIntegrationTest {

    @Autowired private ConfigDrivenProcessDefinitionService definitionService;
    @Autowired private OaWorkflowService workflowService;
    @Autowired private FlowConfigRepository flowConfigRepository;
    @Autowired private RepositoryService repositoryService;
    @Autowired private RuntimeService runtimeService;
    @Autowired private TaskService taskService;
    @Autowired private HistoryService historyService;

    private FlowConfig config;

    private static final String MANAGER_GROUP_ID = "501";
    private static final String CEO_GROUP_ID = "502";

    @BeforeEach
    void setUp() {
        flowConfigRepository.deleteAll();
        config = new FlowConfig();
        config.setType("采购申请");
        config.setCategory(FlowCategoryEnum.DAILY);
        config.addNode(new FlowNodeConfig("发起人", FlowNodeTypeEnum.START));
        FlowNodeConfig manager = new FlowNodeConfig("直属主管", FlowNodeTypeEnum.APPROVAL);
        manager.setAssigneeSubject(com.hxj.enums.AssigneeSubjectEnum.ROLE);
        manager.setAssigneeValue(MANAGER_GROUP_ID);
        config.addNode(manager);
        FlowNodeConfig gm = new FlowNodeConfig("执行总经理（≥2万元）", FlowNodeTypeEnum.APPROVAL);
        gm.setAssigneeSubject(com.hxj.enums.AssigneeSubjectEnum.ROLE);
        gm.setAssigneeValue(CEO_GROUP_ID);
        config.addNode(gm);
        config.addNode(new FlowNodeConfig("抄送财务", FlowNodeTypeEnum.CC));
        // 图模型：发起人→主管；主管→(金额≥2万)→执行总经理，默认→抄送；执行总经理→抄送
        FlowNodeConfig startNode = config.getNodes().get(0);
        FlowNodeConfig managerNode = config.getNodes().get(1);
        FlowNodeConfig gmNode = config.getNodes().get(2);
        FlowNodeConfig ccNode = config.getNodes().get(3);
        config.addTransition(transition(startNode, managerNode, null, null, null));
        config.addTransition(transition(managerNode, gmNode,
                "amount", ConditionOperatorEnum.GREATER_THAN_OR_EQUAL, "20000"));
        config.addTransition(transition(managerNode, ccNode, null, null, null));
        config.addTransition(transition(gmNode, ccNode, null, null, null));
        flowConfigRepository.saveAndFlush(config);
    }

    private com.hxj.entity.FlowTransition transition(
            FlowNodeConfig from, FlowNodeConfig to,
            String variable, ConditionOperatorEnum operator, String expectedValue) {
        com.hxj.entity.FlowTransition transition = new com.hxj.entity.FlowTransition();
        transition.setFromNode(from);
        transition.setToNode(to);
        transition.setConditionVariable(variable);
        transition.setOperator(operator);
        transition.setExpectedValue(expectedValue);
        return transition;
    }

    @Test
    void shouldDeployConfigAndFlowThroughHighAmountBranchAndCc() {
        String definitionId = definitionService.deploy(config.getId());
        assertThat(repositoryService.getProcessDefinition(definitionId)).isNotNull();

        String processInstanceId = workflowService.startProcess(
                config.getId(), 1001L, Map.of("amount", 30000));

        Task managerTask = singleTask(processInstanceId);
        assertThat(managerTask.getName()).isEqualTo("直属主管");
        assertThat(taskService.getIdentityLinksForTask(managerTask.getId()))
                .anyMatch(link -> "candidate".equals(link.getType())
                        && MANAGER_GROUP_ID.equals(link.getGroupId()));
        workflowService.completeTask(managerTask.getId(), Map.of());

        Task gmTask = singleTask(processInstanceId);
        assertThat(gmTask.getName()).isEqualTo("执行总经理（≥2万元）");
        workflowService.completeTask(gmTask.getId(), Map.of());

        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult()).isNull();
        HistoricProcessInstance history = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        assertThat(history.getEndTime()).isNotNull();
        assertThat(workflowService.history(processInstanceId))
                .extracting(WorkflowHistoryItemResponse::nodeName)
                .contains("直属主管", "执行总经理（≥2万元）", "抄送财务");
    }

    @Test
    void shouldSkipHighAmountNodeWhenConditionDoesNotMatch() {
        definitionService.deploy(config.getId());
        String processInstanceId = workflowService.startProcess(
                config.getId(), 1002L, Map.of("amount", 1000));

        workflowService.completeTask(singleTask(processInstanceId).getId(), Map.of());

        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult()).isNull();
        List<WorkflowHistoryItemResponse> history = workflowService.history(processInstanceId);
        assertThat(history).extracting(WorkflowHistoryItemResponse::nodeName)
                .doesNotContain("执行总经理（≥2万元）")
                .contains("抄送财务");
    }

    private Task singleTask(String processInstanceId) {
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .includeIdentityLinks()
                .singleResult();
    }

    @Test
    void shouldRouteDeptScopedRoleNodeToResolvedCandidates() {
        FlowConfig cfg = deptScopedRoleFlow(null);
        definitionService.deploy(cfg.getId());

        // 候选人在提交时解析写入节点级变量：多实例消费（或签=任一通过即过）
        String candidatesVar = WorkflowVariables.candidatesVariable(cfg.getNodes().get(1).getId());
        String withMapping = workflowService.startProcess(
                cfg.getId(), 2001L, Map.of(candidatesVar, List.of("wengtingting")));
        assertThat(singleTask(withMapping).getAssignee()).isEqualTo("wengtingting");

        runtimeService.deleteProcessInstance(withMapping, "cleanup");
    }

    @Test
    void shouldSkipNodeWhenEmptyStrategyIsAutoPass() {
        // 空策略=自动通过：提交关口写入跳过变量，引擎按 skipExpression 跳过该节点（钉钉同款）
        FlowConfig cfg = deptScopedRoleFlow(com.hxj.enums.EmptyAssigneeStrategyEnum.AUTO_PASS);
        definitionService.deploy(cfg.getId());

        String candidatesVar = WorkflowVariables.candidatesVariable(cfg.getNodes().get(1).getId());
        String skipped = workflowService.startProcess(cfg.getId(), 2002L, Map.of(
                candidatesVar, List.of(),
                WorkflowVariables.skipVariable(cfg.getNodes().get(1).getId()), Boolean.TRUE,
                com.hxj.workflow.AssigneeResolver.SKIP_EXPRESSION_ENABLED_VARIABLE, Boolean.TRUE));

        assertThat(taskService.createTaskQuery().processInstanceId(skipped).count()).isZero();
    }

    /** 角色 + 发起人部门范围节点（钉钉"角色管理范围"，即"会计按部门"）。 */
    private FlowConfig deptScopedRoleFlow(com.hxj.enums.EmptyAssigneeStrategyEnum emptyStrategy) {
        flowConfigRepository.deleteAll();
        FlowConfig cfg = new FlowConfig();
        cfg.setType("费用报销");
        cfg.setCategory(FlowCategoryEnum.DAILY);
        cfg.addNode(new FlowNodeConfig("发起人", FlowNodeTypeEnum.START));
        FlowNodeConfig accountant = new FlowNodeConfig("会计（按部门）", FlowNodeTypeEnum.APPROVAL);
        accountant.setAssigneeSubject(com.hxj.enums.AssigneeSubjectEnum.ROLE);
        accountant.setAssigneeValue("33");
        accountant.setAssigneeScope(com.hxj.enums.AssigneeScopeEnum.INITIATOR_DEPT);
        accountant.setEmptyStrategy(emptyStrategy);
        cfg.addNode(accountant);
        cfg.addTransition(transition(cfg.getNodes().get(0), accountant, null, null, null));
        return flowConfigRepository.saveAndFlush(cfg);
    }
}
