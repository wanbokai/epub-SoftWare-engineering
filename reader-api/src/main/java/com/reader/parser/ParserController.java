package com.reader.parser;

import com.reader.auth.OwnershipGuard;
import com.reader.bookshelf.BookshelfItemMapper;
import com.reader.common.ApiResponse;
import com.reader.common.BusinessException;
import com.reader.file.FileAsset;
import com.reader.file.FileAssetMapper;
import com.reader.file.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 解析接口控制器：按 fileId 解析已上传文件，返回统一书对象（元数据 + 目录 + 章节）。
 * <p>
 * 物理文件按 SHA-256 全局去重，可能被多个用户引用，因此这里以“文件是否在当前用户书架中”
 * 作为访问边界，避免通过遍历 fileId 读取他人书籍。
 */
@RestController
@RequestMapping("/api/parser")
public class ParserController {

    private static final Logger log = LoggerFactory.getLogger(ParserController.class);
    private static final String UNNAMED_TITLE = "未命名书籍";

    private final ParserService parserService;
    private final FileAssetMapper fileAssetMapper;
    private final StorageService storageService;
    private final BookshelfItemMapper bookshelfItemMapper;
    private final OwnershipGuard ownershipGuard;
    private final BookCache bookCache;

    public ParserController(ParserService parserService,
                            FileAssetMapper fileAssetMapper,
                            StorageService storageService,
                            BookshelfItemMapper bookshelfItemMapper,
                            OwnershipGuard ownershipGuard,
                            BookCache bookCache) {
        this.parserService = parserService;
        this.fileAssetMapper = fileAssetMapper;
        this.storageService = storageService;
        this.bookshelfItemMapper = bookshelfItemMapper;
        this.ownershipGuard = ownershipGuard;
        this.bookCache = bookCache;
    }

    /**
     * 解析已上传文件，返回书对象（元数据 + 目录 + 章节标题，不含章节正文）。
     * <p>
     * 正文通过 {@code /chapter/{index}} 按需加载，避免一本 2000+ 章的书一次返回几十 MB JSON。
     */
    @GetMapping("/{fileId}")
    public ApiResponse<Book> parse(@PathVariable("fileId") Long fileId) {
        FileAsset asset = requireReadableAsset(fileId);
        Book book = loadBook(asset);
        return ApiResponse.ok(slimCopy(book));
    }

    /**
     * 按章节下标返回正文，供阅读器按需加载。
     */
    @GetMapping("/{fileId}/chapter/{index}")
    public ApiResponse<Book.Chapter> chapter(@PathVariable("fileId") Long fileId,
                                             @PathVariable("index") Integer index) {
        FileAsset asset = requireReadableAsset(fileId);
        Book book = loadBook(asset);
        if (index == null || index < 0 || index >= book.getChapters().size()) {
            throw new BusinessException(404, "章节不存在");
        }
        return ApiResponse.ok(book.getChapters().get(index));
    }

    /**
     * 取整本书：优先用进程内缓存，未命中则从对象存储下载并解析。
     */
    private Book loadBook(FileAsset asset) {
        Book cached = bookCache.get(asset.getId());
        if (cached != null) {
            return cached;
        }

        long started = System.currentTimeMillis();
        byte[] content = storageService.download(asset.getStorageKey());
        Book book = parserService.parse(content, asset.getFileType(), asset.getMetaTitle());

        // 历史数据的元数据为空，首次解析成功后回填，供书架展示与作者检索
        backfillMeta(asset, book.getMeta());
        bookCache.put(asset.getId(), book);
        log.info("解析完成 fileId={} size={}KB chapters={} 耗时={}ms",
                asset.getId(), content.length / 1024, book.getChapters().size(),
                System.currentTimeMillis() - started);
        return book;
    }

    /**
     * 只保留目录与章节标题，正文置空，显著缩小首次加载的响应体。
     */
    private Book slimCopy(Book source) {
        Book copy = new Book();
        copy.setMeta(source.getMeta());
        copy.setSpine(source.getSpine());
        copy.setToc(source.getToc());
        java.util.List<Book.Chapter> chapters = new java.util.ArrayList<>();
        for (Book.Chapter chapter : source.getChapters()) {
            Book.Chapter slim = new Book.Chapter();
            slim.setId(chapter.getId());
            slim.setTitle(chapter.getTitle());
            slim.setContent("");
            chapters.add(slim);
        }
        copy.setChapters(chapters);
        return copy;
    }

    /**
     * 仅抽取元数据（书名/作者），供秒传预检复用。
     */
    @GetMapping("/{fileId}/meta")
    public ApiResponse<Book.BookMeta> meta(@PathVariable("fileId") Long fileId) {
        FileAsset asset = requireReadableAsset(fileId);

        byte[] content = storageService.download(asset.getStorageKey());
        Book.BookMeta meta = parserService.extractMeta(content, asset.getFileType(), asset.getMetaTitle());
        backfillMeta(asset, meta);
        return ApiResponse.ok(meta);
    }

    /**
     * 文件必须存在且在当前用户书架中，否则 404 / 403。
     * <p>
     * 先校验归属再判断存在性，避免用 404/403 的差异探测他人 fileId 是否存在。
     */
    private FileAsset requireReadableAsset(Long fileId) {
        Long userId = ownershipGuard.requireCurrentUserId();
        if (bookshelfItemMapper.findByUserAndFile(userId, fileId) == null) {
            throw new BusinessException(403, "该文件不在当前用户书架中");
        }
        FileAsset asset = fileAssetMapper.findById(fileId);
        if (asset == null) {
            throw new BusinessException(404, "文件不存在");
        }
        return asset;
    }

    /**
     * 把解析出的书名/作者写回 file_asset（仅补空字段，不覆盖已有值）。
     */
    private void backfillMeta(FileAsset asset, Book.BookMeta meta) {
        if (meta == null) {
            return;
        }
        String title = meta.getTitle();
        String author = meta.getAuthor();

        boolean titleMissing = asset.getMetaTitle() == null || asset.getMetaTitle().isBlank();
        boolean authorMissing = asset.getMetaAuthor() == null || asset.getMetaAuthor().isBlank();
        boolean needTitle = titleMissing && title != null && !title.isBlank()
                && !UNNAMED_TITLE.equals(title);
        boolean needAuthor = authorMissing && author != null && !author.isBlank();
        if (!needTitle && !needAuthor) {
            return;
        }

        try {
            fileAssetMapper.updateMeta(asset.getId(),
                    needTitle ? title : asset.getMetaTitle(),
                    needAuthor ? author : asset.getMetaAuthor());
            if (needTitle) {
                asset.setMetaTitle(title);
            }
            if (needAuthor) {
                asset.setMetaAuthor(author);
            }
        } catch (RuntimeException ignored) {
            // 回填失败不影响本次解析
        }
    }
}
