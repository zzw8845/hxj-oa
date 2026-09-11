package com.hxj.repository;

import com.hxj.entity.FormTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormTemplateRepository extends JpaRepository<FormTemplate, Long> {

    Optional<FormTemplate> findByBusinessType(String businessType);

    List<FormTemplate> findByStatusOrderBySortOrderAsc(String status);
}
