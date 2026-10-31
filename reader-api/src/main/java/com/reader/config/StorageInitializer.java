package com.reader.config;

import com.reader.file.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动后确保对象存储桶存在。
 * <p>
 * 失败只记录告警、不阻断启动：本地开发时 SeaweedFS 可能晚于后端启动，
 * 首次上传前仍可通过日志定位问题。
 */
@Component
public class StorageInitializer {

    private static final Logger log = LoggerFactory.getLogger(StorageInitializer.class);

    private final StorageService storageService;

    public StorageInitializer(StorageService storageService) {
        this.storageService = storageService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        try {
            storageService.ensureBucket();
            log.info("对象存储桶已就绪");
        } catch (Exception e) {
            log.warn("对象存储桶初始化失败，请确认 SeaweedFS 已启动且桶可创建：{}", e.getMessage());
        }
    }
}
