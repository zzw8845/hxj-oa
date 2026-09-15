package com.hxj.repository;

import com.hxj.entity.FormTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormTemplateRepository extends JpaRepository<FormTemplate, Long> {

    Optional<FormTemplate> findByBusinessType(String businessType);

    Optional<FormTemplate> findByName(String name);

    List<FormTemplate> findByStatusOrderBySortOrderAsc(String status);

    /** 绑定该流程配置的全部模板（条件变量目录按此求并集）。 */
    List<FormTemplate> findByFlowConfigId(Long flowConfigId);
}
