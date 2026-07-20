package com.woowasoripae.attendance.global.file;

import com.woowasoripae.attendance.global.exception.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * MVP shortcut: stores photos on local disk under app.file.upload-dir and serves them back via
 * a static resource handler at app.file.base-url. Swap for S3/object storage once this needs to
 * survive redeploys or run on more than one instance.
 */
@Service
public class FileStorageService {

    private final FileStorageProperties properties;

    public FileStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("첨부된 사진 파일이 없습니다.");
        }

        String extension = extractExtension(file.getOriginalFilename());
        String storedFileName = UUID.randomUUID() + extension;
        Path targetDir = Path.of(properties.uploadDir(), LocalDate.now().toString());
        Path targetPath = targetDir.resolve(storedFileName);

        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new IllegalStateException("사진 저장에 실패했습니다.", e);
        }

        return properties.baseUrl() + "/" + LocalDate.now() + "/" + storedFileName;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }
}
