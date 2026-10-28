package com.reader.sync;

import com.reader.bookshelf.BookshelfItem;

import java.util.List;

/**
 * 下行拉取响应体：进度列表 + 排版配置。
 */
public class SyncPullResponse {

    private List<BookshelfItem> progressList;
    private ReaderConfig config;

    public List<BookshelfItem> getProgressList() {
        return progressList;
    }

    public void setProgressList(List<BookshelfItem> progressList) {
        this.progressList = progressList;
    }

    public ReaderConfig getConfig() {
        return config;
    }

    public void setConfig(ReaderConfig config) {
        this.config = config;
    }
}
