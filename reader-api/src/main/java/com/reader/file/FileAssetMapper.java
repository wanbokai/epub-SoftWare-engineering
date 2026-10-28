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

    int incrementRefCount(@Param("id") Long id);

    int decrementRefCount(@Param("id") Long id);

    int deleteById(@Param("id") Long id);
}
