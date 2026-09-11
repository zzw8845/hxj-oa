package com.hxj.workflow;

import com.hxj.enums.ConditionOperatorEnum;
import com.hxj.enums.FlowCategoryEnum;
import com.hxj.entity.FlowConditionRule;
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

    @BeforeEach
    void setUp() {
        flowConfigRepository.deleteAll();
        config = new FlowConfig();
        config.setType("采购申请");
        config.setCategory(FlowCategoryEnum.DAILY);
        config.addNode(new FlowNodeConfig("发起人", FlowNodeTypeEnum.START));
        FlowNodeConfig manager = new FlowNodeConfig("直属主管", FlowNodeTypeEnum.APPROVAL);
        manager.setAssigneeRole("二级部门负责人");
        config.addNode(manager);
        FlowNodeConfig gm = new FlowNodeConfig("执行总经理（≥2万元）", FlowNodeTypeEnum.APPROVAL);
        gm.setAssigneeRole("执行总经理");
        config.addNode(gm);
        config.addNode(new FlowNodeConfig("抄送财务", FlowNodeTypeEnum.CC));
        config.addConditionRule(new FlowConditionRule(
                "amount", ConditionOperatorEnum.GREATER_THAN_OR_EQUAL, "20000", "执行总经理（≥2万元）"));
        flowConfigRepository.saveAndFlush(config);
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
                        && "二级部门负责人".equals(link.getGroupId()));
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
    void shouldRouteDeptAccountantNodeToResolvedVariableAndStayUnassignedWhenAbsent() {
        flowConfigRepository.deleteAll();
        FlowConfig cfg = new FlowConfig();
        cfg.setType("费用报销");
        cfg.setCategory(FlowCategoryEnum.DAILY);
        cfg.addNode(new FlowNodeConfig("发起人", FlowNodeTypeEnum.START));
        FlowNodeConfig accountant = new FlowNodeConfig("会计（按部门）", FlowNodeTypeEnum.APPROVAL);
        accountant.setAssigneeRole("会计（按部门）");
        cfg.addNode(accountant);
        flowConfigRepository.saveAndFlush(cfg);

        definitionService.deploy(cfg.getId());

        // 有核算分工解析结果：动态指派给 ${deptAccountant}
        String withMapping = workflowService.startProcess(
                cfg.getId(), 2001L, Map.of("deptAccountant", "wengtingting"));
        assertThat(singleTask(withMapping).getAssignee()).isEqualTo("wengtingting");

        runtimeService.deleteProcessInstance(withMapping, "cleanup");

        // 无分工映射：生产路径总是写入空串变量 → 任务保持未指派，待管理员人工指派
        String withoutMapping = workflowService.startProcess(cfg.getId(), 2002L, Map.of("deptAccountant", ""));
        String unassigned = singleTask(withoutMapping).getAssignee();
        assertThat(unassigned == null || unassigned.isBlank()).isTrue();
    }
}
