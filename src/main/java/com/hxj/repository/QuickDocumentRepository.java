package com.hxj.repository;

import com.hxj.enums.BusinessTypeEnum;
import com.hxj.entity.QuickDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuickDocumentRepository extends JpaRepository<QuickDocument, Long> {
    List<QuickDocument> findByBusinessTypeOrderBySortOrderAsc(BusinessTypeEnum businessType);

    Optional<QuickDocument> findByBusinessTypeAndName(BusinessTypeEnum businessType, String name);
}