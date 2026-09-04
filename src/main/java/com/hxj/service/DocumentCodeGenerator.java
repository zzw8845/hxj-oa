package com.hxj.service;

import com.hxj.entity.BusinessType;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** 生成“类型前缀 + yyyyMMdd + 四位当日流水号”的不可变单据编号。 */
@Service
public class DocumentCodeGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final long MAX_DAILY_SEQUENCE = 9_999L;

    private final DocumentSequenceAllocator sequenceAllocator;
    private final Clock clock;

    public DocumentCodeGenerator(DocumentSequenceAllocator sequenceAllocator, Clock clock) {
        this.sequenceAllocator = sequenceAllocator;
        this.clock = clock;
    }

    public String generate(BusinessType businessType) {
        Objects.requireNonNull(businessType, "业务类型不能为空");
        String date = LocalDate.now(clock).format(DATE_FORMATTER);
        long sequence = sequenceAllocator.nextValue(date);
        if (sequence > MAX_DAILY_SEQUENCE) {
            throw new IllegalStateException("当日单据流水号已超过四位上限: " + date);
        }
        return businessType.getCodePrefix() + date + "%04d".formatted(sequence);
    }
}