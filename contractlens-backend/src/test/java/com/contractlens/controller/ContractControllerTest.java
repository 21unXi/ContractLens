package com.contractlens.controller;

import com.contractlens.entity.Contract;
import com.contractlens.service.ContractService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContractControllerTest {

    @Test
    void getContractByIdReturnsOkWhenOwned() {
        ContractService contractService = mock(ContractService.class);
        ContractController controller = new ContractController();
        ReflectionTestUtils.setField(controller, "contractService", contractService);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("alice");

        Contract contract = new Contract();
        contract.setId(1L);
        contract.setTitle("t");
        contract.setContent("c");
        contract.setFileType("text/plain");

        when(contractService.getContractById("alice", 1L)).thenReturn(Optional.of(contract));

        ResponseEntity<Contract> response = controller.getContractById(1L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(contract);
    }

    @Test
    void getContractByIdReturnsNotFoundWhenNotOwnedOrMissing() {
        ContractService contractService = mock(ContractService.class);
        ContractController controller = new ContractController();
        ReflectionTestUtils.setField(controller, "contractService", contractService);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("alice");

        when(contractService.getContractById("alice", 2L)).thenReturn(Optional.empty());

        ResponseEntity<Contract> response = controller.getContractById(2L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }
}

