package com.hxj.repository;

import com.hxj.entity.BusinessType;
import com.hxj.entity.QuickDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuickDocumentRepository extends JpaRepository<QuickDocument, Long> {
    List<QuickDocument> findByBusinessTypeOrderBySortOrderAsc(BusinessType businessType);
}