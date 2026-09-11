package com.hxj.repository;

import com.hxj.entity.FormField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormFieldRepository extends JpaRepository<FormField, Long> {

    List<FormField> findByTemplateIdOrderBySortOrderAsc(Long templateId);

    void deleteByTemplateId(Long templateId);
}
