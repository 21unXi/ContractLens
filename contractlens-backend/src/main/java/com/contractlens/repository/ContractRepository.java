package com.contractlens.repository;

import com.contractlens.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByUserId(Long userId);

    Optional<Contract> findByIdAndUserUsername(Long id, String username);
}
