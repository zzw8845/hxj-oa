package com.hxj.repository;

import com.hxj.service.DocumentSequenceAllocator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 基于 doc_seq 行锁实现的数据库序号分配器。 */
@Repository
public class JdbcDocumentSequenceAllocator implements DocumentSequenceAllocator {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentSequenceAllocator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long nextValue(String sequenceDate) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO doc_seq (seq_date, seq_value) VALUES (?, 0)",
                sequenceDate);
        jdbcTemplate.update(
                "UPDATE doc_seq SET seq_value = seq_value + 1 WHERE seq_date = ?",
                sequenceDate);
        Long value = jdbcTemplate.queryForObject(
                "SELECT seq_value FROM doc_seq WHERE seq_date = ?",
                Long.class,
                sequenceDate);
        if (value == null) {
            throw new IllegalStateException("单据序号分配失败: " + sequenceDate);
        }
        return value;
    }
}