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
     * 列出书架（可选 keyword 检索）。
     */
    @GetMapping
    public ApiResponse<List<BookshelfItem>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        Long userId = ownershipGuard.requireCurrentUserId();
        return ApiResponse.ok(bookshelfService.list(userId, keyword));
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
     * 移除书架条目（含引用计数回收）。
     */
    @DeleteMapping("/{itemId}")
    public ApiResponse<Void> remove(@PathVariable("itemId") Long itemId) {
        Long userId = ownershipGuard.requireCurrentUserId();
        bookshelfService.remove(userId, itemId);
        return ApiResponse.ok();
    }
}
