package com.hxj.archive;

import java.time.LocalDateTime;

/** 归档台账多条件组合查询条件。 */
public record ArchiveLedgerQuery(
        String applicant,
        String department,
        String docCode,
        LocalDateTime archivedFrom,
        LocalDateTime archivedTo) {
}