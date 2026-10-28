package com.reader.parser;

import com.reader.auth.OwnershipGuard;
import com.reader.common.ApiResponse;
import com.reader.common.BusinessException;
import com.reader.file.FileAsset;
import com.reader.file.FileAssetMapper;
import com.reader.file.StorageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 解析接口控制器：按 fileId 解析已上传文件，返回统一书对象（元数据 + 目录 + 章节）。
 */
@RestController
@RequestMapping("/api/parser")
public class ParserController {

    private final ParserService parserService;
    private final FileAssetMapper fileAssetMapper;
    private final StorageService storageService;

    public ParserController(ParserService parserService,
                            FileAssetMapper fileAssetMapper,
                            StorageService storageService) {
        this.parserService = parserService;
        this.fileAssetMapper = fileAssetMapper;
        this.storageService = storageService;
    }

    /**
     * 解析已上传文件，返回完整书对象（元数据 + 目录 + 章节正文）。
     */
    @GetMapping("/{fileId}")
    public ApiResponse<Book> parse(@PathVariable("fileId") Long fileId) {
        FileAsset asset = fileAssetMapper.findById(fileId);
        if (asset == null) {
            throw new BusinessException(404, "文件不存在");
        }

        byte[] content = storageService.download(asset.getStorageKey());
        Book book = parserService.parse(content, asset.getFileType(), asset.getMetaTitle());
        return ApiResponse.ok(book);
    }

    /**
     * 仅抽取元数据（书名/作者），供秒传预检复用。
     */
    @GetMapping("/{fileId}/meta")
    public ApiResponse<Book.BookMeta> meta(@PathVariable("fileId") Long fileId) {
        FileAsset asset = fileAssetMapper.findById(fileId);
        if (asset == null) {
            throw new BusinessException(404, "文件不存在");
        }

        byte[] content = storageService.download(asset.getStorageKey());
        Book.BookMeta meta = parserService.extractMeta(content, asset.getFileType(), asset.getMetaTitle());
        return ApiResponse.ok(meta);
    }
}
