package com.contractlens.controller;

import com.contractlens.entity.Contract;
import com.contractlens.service.ContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    @Autowired
    private ContractService contractService;

    @PostMapping("/upload")
    public ResponseEntity<Contract> uploadContract(@RequestParam("file") MultipartFile file, Authentication authentication) throws IOException {
        String username = authentication.getName();
        Contract contract = contractService.uploadContract(username, file);
        return ResponseEntity.ok(contract);
    }

    @GetMapping
    public ResponseEntity<List<Contract>> getUserContracts(Authentication authentication) {
        String username = authentication.getName();
        List<Contract> contracts = contractService.getContractsByUsername(username);
        return ResponseEntity.ok(contracts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Contract> getContractById(@PathVariable Long id, Authentication authentication) {
        String username = authentication.getName();
        Optional<Contract> contract = contractService.getContractById(username, id);
        return contract.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContract(@PathVariable Long id, Authentication authentication) {
        String username = authentication.getName();
        contractService.deleteContract(username, id);
        return ResponseEntity.noContent().build();
    }
}
