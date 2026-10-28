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

    List<BookshelfItem> listByUser(@Param("userId") Long userId, @Param("keyword") String keyword);

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
