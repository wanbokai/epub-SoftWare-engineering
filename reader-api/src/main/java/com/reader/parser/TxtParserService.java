package com.reader.parser;

import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TXT 解析器：编码探测 + 正则分章。
 * <p>
 * 编码探测顺序：BOM → UTF-8 校验 → GB18030 → UTF-16（juniversalchardet 兜底）。
 * 章节识别：匹配常见章节标题（第X章/第X节/第X回/Chapter N 等），无匹配则整文单章兜底。
 */
@Component
public class TxtParserService {

    // 章节标题正则：第[数字/汉字/一二三…]+[章|节|回|卷|篇] 或 Chapter/Section + 数字
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^\\s*(第\\s*[0-9零一二三四五六七八九十百千万两]+\\s*[章節节回卷篇部]|" +
                    "(?:Chapter|Section|CHAPTER|SECTION)\\s+[0-9IVXivx]+)\\s*[：:、.．\\s]?.*$");

    /**
     * 解析 TXT 字节流，输出统一书对象。
     *
     * @param content 原始字节
     * @param title   展示书名（无元数据时用作书名）
     */
    public Book parse(byte[] content, String title) throws IOException {
        String text = decode(content);
        return splitChapters(text, title);
    }

    /**
     * 编码探测并解码为 UTF-8 字符串。
     */
    public String decode(byte[] content) throws IOException {
        Charset charset = detectCharset(content);
        return new String(content, charset);
    }

    /**
     * 编码探测：BOM 优先，其次 UTF-8 严格校验，再 GB18030 / UTF-16，最后 juniversalchardet 兜底。
     */
    private Charset detectCharset(byte[] content) {
        // 1. BOM 探测
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (content.length >= 2 && (content[0] & 0xFF) == 0xFE && (content[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (content.length >= 2 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }

        // 2. UTF-8 严格校验
        if (isValidUtf8(content)) {
            return StandardCharsets.UTF_8;
        }

        // 3. juniversalchardet 兜底
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(content, 0, content.length);
        detector.dataEnd();
        String detected = detector.getDetectedCharset();
        detector.reset();
        if (detected != null) {
            try {
                return Charset.forName(detected);
            } catch (Exception ignored) {
                // 忽略不支持的字符集名
            }
        }

        // 4. 默认 GB18030（中文 TXT 常见编码）
        return Charset.forName("GB18030");
    }

    /**
     * 严格校验字节序列是否为合法 UTF-8。
     */
    private boolean isValidUtf8(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            int expected;
            if (b <= 0x7F) {
                i++;
                continue;
            } else if (b >= 0xC2 && b <= 0xDF) {
                expected = 1;
            } else if (b >= 0xE0 && b <= 0xEF) {
                expected = 2;
            } else if (b >= 0xF0 && b <= 0xF4) {
                expected = 3;
            } else {
                return false;
            }
            if (i + expected >= bytes.length) {
                return false;
            }
            for (int j = 1; j <= expected; j++) {
                if ((bytes[i + j] & 0xC0) != 0x80) {
                    return false;
                }
            }
            i += expected + 1;
        }
        return true;
    }

    /**
     * 正则分章：按章节标题切分，无匹配则整文单章。
     */
    private Book splitChapters(String text, String title) {
        Book book = new Book();
        Book.BookMeta meta = new Book.BookMeta();
        meta.setTitle(title != null ? title : "未命名书籍");
        book.setMeta(meta);

        String[] lines = text.split("\\r?\\n", -1);

        // 扫描章节起始行
        java.util.List<Integer> chapterStarts = new java.util.ArrayList<>();
        java.util.List<String> chapterTitles = new java.util.ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher m = CHAPTER_PATTERN.matcher(lines[i]);
            if (m.matches()) {
                chapterStarts.add(i);
                chapterTitles.add(lines[i].trim());
            }
        }

        // 无章节标题：整文单章兜底
        if (chapterStarts.isEmpty()) {
            Book.Chapter single = new Book.Chapter();
            single.setId("1");
            single.setTitle("正文");
            single.setContent(String.join("\n", lines));
            book.getChapters().add(single);
            book.getSpine().add("1");

            Book.TocItem root = new Book.TocItem();
            root.setTitle("正文");
            root.setHref("1");
            root.setLevel(1);
            book.getToc().add(root);
            return book;
        }

        // 正文前的内容作为「前言」章节（若有）
        if (chapterStarts.get(0) > 0) {
            String prefix = joinRange(lines, 0, chapterStarts.get(0));
            if (!prefix.isBlank()) {
                Book.Chapter preface = new Book.Chapter();
                preface.setId("0");
                preface.setTitle("前言");
                preface.setContent(prefix);
                book.getChapters().add(preface);
                book.getSpine().add("0");
            }
        }

        // 切分各章
        for (int c = 0; c < chapterStarts.size(); c++) {
            int start = chapterStarts.get(c);
            int end = (c + 1 < chapterStarts.size()) ? chapterStarts.get(c + 1) : lines.length;

            Book.Chapter chapter = new Book.Chapter();
            chapter.setId(String.valueOf(c + 1));
            chapter.setTitle(chapterTitles.get(c));
            chapter.setContent(joinRange(lines, start, end));
            book.getChapters().add(chapter);
            book.getSpine().add(String.valueOf(c + 1));

            Book.TocItem tocItem = new Book.TocItem();
            tocItem.setTitle(chapterTitles.get(c));
            tocItem.setHref(String.valueOf(c + 1));
            tocItem.setLevel(1);
            book.getToc().add(tocItem);
        }

        return book;
    }

    private String joinRange(String[] lines, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to && i < lines.length; i++) {
            sb.append(lines[i]).append('\n');
        }
        return sb.toString();
    }
}
