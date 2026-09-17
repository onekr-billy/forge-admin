package com.mdframe.forge.plugin.system.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.mdframe.forge.starter.config.config.LoginConfig;
import com.mdframe.forge.starter.config.service.ConfigManagerService;
import com.mdframe.forge.starter.core.annotation.api.ApiPermissionIgnore;
import com.mdframe.forge.starter.core.annotation.tenant.IgnoreTenant;
import com.mdframe.forge.starter.file.core.FileManager;
import com.mdframe.forge.starter.file.model.FileMetadata;
import com.mdframe.forge.starter.file.storage.FileStorage;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 登录页群二维码公开访问接口。
 *
 * <p>登录页处于未登录状态，通用文件接口会做登录校验和私有文件权限校验，
 * 因此这里参照 {@link LoginTenantAssetController} 只允许访问登录配置中
 * groupQrcodeImage 引用的图片，避免放开通用文件下载接口。</p>
 */
@RestController
@RequestMapping("/auth/loginQrcode")
@RequiredArgsConstructor
@IgnoreTenant
@SaIgnore
@ApiPermissionIgnore
public class LoginGroupQrcodeController {

    private static final Pattern SAFE_FILE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,128}$");
    private static final Set<String> SAFE_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final ConfigManagerService configManagerService;
    private final FileManager fileManager;

    /**
     * 获取登录页群二维码图片。
     */
    @GetMapping
    public void getGroupQrcode(HttpServletResponse response) {
        LoginConfig config = configManagerService.getLoginConfig();
        if (config == null || !Boolean.TRUE.equals(config.getGroupQrcodeEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "群二维码未启用");
        }
        String fileId = extractManagedFileId(config.getGroupQrcodeImage());
        if (fileId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "群二维码未配置");
        }

        FileMetadata metadata = fileManager.getMetadata(fileId);
        if (metadata == null || !isSafeImage(metadata)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "群二维码不存在");
        }
        FileStorage storage = fileManager.getStorage(metadata.getStorageType());
        if (storage == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "群二维码不存在");
        }

        try (InputStream inputStream = storage.download(fileId)) {
            response.setContentType(resolveContentType(metadata));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=300");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename="
                    + URLEncoder.encode(resolveFileName(metadata, fileId), StandardCharsets.UTF_8));
            inputStream.transferTo(response.getOutputStream());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "群二维码不存在", ex);
        }
    }

    private String extractManagedFileId(String assetReference) {
        if (assetReference == null || assetReference.isBlank()) {
            return null;
        }
        String value = assetReference.trim();
        String lowerValue = value.toLowerCase(Locale.ROOT);
        if (lowerValue.startsWith("http://")
                || lowerValue.startsWith("https://")
                || lowerValue.startsWith("data:")
                || lowerValue.startsWith("blob:")) {
            return null;
        }

        String fileId = extractFileIdAfterMarker(value, "/api/file/download/");
        if (fileId == null) {
            fileId = extractFileIdAfterMarker(value, "/api/file/url/");
        }
        if (fileId == null && !value.startsWith("/") && !value.contains("/") && !value.contains("\\")) {
            fileId = value;
        }
        if (fileId == null) {
            return null;
        }
        int queryIndex = fileId.indexOf('?');
        if (queryIndex >= 0) {
            fileId = fileId.substring(0, queryIndex);
        }
        int hashIndex = fileId.indexOf('#');
        if (hashIndex >= 0) {
            fileId = fileId.substring(0, hashIndex);
        }
        return SAFE_FILE_ID_PATTERN.matcher(fileId).matches() ? fileId : null;
    }

    private String extractFileIdAfterMarker(String value, String marker) {
        int index = value.indexOf(marker);
        if (index < 0) {
            return null;
        }
        String rest = value.substring(index + marker.length());
        int slashIndex = rest.indexOf('/');
        return slashIndex >= 0 ? rest.substring(0, slashIndex) : rest;
    }

    private boolean isSafeImage(FileMetadata metadata) {
        String extension = metadata.getExtension();
        if (extension != null && "svg".equalsIgnoreCase(extension)) {
            return false;
        }
        String mimeType = metadata.getMimeType();
        if (mimeType != null) {
            String normalizedMimeType = mimeType.toLowerCase(Locale.ROOT);
            if (normalizedMimeType.startsWith("image/svg+xml")) {
                return false;
            }
            if (normalizedMimeType.startsWith("image/")) {
                return true;
            }
        }
        return extension != null && SAFE_IMAGE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    private String resolveFileName(FileMetadata metadata, String fileId) {
        if (metadata.getOriginalName() != null && !metadata.getOriginalName().isBlank()) {
            return metadata.getOriginalName();
        }
        if (metadata.getStorageName() != null && !metadata.getStorageName().isBlank()) {
            return metadata.getStorageName();
        }
        return fileId;
    }

    private String resolveContentType(FileMetadata metadata) {
        String mimeType = metadata.getMimeType();
        if (mimeType != null && !mimeType.isBlank() && !"application/octet-stream".equalsIgnoreCase(mimeType)) {
            return mimeType;
        }
        String extension = metadata.getExtension();
        if (extension == null) {
            return "image/png";
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/png";
        };
    }
}
