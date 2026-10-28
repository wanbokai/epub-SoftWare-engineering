package com.reader.bookshelf;

import com.reader.auth.OwnershipGuard;
import com.reader.common.BusinessException;
import com.reader.file.FileAsset;
import com.reader.file.FileAssetMapper;
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
    private final OwnershipGuard ownershipGuard;

    public BookshelfService(BookshelfItemMapper bookshelfItemMapper,
                            FileAssetMapper fileAssetMapper,
                            OwnershipGuard ownershipGuard) {
        this.bookshelfItemMapper = bookshelfItemMapper;
        this.fileAssetMapper = fileAssetMapper;
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
     * 列出当前用户书架，支持关键词检索，按最近阅读时间倒序。
     */
    public List<BookshelfItem> list(Long userId, String keyword) {
        return bookshelfItemMapper.listByUser(userId, keyword);
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
     * 移除书架条目：引用计数 -1，归零时回收物理文件（对象存储 + 数据库记录）。
     */
    @Transactional
    public void remove(Long userId, Long itemId) {
        BookshelfItem item = getOwnedItem(userId, itemId);
        bookshelfItemMapper.deleteById(itemId);

        // 引用计数 -1，若归零且无其他条目引用则回收物理文件
        fileAssetMapper.decrementRefCount(item.getFileId());
        FileAsset fileAsset = fileAssetMapper.findById(item.getFileId());
        if (fileAsset != null && fileAsset.getRefCount() != null && fileAsset.getRefCount() <= 0) {
            fileAssetMapper.deleteById(item.getFileId());
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
