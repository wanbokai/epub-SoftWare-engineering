package com.reader.parser;

import com.reader.common.BusinessException;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 解析服务门面：根据文件类型路由到 TXT / EPUB 解析器。
 * <p>
 * 后端解析引擎仅用于元数据与目录预抽取（供秒传复用书名/作者，见 M2），
 * 完整解析结果通过统一书对象 {@link Book} 返回。
 */
@Service
public class ParserService {

    private final TxtParserService txtParserService;
    private final EpubParserService epubParserService;

    public ParserService(TxtParserService txtParserService, EpubParserService epubParserService) {
        this.txtParserService = txtParserService;
        this.epubParserService = epubParserService;
    }

    /**
     * 解析文件字节，输出统一书对象。
     *
     * @param content  文件字节
     * @param fileType 1=EPUB, 2=TXT
     * @param title    展示书名（TXT 无元数据时用作书名）
     */
    public Book parse(byte[] content, int fileType, String title) {
        try {
            if (fileType == 1) {
                return epubParserService.parse(content);
            } else if (fileType == 2) {
                return txtParserService.parse(content, title);
            }
            throw new BusinessException(400, "不支持的文件类型（1=EPUB, 2=TXT）");
        } catch (IOException e) {
            throw new BusinessException(400, "文件解析失败：" + e.getMessage());
        }
    }

    /**
     * 仅抽取元数据（书名/作者），供上传登记与秒传预检复用。
     * <p>
     * 不解析章节正文，EPUB 只读 container.xml + OPF，避免为拿书名付出整本解析的代价。
     */
    public Book.BookMeta extractMeta(byte[] content, int fileType, String title) {
        try {
            if (fileType == 1) {
                return epubParserService.extractMeta(content);
            } else if (fileType == 2) {
                return txtParserService.extractMeta(title);
            }
            throw new BusinessException(400, "不支持的文件类型（1=EPUB, 2=TXT）");
        } catch (IOException e) {
            throw new BusinessException(400, "文件解析失败：" + e.getMessage());
        }
    }
}
