package com.reader.file;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 物理文件资源数据访问接口。
 */
@Mapper
public interface FileAssetMapper {

    FileAsset findBySha256(@Param("sha256") String sha256);

    FileAsset findById(@Param("id") Long id);

    int insert(FileAsset fileAsset);

    /**
     * 补写/更新元数据（书名、作者）。
     */
    int updateMeta(@Param("id") Long id,
                   @Param("metaTitle") String metaTitle,
                   @Param("metaAuthor") String metaAuthor);

    int incrementRefCount(@Param("id") Long id);

    int decrementRefCount(@Param("id") Long id);

    int deleteById(@Param("id") Long id);
}
