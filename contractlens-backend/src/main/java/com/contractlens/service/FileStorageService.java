package com.contractlens.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.util.StringUtils;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService(@Value("${upload.path}") String uploadPath) {
        this.fileStorageLocation = Paths.get(uploadPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public String storeFile(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String storedFileName = UUID.randomUUID().toString() + fileExtension;

        try {
            Path targetLocation = this.fileStorageLocation.resolve(storedFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return storedFileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + originalFileName + ". Please try again!", ex);
        }
    }

    public boolean deleteStoredFile(String storedFileName) {
        if (!StringUtils.hasText(storedFileName)) {
            return false;
        }

        try {
            Path targetLocation = this.fileStorageLocation.resolve(storedFileName).normalize();
            if (!targetLocation.startsWith(this.fileStorageLocation)) {
                return false;
            }
            return Files.deleteIfExists(targetLocation);
        } catch (IOException ex) {
            return false;
        }
    }

    public String parseFileContent(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IOException("File name is null");
        }

        if (filename.endsWith(".pdf")) {
            return parsePdf(file.getInputStream());
        } else if (filename.endsWith(".docx")) {
            return parseDocx(file.getInputStream());
        } else if (filename.endsWith(".txt")) {
            return new String(file.getBytes());
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + filename);
        }
    }

    private String parsePdf(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String parseDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            return extractor.getText();
        }
    }
}
