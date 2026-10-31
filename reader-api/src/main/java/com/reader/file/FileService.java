package com.reader.file;

import com.reader.parser.Book;
import com.reader.parser.ParserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 文件服务：SHA-256 秒传预检、物理文件上传与登记。
 * <p>
 * 上传时顺带抽取书名/作者写入 file_asset，供书架展示与检索使用；
 * 写对象存储与写库之间做失败补偿，避免留下孤儿文件。
 */
@Service
public class FileService {

    private final FileAssetMapper fileAssetMapper;
    private final StorageService storageService;
    private final ParserService parserService;

    public FileService(FileAssetMapper fileAssetMapper,
                       StorageService storageService,
                       ParserService parserService) {
        this.fileAssetMapper = fileAssetMapper;
        this.storageService = storageService;
        this.parserService = parserService;
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
     *
     * @param filename 原始文件名，TXT 无内嵌元数据时用文件名兜底书名
     */
    public FileAsset upload(String sha256, byte[] content, int fileType, String contentType, String filename) {
        // 已存在则直接返回（并发下兜底去重）
        FileAsset existing = fileAssetMapper.findBySha256(sha256);
        if (existing != null) {
            backfillMeta(existing, content, fileType, filename);
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
        applyMeta(asset, content, fileType, filename);

        try {
            fileAssetMapper.insert(asset);
        } catch (DuplicateKeyException e) {
            // 并发上传同一文件：对象内容相同，直接复用已登记记录
            FileAsset duplicate = fileAssetMapper.findBySha256(sha256);
            if (duplicate != null) {
                return duplicate;
            }
            throw e;
        } catch (RuntimeException e) {
            // 写库失败：回收刚写入的对象，避免孤儿文件（同内容并发时可能已登记，先确认）
            if (safeFindBySha256(sha256) == null) {
                safeDelete(storageKey);
            }
            throw e;
        }
        return asset;
    }

    /**
     * 为已存在但元数据为空的记录补写书名/作者（历史数据首次上传/打开时触发）。
     */
    private void backfillMeta(FileAsset asset, byte[] content, int fileType, String filename) {
        if (asset.getMetaTitle() != null && !asset.getMetaTitle().isBlank()) {
            return;
        }
        Book.BookMeta meta = extractMetaQuietly(content, fileType, filename);
        if (meta == null || meta.getTitle() == null || meta.getTitle().isBlank()) {
            return;
        }
        String author = meta.getAuthor() != null ? meta.getAuthor() : asset.getMetaAuthor();
        try {
            fileAssetMapper.updateMeta(asset.getId(), meta.getTitle(), author);
            asset.setMetaTitle(meta.getTitle());
            asset.setMetaAuthor(author);
        } catch (RuntimeException ignored) {
            // 补写失败不影响上传结果
        }
    }

    private void applyMeta(FileAsset asset, byte[] content, int fileType, String filename) {
        Book.BookMeta meta = extractMetaQuietly(content, fileType, filename);
        if (meta != null) {
            asset.setMetaTitle(meta.getTitle());
            asset.setMetaAuthor(meta.getAuthor());
        }
    }

    /**
     * 元数据抽取失败不应阻断上传，解析接口仍可按需解析。
     */
    private Book.BookMeta extractMetaQuietly(byte[] content, int fileType, String filename) {
        try {
            return parserService.extractMeta(content, fileType, baseName(filename));
        } catch (Exception e) {
            return null;
        }
    }

    private String baseName(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.replaceAll("(?i)\\.(epub|txt)$", "");
    }

    private FileAsset safeFindBySha256(String sha256) {
        try {
            return fileAssetMapper.findBySha256(sha256);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void safeDelete(String storageKey) {
        try {
            storageService.delete(storageKey);
        } catch (RuntimeException ignored) {
            // 补偿删除失败只能留待运维清理，不能覆盖原始异常
        }
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
