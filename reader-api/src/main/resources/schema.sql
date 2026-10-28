-- 云端 EPUB/TXT 图书阅读器 - 数据库初始化脚本（对应设计文档第 5 章数据设计）
-- MySQL 8.0+

CREATE DATABASE IF NOT EXISTS reader_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE reader_db;

-- 表 5-1 用户表 user
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户唯一标识',
    `username`      VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password_hash` CHAR(60)     NOT NULL COMMENT 'BCrypt 密码哈希（含盐）',
    `email`         VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- 表 5-2 物理文件资源表 file_asset
CREATE TABLE IF NOT EXISTS `file_asset` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '资源唯一标识',
    `sha256`      CHAR(64)     NOT NULL COMMENT '内容指纹，秒传判定键',
    `storage_key` VARCHAR(200) NOT NULL COMMENT '对象存储键（随机化命名）',
    `file_type`   TINYINT      NOT NULL COMMENT '1=EPUB, 2=TXT',
    `file_size`   INT          NOT NULL COMMENT '文件大小（字节）',
    `meta_title`  VARCHAR(200) DEFAULT NULL COMMENT '解析出的书名（秒传时复用）',
    `meta_author` VARCHAR(100) DEFAULT NULL COMMENT '解析出的作者',
    `ref_count`   INT          NOT NULL DEFAULT 0 COMMENT '引用计数，为 0 时可回收',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sha256` (`sha256`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='物理文件资源表';

-- 表 5-3 用户书架表 bookshelf_item
CREATE TABLE IF NOT EXISTS `bookshelf_item` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '条目标识',
    `user_id`          BIGINT        NOT NULL COMMENT '归属用户',
    `file_id`          BIGINT        NOT NULL COMMENT '引用的物理文件',
    `display_title`    VARCHAR(100)  NOT NULL COMMENT '展示书名（可重命名）',
    `progress_cfi`     VARCHAR(500)  DEFAULT NULL COMMENT '进度锚点（TXT 存章节号+段内偏移）',
    `progress_percent` DECIMAL(5, 2) DEFAULT 0 COMMENT '全书进度百分比',
    `last_read_at`     DATETIME      DEFAULT NULL COMMENT '最近阅读时间（排序依据）',
    `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入架时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_file` (`user_id`, `file_id`),
    KEY `idx_user_last_read` (`user_id`, `last_read_at`),
    CONSTRAINT `fk_bookshelf_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_bookshelf_file` FOREIGN KEY (`file_id`) REFERENCES `file_asset` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户书架表';

-- 表 5-4 排版配置表 reader_config
CREATE TABLE IF NOT EXISTS `reader_config` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '配置标识',
    `user_id`      BIGINT       NOT NULL COMMENT '归属用户',
    `theme`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1日间/2夜间/3羊皮纸/4护眼',
    `font_size`    TINYINT      NOT NULL DEFAULT 16 COMMENT '字号（px）',
    `line_height`  DECIMAL(3, 1) NOT NULL DEFAULT 1.5 COMMENT '行高（倍）',
    `page_margin`  TINYINT      NOT NULL DEFAULT 2 COMMENT '1窄/2中/3宽',
    `page_mode`    TINYINT      NOT NULL DEFAULT 1 COMMENT '1分页/2流式',
    `sync_version` INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（冲突检测依据）',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`),
    CONSTRAINT `fk_config_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='排版配置表';
