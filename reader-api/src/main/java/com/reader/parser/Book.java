package com.reader.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一书对象（Book）：前后端解析引擎对齐的 JSON schema。
 * <p>
 * 前端 TypeScript 版与后端 Java 版解析器均输出该结构，避免语义漂移。
 * 字段：元数据（书名/作者/封面）+ spine 阅读顺序 + 多级目录 + 章节数据。
 */
public class Book {

    private BookMeta meta;
    private List<String> spine = new ArrayList<>();       // 章节资源标识（按阅读顺序）
    private List<Chapter> chapters = new ArrayList<>();    // 章节内容列表
    private List<TocItem> toc = new ArrayList<>();         // 多级目录树

    public BookMeta getMeta() {
        return meta;
    }

    public void setMeta(BookMeta meta) {
        this.meta = meta;
    }

    public List<String> getSpine() {
        return spine;
    }

    public void setSpine(List<String> spine) {
        this.spine = spine;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    public void setChapters(List<Chapter> chapters) {
        this.chapters = chapters;
    }

    public List<TocItem> getToc() {
        return toc;
    }

    public void setToc(List<TocItem> toc) {
        this.toc = toc;
    }

    /**
     * 书籍元数据。
     */
    public static class BookMeta {
        private String title;
        private String author;
        private String cover;   // 封面资源标识（EPUB 内相对路径或 data 标识）

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getCover() {
            return cover;
        }

        public void setCover(String cover) {
            this.cover = cover;
        }
    }

    /**
     * 章节内容。
     */
    public static class Chapter {
        private String id;       // 章节唯一标识（EPUB 为资源路径，TXT 为章节序号）
        private String title;
        private String content;  // 正文（TXT 为纯文本，EPUB 为 XHTML 片段）

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * 目录条目（支持多级）。
     */
    public static class TocItem {
        private String title;
        private String href;     // 定位目标（EPUB 为资源锚点，TXT 为章节号）
        private int level;       // 层级（1 为顶级）
        private List<TocItem> children = new ArrayList<>();

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getHref() {
            return href;
        }

        public void setHref(String href) {
            this.href = href;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public List<TocItem> getChildren() {
            return children;
        }

        public void setChildren(List<TocItem> children) {
            this.children = children;
        }
    }
}
