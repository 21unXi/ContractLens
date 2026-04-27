package com.contractlens.repository;

import com.contractlens.entity.AnalysisChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisChatMessageRepository extends JpaRepository<AnalysisChatMessage, Long> {

    List<AnalysisChatMessage> findTop20ByContractIdOrderByCreatedAtAsc(Long contractId);

    List<AnalysisChatMessage> findByContractIdOrderByCreatedAtAsc(Long contractId, Pageable pageable);

    long countByContractId(Long contractId);

    void deleteByContractId(Long contractId);
}

