package com.hxj.repository;

import com.hxj.entity.WorkbenchEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WorkbenchEntryRepository extends JpaRepository<WorkbenchEntry, Long> {

    /** 按 DAILY → BUSINESS → SEAL 分区次序、区内按 sort_order 排序的全量清单。 */
    @Query("select w from WorkbenchEntry w order by case w.zone when 'DAILY' then 0 when 'BUSINESS' then 1 else 2 end, w.sortOrder")
    List<WorkbenchEntry> findAllGroupedByZone();

    /** 引用指定模板的事项数（模板删除保护）。 */
    long countByTemplateId(Long templateId);
}
