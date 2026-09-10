package com.hxj.architecture;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构规范自动化约束（ArchUnit）：
 * 把 AGENTS.md 中"靠自觉"的规范变成可执行测试，违反即构建失败。
 * 新增约束规则应同步更新 AGENTS.md 的「自动化约束」小节。
 */
@AnalyzeClasses(packages = "com.hxj")
public class ArchitectureConstraintTest {

    /** 规范 4：Controller 类必须以 Controller 结尾 */
    @ArchTest
    public static final ArchRule controllerClassesShouldBeNamedProperly =
            classes().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .should().haveNameMatching(".*Controller");

    /** 规范 3.1 / 9.4：Controller 公开方法必须返回 ApiResponse 或 ResponseEntity；文件下载用 ResponseEntity<byte[]> 返回字节流，不再豁免 void */
    @ArchTest
    public static final ArchRule controllerMethodsShouldReturnUnifiedResponse =
            methods().that().arePublic().and().areDeclaredInClassesThat().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .should().haveRawReturnType(com.hxj.common.ApiResponse.class)
                    .orShould().haveRawReturnType(org.springframework.http.ResponseEntity.class);

    /** 自定义条件：方法不得声明 HttpServletResponse 类型的参数（接收并手写出响应流会绕过统一响应增强）。 */
    private static final ArchCondition<JavaMethod> notHaveHttpServletResponseParameter =
            new ArchCondition<>("not declare a HttpServletResponse parameter") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    boolean has = method.getParameters().stream()
                            .anyMatch(p -> p.getRawType().isAssignableTo(jakarta.servlet.http.HttpServletResponse.class));
                    if (has) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " declares a HttpServletResponse parameter"));
                    }
                }
            };

    /** 规范 9.4：Controller 不得直接接收 HttpServletResponse 并手写出响应流；下载统一用 ResponseEntity<byte[]> 返回，便于统一错误包装与链路追踪 */
    @ArchTest
    public static final ArchRule controllerMethodsShouldNotWriteResponseDirectly =
            methods().that().areDeclaredInClassesThat().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .should(notHaveHttpServletResponseParameter);

    /** 规范 5.2：Service 层禁止直接依赖 Servlet API（HTTP 概念不得渗入业务层） */
    @ArchTest
    public static final ArchRule servicesShouldNotDependOnServletApi =
            noClasses().that().resideInAPackage("..hxj.service..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..");

    /** 规范 5.2：业务异常必须经由 GlobalExceptionHandler 统一转换，Controller 不得 try-catch 吞异常后自行返回错误响应 */
    @ArchTest
    public static final ArchRule controllersShouldNotSwallowExceptions =
            noClasses().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                     .should().callMethod(com.hxj.common.ApiResponse.class, "error",
                             com.hxj.common.ErrorCodeEnum.class, String.class);

    /**
     * 规范 11.1：JPA 实体必须是 class。
     *
     * <p>record 隐式 final，Hibernate 无法为其生成懒加载代理子类，会导致 LazyInitializationException。
     */
    @ArchTest
    public static final ArchRule entitiesShouldNotBeRecords =
            classes().that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().notBeRecords();

    /**
     * 规范 11.1：Spring 受管 Bean 必须是 class。
     *
     * <p>record 为 final，CGLIB 无法代理，@Transactional / @Aspect / @Idempotent 会静默失效。
     * 使用元注解匹配，覆盖 @Component 及 @Service、@Controller、@Configuration、@Aspect 等派生注解。
     */
    @ArchTest
    public static final ArchRule springManagedBeansShouldNotBeRecords =
            classes().that().areMetaAnnotatedWith(org.springframework.stereotype.Component.class)
                    .should().notBeRecords();

    /**
     * 规范 11.1：DTO 必须是 record，无例外。
     *
     * <p>分页查询请求曾因复用 {@code page}/{@code size} 而退化为可变 class，
     * 现统一实现 {@link PageableRequest} 接口复用逻辑，因此不存在任何例外。
     */
    @ArchTest
    public static final ArchRule dataTransferObjectsShouldBeRecords =
            classes().that()
                    .haveNameMatching(".*(Request|Response)$")
                    .and().areNotInterfaces()
                    .should().beRecords();

    /**
     * 规范 4：枚举类名统一以 Enum 结尾（含嵌套枚举，无豁免）。
     *
     * <p>项目曾出现「部分枚举带 Enum 后缀、部分不带」的半改状态，本规则用于固化统一命名，防止再次漂移。
     * 注意：仅约束类名，枚举字段的字段名与数据库列名无关，改名不涉及数据迁移。
     */
    @ArchTest
    public static final ArchRule enumsShouldEndWithEnumSuffix =
            classes().that().areEnums()
                    .should().haveSimpleNameEndingWith("Enum");

    /**
     * 第 4 章：DTO 后缀只允许 Request / Response 两种，禁止中间态后缀。
     *
     * <p>历史上 Item/Summary/Detail/Query/Criteria/Result/Node/Stat 等后缀都能触发
     * {@code dataTransferObjectsShouldBeRecords}，导致「命名 → 规则」的映射多达十种，
     * 一个类名要同时满足多个约束。现统一收敛：作为 HTTP 契约的用 Request/Response，
     * 不作为独立契约的内部结构直接去掉后缀（如 {@code AttachmentItem -> Attachment}）。
     */
    @ArchTest
    public static final ArchRule noLegacyDtoSuffixes =
            classes().should().haveNameNotMatching(".*(Item|Summary|Detail|Query|Criteria|Result|Node|Stat|Stats)$");
}
