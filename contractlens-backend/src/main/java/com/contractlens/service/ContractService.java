package com.contractlens.service;

import com.contractlens.entity.Contract;
import com.contractlens.entity.User;
import com.contractlens.repository.AnalysisResultRepository;
import com.contractlens.repository.ContractRepository;
import com.contractlens.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class ContractService {

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    public Contract uploadContract(String username, MultipartFile file) throws IOException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String filePath = fileStorageService.storeFile(file);
        String content = fileStorageService.parseFileContent(file);

        Contract contract = new Contract();
        contract.setUser(user);
        contract.setTitle(file.getOriginalFilename());
        contract.setContent(content);
        contract.setFileType(file.getContentType());
        contract.setFilePath(filePath);
        contract.setFileSize(file.getSize());

        return contractRepository.save(contract);
    }

    public List<Contract> getContractsByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return contractRepository.findByUserId(user.getId());
    }

    public Optional<Contract> getContractById(String username, Long id) {
        return contractRepository.findByIdAndUserUsername(id, username);
    }

    @Transactional
    public void deleteContract(String username, Long contractId) {
        Contract contract = contractRepository.findByIdAndUserUsername(contractId, username)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        analysisResultRepository.deleteByContractId(contractId);
        contractRepository.delete(contract);

        fileStorageService.deleteStoredFile(contract.getFilePath());
    }
}
