package com.reader.parser;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已解析书籍的进程内缓存。
 * <p>
 * 同一本书重复打开时直接复用，避免每次都从对象存储下载并重新解析。
 * 用 LRU 限制本数，并跳过超大书籍，防止占满堆内存。
 */
@Component
public class BookCache {

    private static final int MAX_BOOKS = 2;
    private static final long MAX_CHARS = 40_000_000L;

    private final Map<Long, Book> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Book> eldest) {
            return size() > MAX_BOOKS;
        }
    };

    public synchronized Book get(Long fileId) {
        return cache.get(fileId);
    }

    public synchronized void put(Long fileId, Book book) {
        if (book == null || book.getChapters() == null) {
            return;
        }
        long chars = 0;
        for (Book.Chapter chapter : book.getChapters()) {
            if (chapter.getContent() != null) {
                chars += chapter.getContent().length();
            }
        }
        if (chars > MAX_CHARS) {
            return;
        }
        cache.put(fileId, book);
    }

    public synchronized void evict(Long fileId) {
        cache.remove(fileId);
    }
}
