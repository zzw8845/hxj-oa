package com.hxj.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构规范自动化约束（ArchUnit）：
 * 把 AGENTS.md 中"靠自觉"的规范变成可执行测试，违反即构建失败。
 * 新增约束规则应同步更新 AGENTS.md 的「自动化约束」小节。
 */
@AnalyzeClasses(packages = "com.hxj")
class ArchitectureConstraintTest {

    /** 规范 4：Controller 类必须以 Controller 结尾 */
    @ArchTest
    static final ArchRule controllerClassesShouldBeNamedProperly =
            classes().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .should().haveNameMatching(".*Controller");

    /** 规范 3.1：Controller 公开方法必须返回统一响应 ApiResponse；文件下载等直接写响应流的 void 方法豁免 */
    @ArchTest
    static final ArchRule controllerMethodsShouldReturnUnifiedResponse =
            methods().that().arePublic().and().areDeclaredInClassesThat().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .and().doNotHaveRawReturnType(void.class)
                    .should().haveRawReturnType(com.hxj.common.ApiResponse.class)
                    .orShould().haveRawReturnType(org.springframework.http.ResponseEntity.class);

    /** 规范 5.2：Service 层禁止直接依赖 Servlet API（HTTP 概念不得渗入业务层） */
    @ArchTest
    static final ArchRule servicesShouldNotDependOnServletApi =
            noClasses().that().resideInAPackage("..hxj.service..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..");

    /** 规范 5.2：业务异常必须经由 GlobalExceptionHandler 统一转换，Controller 不得 try-catch 吞异常后自行返回错误响应 */
    @ArchTest
    static final ArchRule controllersShouldNotSwallowExceptions =
            noClasses().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                     .should().callMethod(com.hxj.common.ApiResponse.class, "error",
                             com.hxj.common.ErrorCode.class, String.class);

    /**
     * 规范 11.1：JPA 实体必须是 class。
     *
     * <p>record 隐式 final，Hibernate 无法为其生成懒加载代理子类，会导致 LazyInitializationException。
     */
    @ArchTest
    static final ArchRule entitiesShouldNotBeRecords =
            classes().that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().notBeRecords();

    /**
     * 规范 11.1：Spring 受管 Bean 必须是 class。
     *
     * <p>record 为 final，CGLIB 无法代理，@Transactional / @Aspect / @Idempotent 会静默失效。
     * 使用元注解匹配，覆盖 @Component 及 @Service、@Controller、@Configuration、@Aspect 等派生注解。
     */
    @ArchTest
    static final ArchRule springManagedBeansShouldNotBeRecords =
            classes().that().areMetaAnnotatedWith(org.springframework.stereotype.Component.class)
                    .should().notBeRecords();

    /**
     * 规范 11.1：DTO 必须是 record，无例外。
     *
     * <p>分页查询请求曾因复用 {@code page}/{@code size} 而退化为可变 class，
     * 现统一实现 {@link PageableRequest} 接口复用逻辑，因此不存在任何例外。
     */
    @ArchTest
    static final ArchRule dataTransferObjectsShouldBeRecords =
            classes().that()
                    .haveNameMatching(".*(Request|Response|Item|Summary|Detail|Query|Criteria|Result|Node|Stat|Stats)$")
                    .and().areNotInterfaces()
                    .should().beRecords();
}
