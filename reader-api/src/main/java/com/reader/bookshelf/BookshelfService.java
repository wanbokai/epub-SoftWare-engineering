package com.reader.bookshelf;

import com.reader.auth.OwnershipGuard;
import com.reader.common.BusinessException;
import com.reader.file.FileAsset;
import com.reader.file.FileAssetMapper;
import com.reader.file.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 书架服务：入架、列表检索、重命名、移除（含引用计数回收）。
 */
@Service
public class BookshelfService {

    private final BookshelfItemMapper bookshelfItemMapper;
    private final FileAssetMapper fileAssetMapper;
    private final StorageService storageService;
    private final OwnershipGuard ownershipGuard;

    public BookshelfService(BookshelfItemMapper bookshelfItemMapper,
                            FileAssetMapper fileAssetMapper,
                            StorageService storageService,
                            OwnershipGuard ownershipGuard) {
        this.bookshelfItemMapper = bookshelfItemMapper;
        this.fileAssetMapper = fileAssetMapper;
        this.storageService = storageService;
        this.ownershipGuard = ownershipGuard;
    }

    /**
     * 添加书籍到书架：登记引用，文件引用计数 +1。
     */
    @Transactional
    public BookshelfItem add(Long userId, AddBookRequest request) {
        if (request.getFileId() == null) {
            throw new BusinessException(400, "缺少文件 ID");
        }
        FileAsset fileAsset = fileAssetMapper.findById(request.getFileId());
        if (fileAsset == null) {
            throw new BusinessException(404, "物理文件不存在");
        }

        // 同一用户对同一文件的重复入架拦截
        BookshelfItem existing = bookshelfItemMapper.findByUserAndFile(userId, request.getFileId());
        if (existing != null) {
            throw new BusinessException(400, "该书籍已在书架中");
        }

        String title = (request.getDisplayTitle() == null || request.getDisplayTitle().isBlank())
                ? (fileAsset.getMetaTitle() != null && !fileAsset.getMetaTitle().isBlank()
                    ? fileAsset.getMetaTitle()
                    : "未命名书籍")
                : request.getDisplayTitle().trim();

        BookshelfItem item = new BookshelfItem();
        item.setUserId(userId);
        item.setFileId(request.getFileId());
        item.setDisplayTitle(title);
        bookshelfItemMapper.insert(item);

        // 引用计数 +1（物理文件被引用）
        fileAssetMapper.incrementRefCount(request.getFileId());

        return bookshelfItemMapper.findById(item.getId());
    }

    /**
     * 列出当前用户书架，支持关键词检索（书名/元数据书名/作者），按指定方式排序。
     *
     * @param sort lastRead（最近阅读，默认）/ created（导入时间）/ title（书名）
     */
    public List<BookshelfItem> list(Long userId, String keyword, String sort) {
        String normalized = ("created".equals(sort) || "title".equals(sort)) ? sort : "lastRead";
        return bookshelfItemMapper.listByUser(userId, keyword, normalized);
    }

    /**
     * 重命名书架条目（须校验归属）。
     */
    @Transactional
    public BookshelfItem rename(Long userId, Long itemId, UpdateBookRequest request) {
        BookshelfItem item = getOwnedItem(userId, itemId);
        if (request.getDisplayTitle() == null || request.getDisplayTitle().isBlank()) {
            throw new BusinessException(400, "书名不能为空");
        }
        item.setDisplayTitle(request.getDisplayTitle().trim());
        bookshelfItemMapper.update(item);
        return bookshelfItemMapper.findById(itemId);
    }

    /**
     * 移除书架条目：引用计数 -1。
     * <p>
     * 仅当 {@code removeCloudCopy=true} 且引用计数归零时，才回收对象存储中的物理文件与 file_asset 记录；
     * 否则只把书从个人书架移出，物理文件继续保留（可被其他用户秒传复用）。
     *
     * @param removeCloudCopy 是否同时删除云端文件副本
     */
    @Transactional
    public void remove(Long userId, Long itemId, boolean removeCloudCopy) {
        BookshelfItem item = getOwnedItem(userId, itemId);
        bookshelfItemMapper.deleteById(itemId);

        fileAssetMapper.decrementRefCount(item.getFileId());
        if (!removeCloudCopy) {
            return;
        }

        // 引用计数 -1 后归零且确实没有条目引用时，回收物理文件。
        // 先删数据库行再删对象：对象删除失败会回滚事务，避免“库里有记录、对象已丢失”。
        FileAsset fileAsset = fileAssetMapper.findById(item.getFileId());
        if (fileAsset != null && fileAsset.getRefCount() != null && fileAsset.getRefCount() <= 0) {
            fileAssetMapper.deleteById(item.getFileId());
            storageService.delete(fileAsset.getStorageKey());
        }
    }

    /**
     * 获取属于当前用户的书架条目，不存在或越权则抛异常。
     */
    private BookshelfItem getOwnedItem(Long userId, Long itemId) {
        BookshelfItem item = bookshelfItemMapper.findById(itemId);
        if (item == null) {
            throw new BusinessException(404, "书架条目不存在");
        }
        ownershipGuard.assertOwnership(item.getUserId());
        return item;
    }
}
