package com.hxj.repository;

import com.hxj.entity.OaAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OaAttachmentRepository extends JpaRepository<OaAttachment, Long> {

    List<OaAttachment> findByDocumentIdOrderByCreatedAtAsc(Long documentId);
}