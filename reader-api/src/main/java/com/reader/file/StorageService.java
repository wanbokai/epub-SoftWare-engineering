package com.reader.file;

import com.reader.config.StorageProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 对象存储服务：封装 S3 兼容 SDK，负责物理文件的存取。
 */
@Service
public class StorageService {

    private final S3Client s3Client;
    private final StorageProperties properties;

    public StorageService(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /**
     * 上传文件到对象存储。
     */
    public void upload(String storageKey, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(storageKey)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
    }

    /**
     * 删除物理文件。
     */
    public void delete(String storageKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(storageKey)
                .build();
        s3Client.deleteObject(request);
    }

    /**
     * 确保存储桶存在（应用启动时调用）。
     */
    public void ensureBucket() {
        try {
            s3Client.headBucket(b -> b.bucket(properties.getBucket()));
        } catch (Exception e) {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(properties.getBucket())
                    .build());
        }
    }
}
