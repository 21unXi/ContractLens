package com.contractlens.repository;

import com.contractlens.entity.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Optional<AnalysisResult> findByContractId(Long contractId);

    void deleteByContractId(Long contractId);
}
