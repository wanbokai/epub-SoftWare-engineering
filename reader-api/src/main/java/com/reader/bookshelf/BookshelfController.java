package com.reader.bookshelf;

import com.reader.auth.OwnershipGuard;
import com.reader.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 书架接口控制器：入架、列表检索、重命名、移除。
 */
@RestController
@RequestMapping("/api/bookshelf")
public class BookshelfController {

    private final BookshelfService bookshelfService;
    private final OwnershipGuard ownershipGuard;

    public BookshelfController(BookshelfService bookshelfService, OwnershipGuard ownershipGuard) {
        this.bookshelfService = bookshelfService;
        this.ownershipGuard = ownershipGuard;
    }

    /**
     * 添加书籍到书架。
     */
    @PostMapping
    public ApiResponse<BookshelfItem> add(@RequestBody AddBookRequest request) {
        Long userId = ownershipGuard.requireCurrentUserId();
        return ApiResponse.ok(bookshelfService.add(userId, request));
    }

    /**
     * 列出书架（可选 keyword 检索、sort 排序）。
     *
     * @param sort lastRead（最近阅读，默认）/ created（导入时间）/ title（书名）
     */
    @GetMapping
    public ApiResponse<List<BookshelfItem>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false, defaultValue = "lastRead") String sort) {
        Long userId = ownershipGuard.requireCurrentUserId();
        return ApiResponse.ok(bookshelfService.list(userId, keyword, sort));
    }

    /**
     * 重命名书架条目。
     */
    @PutMapping("/{itemId}")
    public ApiResponse<BookshelfItem> rename(@PathVariable("itemId") Long itemId,
                                             @RequestBody UpdateBookRequest request) {
        Long userId = ownershipGuard.requireCurrentUserId();
        return ApiResponse.ok(bookshelfService.rename(userId, itemId, request));
    }

    /**
     * 移除书架条目（可选同时删除云端文件副本）。
     *
     * @param removeCloudCopy true=引用计数归零时回收物理文件；false=仅移出个人书架
     */
    @DeleteMapping("/{itemId}")
    public ApiResponse<Void> remove(
            @PathVariable("itemId") Long itemId,
            @RequestParam(value = "removeCloudCopy", required = false, defaultValue = "false") boolean removeCloudCopy) {
        Long userId = ownershipGuard.requireCurrentUserId();
        bookshelfService.remove(userId, itemId, removeCloudCopy);
        return ApiResponse.ok();
    }
}
