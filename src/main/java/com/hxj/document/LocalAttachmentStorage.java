package com.hxj.document;

import com.hxj.common.ErrorCode;
import com.hxj.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class LocalAttachmentStorage {

    private final Path root;

    public LocalAttachmentStorage(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_FILE, "上传文件不能为空");
        }
        try {
            Files.createDirectories(root);
            String original = Path.of(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename())
                    .getFileName().toString();
            String storedName = UUID.randomUUID() + "-" + original;
            Path target = root.resolve(storedName).normalize();
            if (!target.startsWith(root)) {
                throw new BusinessException(ErrorCode.INVALID_FILE_NAME, "文件名不合法");
            }
            file.transferTo(target);
            return new StoredFile(original, target.toString(), file.getContentType(), file.getSize());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_STORE_FAILED, "文件保存失败");
        }
    }

    public Resource load(String filePath) {
        try {
            Path path = Path.of(filePath).toAbsolutePath().normalize();
            if (!path.startsWith(root)) {
                throw new BusinessException(ErrorCode.INVALID_FILE_PATH, "文件路径不合法");
            }
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
            }
            return resource;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_READ_FAILED, "文件读取失败");
        }
    }

    public record StoredFile(String originalName, String path, String contentType, long size) {}
}
