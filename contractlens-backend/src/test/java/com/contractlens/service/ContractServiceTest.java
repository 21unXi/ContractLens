package com.contractlens.service;

import com.contractlens.entity.Contract;
import com.contractlens.repository.AnalysisResultRepository;
import com.contractlens.repository.ContractRepository;
import com.contractlens.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private AnalysisResultRepository analysisResultRepository;

    @InjectMocks
    private ContractService contractService;

    @Test
    void getContractByIdUsesUsernameScope() {
        String username = "alice";
        Long contractId = 1L;

        Contract contract = new Contract();
        contract.setId(contractId);
        contract.setTitle("t");
        contract.setContent("c");
        contract.setFileType("text/plain");

        when(contractRepository.findByIdAndUserUsername(contractId, username)).thenReturn(Optional.of(contract));

        Optional<Contract> result = contractService.getContractById(username, contractId);

        assertThat(result).contains(contract);
        verify(contractRepository).findByIdAndUserUsername(contractId, username);
    }

    @Test
    void getContractByIdReturnsEmptyWhenNotOwnedOrMissing() {
        String username = "alice";
        Long contractId = 2L;

        when(contractRepository.findByIdAndUserUsername(contractId, username)).thenReturn(Optional.empty());

        Optional<Contract> result = contractService.getContractById(username, contractId);

        assertThat(result).isEmpty();
        verify(contractRepository).findByIdAndUserUsername(contractId, username);
    }
}

