package com.reader.file;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 文件服务：SHA-256 秒传预检、物理文件上传与登记。
 */
@Service
public class FileService {

    private final FileAssetMapper fileAssetMapper;
    private final StorageService storageService;

    public FileService(FileAssetMapper fileAssetMapper, StorageService storageService) {
        this.fileAssetMapper = fileAssetMapper;
        this.storageService = storageService;
    }

    /**
     * 秒传预检：根据 SHA-256 判断物理文件是否已存在。
     */
    public UploadCheckResponse check(String sha256) {
        FileAsset existing = fileAssetMapper.findBySha256(sha256);
        if (existing != null) {
            UploadCheckResponse resp = new UploadCheckResponse(true, sha256);
            resp.setFileId(existing.getId());
            resp.setMetaTitle(existing.getMetaTitle());
            resp.setMetaAuthor(existing.getMetaAuthor());
            return resp;
        }
        return new UploadCheckResponse(false, sha256);
    }

    /**
     * 上传并登记物理文件：内容按 SHA-256 内容寻址存储，随机化 storage_key。
     */
    public FileAsset upload(String sha256, byte[] content, int fileType, String contentType) {
        // 已存在则直接返回（并发下兜底去重）
        FileAsset existing = fileAssetMapper.findBySha256(sha256);
        if (existing != null) {
            return existing;
        }

        // 随机化存储键：{fileType}/{前2位}/{sha256}
        String storageKey = fileType + "/" + sha256.substring(0, 2) + "/" + sha256;
        storageService.upload(storageKey, content, contentType);

        FileAsset asset = new FileAsset();
        asset.setSha256(sha256);
        asset.setStorageKey(storageKey);
        asset.setFileType(fileType);
        asset.setFileSize(content.length);
        asset.setRefCount(0);
        fileAssetMapper.insert(asset);
        return asset;
    }

    /**
     * 计算文件 SHA-256 指纹。
     */
    public static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }
}
