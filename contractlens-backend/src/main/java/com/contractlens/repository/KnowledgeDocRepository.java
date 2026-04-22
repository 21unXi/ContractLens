package com.contractlens.repository;

import com.contractlens.entity.KnowledgeDoc;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocRepository extends JpaRepository<KnowledgeDoc, Long> {
}
