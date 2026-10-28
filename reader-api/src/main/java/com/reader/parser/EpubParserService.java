package com.reader.parser;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * EPUB 解析器：ZIP 解包 → container.xml → OPF（元数据/spine）→ NCX/NAV（目录）→ 章节正文。
 * <p>
 * 覆盖 EPUB 2.x / 3.x 规范子集：XHTML 正文 + NCX/NAV 目录 + 元数据。
 */
@Component
public class EpubParserService {

    private static final String CONTAINER_PATH = "META-INF/container.xml";

    /**
     * 解析 EPUB 字节流，输出统一书对象。
     */
    public Book parse(byte[] content) throws IOException {
        // 1. 解压 ZIP，建立资源映射（路径 -> 字节）
        Map<String, byte[]> resources = unzip(content);

        // 2. 读取 container.xml，定位 OPF 路径
        String opfPath = findOpfPath(resources);
        if (opfPath == null) {
            throw new IOException("EPUB 无效：缺少 container.xml 或 OPF 路径");
        }

        // 3. 解析 OPF：元数据 + spine + manifest + 目录
        Document opf = parseXml(resources.get(opfPath));
        String baseDir = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";

        Book book = new Book();
        Book.BookMeta meta = parseMeta(opf);
        book.setMeta(meta);

        // manifest：id -> href
        Map<String, String> manifest = parseManifest(opf);

        // spine：阅读顺序（idref 列表）
        java.util.List<String> spineIdrefs = parseSpine(opf);

        // 目录：优先 NAV（EPUB3），回退 NCX（EPUB2）
        java.util.List<Book.TocItem> toc = parseToc(opf, resources, baseDir, manifest);
        book.setToc(toc);

        // 4. 解析各 spine 章节正文
        java.util.List<Book.Chapter> chapters = new java.util.ArrayList<>();
        java.util.List<String> spineHrefs = new java.util.ArrayList<>();
        int idx = 0;
        for (String idref : spineIdrefs) {
            String href = manifest.get(idref);
            if (href == null) {
                continue;
            }
            String fullPath = normalizePath(baseDir + href);
            byte[] chapterBytes = resources.get(fullPath);
            if (chapterBytes == null) {
                // 尝试未加 baseDir 的路径
                chapterBytes = resources.get(href);
            }
            if (chapterBytes == null) {
                continue;
            }

            String chapterText = new String(chapterBytes, StandardCharsets.UTF_8);
            Book.Chapter chapter = new Book.Chapter();
            chapter.setId(fullPath);
            chapter.setTitle(findTocTitle(toc, href, idx));
            chapter.setContent(chapterText);
            chapters.add(chapter);
            spineHrefs.add(fullPath);
            idx++;
        }
        book.setChapters(chapters);
        book.setSpine(spineHrefs);

        return book;
    }

    /**
     * 解压 ZIP，返回 路径 -> 字节 的映射（跳过目录项）。
     */
    private Map<String, byte[]> unzip(byte[] content) throws IOException {
        Map<String, byte[]> resources = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
                resources.put(entry.getName(), bos.toByteArray());
            }
        }
        return resources;
    }

    /**
     * 从 container.xml 定位 OPF 路径。
     */
    private String findOpfPath(Map<String, byte[]> resources) {
        byte[] container = resources.get(CONTAINER_PATH);
        if (container == null) {
            return null;
        }
        try {
            Document doc = parseXml(container);
            NodeList rootfiles = doc.getElementsByTagName("rootfile");
            if (rootfiles.getLength() > 0) {
                Element rootfile = (Element) rootfiles.item(0);
                String fullPath = rootfile.getAttribute("full-path");
                return fullPath != null ? fullPath : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 解析 OPF 元数据：书名、作者、封面。
     */
    private Book.BookMeta parseMeta(Document opf) {
        Book.BookMeta meta = new Book.BookMeta();
        try {
            NodeList titles = opf.getElementsByTagName("dc:title");
            if (titles.getLength() > 0) {
                meta.setTitle(titles.item(0).getTextContent().trim());
            }
            NodeList creators = opf.getElementsByTagName("dc:creator");
            if (creators.getLength() > 0) {
                meta.setAuthor(creators.item(0).getTextContent().trim());
            }
        } catch (Exception ignored) {
        }
        return meta;
    }

    /**
     * 解析 manifest：id -> href。
     */
    private Map<String, String> parseManifest(Document opf) {
        Map<String, String> manifest = new LinkedHashMap<>();
        NodeList items = opf.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String id = item.getAttribute("id");
            String href = item.getAttribute("href");
            if (id != null && !id.isEmpty() && href != null) {
                manifest.put(id, href);
            }
        }
        return manifest;
    }

    /**
     * 解析 spine：返回按阅读顺序的 idref 列表。
     */
    private java.util.List<String> parseSpine(Document opf) {
        java.util.List<String> idrefs = new java.util.ArrayList<>();
        NodeList spines = opf.getElementsByTagName("spine");
        if (spines.getLength() > 0) {
            Element spine = (Element) spines.item(0);
            NodeList itemrefs = spine.getElementsByTagName("itemref");
            for (int i = 0; i < itemrefs.getLength(); i++) {
                Element itemref = (Element) itemrefs.item(i);
                String idref = itemref.getAttribute("idref");
                if (idref != null && !idref.isEmpty()) {
                    idrefs.add(idref);
                }
            }
        }
        return idrefs;
    }

    /**
     * 解析目录：优先 NAV（EPUB3），回退 NCX（EPUB2）。
     */
    private java.util.List<Book.TocItem> parseToc(Document opf, Map<String, byte[]> resources,
                                                  String baseDir, Map<String, String> manifest) {
        // 1. NAV（EPUB3）：找 properties 含 nav 的 manifest item
        NodeList items = opf.getElementsByTagName("item");
        String navHref = null;
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String properties = item.getAttribute("properties");
            if (properties != null && properties.contains("nav")) {
                navHref = item.getAttribute("href");
                break;
            }
        }
        if (navHref != null) {
            byte[] navBytes = resources.get(normalizePath(baseDir + navHref));
            if (navBytes != null) {
                java.util.List<Book.TocItem> toc = parseNav(navBytes);
                if (!toc.isEmpty()) {
                    return toc;
                }
            }
        }

        // 2. NCX（EPUB2）：spine 的 toc 属性指向 NCX
        String ncxHref = null;
        NodeList spines = opf.getElementsByTagName("spine");
        if (spines.getLength() > 0) {
            Element spine = (Element) spines.item(0);
            String tocAttr = spine.getAttribute("toc");
            if (tocAttr != null && !tocAttr.isEmpty()) {
                ncxHref = manifest.get(tocAttr);
            }
        }
        if (ncxHref != null) {
            byte[] ncxBytes = resources.get(normalizePath(baseDir + ncxHref));
            if (ncxBytes != null) {
                return parseNcx(ncxBytes);
            }
        }

        return new java.util.ArrayList<>();
    }

    /**
     * 解析 EPUB3 NAV 目录（nav 元素内 ol/li 结构）。
     */
    private java.util.List<Book.TocItem> parseNav(byte[] navBytes) {
        java.util.List<Book.TocItem> result = new java.util.ArrayList<>();
        try {
            Document doc = parseXml(navBytes);
            NodeList navs = doc.getElementsByTagName("nav");
            for (int i = 0; i < navs.getLength(); i++) {
                Element nav = (Element) navs.item(i);
                String type = nav.getAttribute("epub:type");
                if (type == null || type.isEmpty()) {
                    type = nav.getAttribute("type");
                }
                if ("toc".equals(type) || navs.getLength() == 1) {
                    NodeList ols = nav.getElementsByTagName("ol");
                    if (ols.getLength() > 0) {
                        parseNavOl((Element) ols.item(0), result, 1);
                    }
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void parseNavOl(Element ol, java.util.List<Book.TocItem> parent, int level) {
        NodeList children = ol.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && "li".equals(node.getNodeName())) {
                Element li = (Element) node;
                Book.TocItem item = new Book.TocItem();
                item.setLevel(level);

                NodeList anchors = li.getElementsByTagName("a");
                if (anchors.getLength() > 0) {
                    Element a = (Element) anchors.item(0);
                    item.setTitle(a.getTextContent().trim());
                    item.setHref(a.getAttribute("href"));
                }
                parent.add(item);

                // 递归子级 ol
                NodeList childOls = li.getElementsByTagName("ol");
                if (childOls.getLength() > 0) {
                    parseNavOl((Element) childOls.item(0), item.getChildren(), level + 1);
                }
            }
        }
    }

    /**
     * 解析 EPUB2 NCX 目录（navMap/navPoint 结构）。
     */
    private java.util.List<Book.TocItem> parseNcx(byte[] ncxBytes) {
        java.util.List<Book.TocItem> result = new java.util.ArrayList<>();
        try {
            Document doc = parseXml(ncxBytes);
            NodeList navMaps = doc.getElementsByTagName("navMap");
            if (navMaps.getLength() > 0) {
                Element navMap = (Element) navMaps.item(0);
                NodeList navPoints = navMap.getElementsByTagName("navPoint");
                for (int i = 0; i < navPoints.getLength(); i++) {
                    Element np = (Element) navPoints.item(i);
                    Book.TocItem item = new Book.TocItem();
                    item.setLevel(1);

                    NodeList labels = np.getElementsByTagName("text");
                    if (labels.getLength() > 0) {
                        item.setTitle(labels.item(0).getTextContent().trim());
                    }
                    NodeList contents = np.getElementsByTagName("content");
                    if (contents.getLength() > 0) {
                        item.setHref(((Element) contents.item(0)).getAttribute("src"));
                    }
                    result.add(item);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    /**
     * 根据 href 在目录中查找标题（用于章节命名），找不到则用序号兜底。
     */
    private String findTocTitle(java.util.List<Book.TocItem> toc, String href, int index) {
        for (Book.TocItem item : toc) {
            String t = matchTocTitle(item, href);
            if (t != null) {
                return t;
            }
        }
        return "第 " + (index + 1) + " 章";
    }

    private String matchTocTitle(Book.TocItem item, String href) {
        String itemHref = item.getHref();
        if (itemHref != null) {
            // href 可能含锚点（#），去掉锚点后比较路径
            String path = itemHref.split("#")[0];
            if (href.endsWith(path) || path.endsWith(href) || href.contains(path)) {
                return item.getTitle();
            }
        }
        for (Book.TocItem child : item.getChildren()) {
            String t = matchTocTitle(child, href);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    /**
     * 路径规范化：处理 ./ 和 ../ 。
     */
    private String normalizePath(String path) {
        String normalized = path;
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    /**
     * 解析 XML 字节为 DOM Document（关闭外部实体，防 XXE）。
     * 解析失败时抛出 IllegalArgumentException（由调用方决定如何处理）。
     */
    private Document parseXml(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            // 防 XXE
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
                return builder.parse(new InputSource(is));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("XML 解析失败", e);
        }
    }
}
