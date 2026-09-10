package com.hxj.repository;

import com.hxj.service.DocumentSequenceAllocator;
import org.springframework.dao.DataIntegrityViolationException;
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
        // 先尝试插入当日序号行；若已存在（并发或重复调用）则忽略，走下方 UPDATE 分支。
        // 用标准 INSERT + 捕获唯一约束冲突，而非 MySQL 专属的 INSERT IGNORE，
        // 以保证 H2（测试）与 MySQL（生产）行为一致。seq_date 为主键，具备唯一约束。
        try {
            jdbcTemplate.update(
                    "INSERT INTO doc_seq (seq_date, seq_value) VALUES (?, 0)",
                    sequenceDate);
        } catch (DataIntegrityViolationException ignored) {
            // 该行已存在，无需处理
        }
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