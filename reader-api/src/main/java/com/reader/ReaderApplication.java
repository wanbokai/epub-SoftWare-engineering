package com.reader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 云端 EPUB/TXT 图书阅读器 - 后端服务启动入口。
 */
@SpringBootApplication
public class ReaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReaderApplication.class, args);
    }
}
