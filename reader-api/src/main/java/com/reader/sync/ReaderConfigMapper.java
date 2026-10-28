package com.reader.sync;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 排版配置数据访问接口。
 */
@Mapper
public interface ReaderConfigMapper {

    ReaderConfig findByUserId(@Param("userId") Long userId);

    int insert(ReaderConfig config);

    int update(ReaderConfig config);

    /**
     * 乐观锁更新：仅当当前版本号匹配时更新并 +1。
     *
     * @return 受影响行数（0 表示版本冲突）
     */
    int updateWithVersion(@Param("config") ReaderConfig config, @Param("expectedVersion") int expectedVersion);
}
