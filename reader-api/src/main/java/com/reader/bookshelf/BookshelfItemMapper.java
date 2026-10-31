package com.reader.bookshelf;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 书架条目数据访问接口。
 */
@Mapper
public interface BookshelfItemMapper {

    int insert(BookshelfItem item);

    BookshelfItem findById(@Param("id") Long id);

    BookshelfItem findByUserAndFile(@Param("userId") Long userId, @Param("fileId") Long fileId);

    /**
     * 列出用户书架。
     *
     * @param sort 排序方式：lastRead（最近阅读）/ created（导入时间）/ title（书名）
     */
    List<BookshelfItem> listByUser(@Param("userId") Long userId,
                                   @Param("keyword") String keyword,
                                   @Param("sort") String sort);

    int update(BookshelfItem item);

    int updateProgress(@Param("id") Long id,
                       @Param("progressCfi") String progressCfi,
                       @Param("progressPercent") java.math.BigDecimal progressPercent,
                       @Param("lastReadAt") java.time.LocalDateTime lastReadAt);

    /**
     * 乐观锁进度更新：仅当版本号匹配时更新并 +1。
     *
     * @return 受影响行数（0 表示版本冲突）
     */
    int updateProgressWithVersion(@Param("id") Long id,
                                  @Param("progressCfi") String progressCfi,
                                  @Param("progressPercent") java.math.BigDecimal progressPercent,
                                  @Param("expectedVersion") int expectedVersion);

    int deleteById(@Param("id") Long id);

    int countByFileId(@Param("fileId") Long fileId);
}
