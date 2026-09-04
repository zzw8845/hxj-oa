package com.hxj.service;

/** 为指定业务日期分配单调递增序号。 */
public interface DocumentSequenceAllocator {

    long nextValue(String sequenceDate);
}