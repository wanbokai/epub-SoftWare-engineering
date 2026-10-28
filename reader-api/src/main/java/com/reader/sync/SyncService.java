package com.reader.sync;

import com.reader.auth.OwnershipGuard;
import com.reader.bookshelf.BookshelfItem;
import com.reader.bookshelf.BookshelfItemMapper;
import com.reader.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 同步服务：进度与配置的乐观锁上行写入、下行拉取、冲突消解。
 *
 * 一致性策略（对应设计文档 9.4 决策 D4）：
 * - 乐观锁：客户端携带上次读到的 sync_version，服务端比对一致才写入并 +1；
 * - 冲突：版本落后则返回 409 冲突摘要（云端 vs 本端）；
 * - LWW 兜底：choice=lww 时按 updated_at 最后写入胜。
 */
@Service
public class SyncService {

    private final BookshelfItemMapper bookshelfItemMapper;
    private final ReaderConfigMapper readerConfigMapper;
    private final OwnershipGuard ownershipGuard;

    public SyncService(BookshelfItemMapper bookshelfItemMapper,
                       ReaderConfigMapper readerConfigMapper,
                       OwnershipGuard ownershipGuard) {
        this.bookshelfItemMapper = bookshelfItemMapper;
        this.readerConfigMapper = readerConfigMapper;
        this.ownershipGuard = ownershipGuard;
    }

    /**
     * 上行进度（乐观锁）：版本一致则写入并 +1，落后则抛出冲突异常（409）。
     */
    @Transactional
    public Integer pushProgress(Long userId, Long itemId, PushProgressRequest request) {
        BookshelfItem item = getOwnedItem(userId, itemId);

        int expected = request.getSyncVersion() != null ? request.getSyncVersion() : 0;
        int updated = bookshelfItemMapper.updateProgressWithVersion(
                itemId, request.getCfi(), request.getPercent(), expected);

        if (updated == 0) {
            // 版本冲突：返回云端摘要
            BookshelfItem cloud = bookshelfItemMapper.findById(itemId);
            ConflictResponse.ProgressSummary cloudSummary =
                    new ConflictResponse.ProgressSummary(cloud.getProgressCfi(), cloud.getProgressPercent());
            ConflictResponse.ProgressSummary localSummary =
                    new ConflictResponse.ProgressSummary(request.getCfi(), request.getPercent());
            throw new SyncConflictException(
                    new ConflictResponse(cloudSummary, localSummary,
                            cloud.getSyncVersion(), expected));
        }

        return expected + 1;
    }

    /**
     * 上行配置（乐观锁）：配置不存在则首次创建。
     */
    @Transactional
    public Integer pushConfig(Long userId, PushConfigRequest request) {
        ReaderConfig config = readerConfigMapper.findByUserId(userId);

        if (config == null) {
            // 首次写入：创建配置，版本置 0
            config = new ReaderConfig();
            config.setUserId(userId);
            config.setTheme(defaultIfNull(request.getTheme(), 1));
            config.setFontSize(defaultIfNull(request.getFontSize(), 16));
            config.setLineHeight(defaultIfNull(request.getLineHeight(), new BigDecimal("1.5")));
            config.setPageMargin(defaultIfNull(request.getPageMargin(), 2));
            config.setPageMode(defaultIfNull(request.getPageMode(), 1));
            readerConfigMapper.insert(config);
            return 0;
        }

        int expected = request.getSyncVersion() != null ? request.getSyncVersion() : config.getSyncVersion();
        config.setTheme(defaultIfNull(request.getTheme(), config.getTheme()));
        config.setFontSize(defaultIfNull(request.getFontSize(), config.getFontSize()));
        config.setLineHeight(defaultIfNull(request.getLineHeight(), config.getLineHeight()));
        config.setPageMargin(defaultIfNull(request.getPageMargin(), config.getPageMargin()));
        config.setPageMode(defaultIfNull(request.getPageMode(), config.getPageMode()));

        int updated = readerConfigMapper.updateWithVersion(config, expected);
        if (updated == 0) {
            throw new BusinessException(409, "配置版本冲突，请先拉取最新配置");
        }
        return expected + 1;
    }

    /**
     * 下行拉取：返回当前用户所有书架条目的进度，以及排版配置。
     */
    public SyncPullResponse pull(Long userId) {
        SyncPullResponse response = new SyncPullResponse();
        response.setProgressList(bookshelfItemMapper.listByUser(userId, null));
        response.setConfig(readerConfigMapper.findByUserId(userId));
        return response;
    }

    /**
     * 冲突消解：choice 为 cloud（保留云端）/ local（采用本机）/ lww（最后写入胜）。
     */
    @Transactional
    public Integer resolve(Long userId, Long itemId, ResolveRequest request) {
        BookshelfItem item = getOwnedItem(userId, itemId);
        String choice = request.getChoice();

        if (choice == null) {
            throw new BusinessException(400, "缺少消解选项 choice");
        }

        switch (choice) {
            case "cloud":
                // 保留云端：仅推进版本号，不改变内容
                return bumpVersion(item);
            case "local":
                // 采用本机：用本端进度覆盖，版本 +1
                if (request.getCfi() == null && request.getPercent() == null) {
                    throw new BusinessException(400, "采用本机需携带本端进度");
                }
                bookshelfItemMapper.updateProgressWithVersion(
                        itemId, request.getCfi(), request.getPercent(), item.getSyncVersion());
                return item.getSyncVersion() + 1;
            case "lww":
                // 最后写入胜：本端携带进度则覆盖（本端已是最新）
                if (request.getCfi() != null || request.getPercent() != null) {
                    bookshelfItemMapper.updateProgressWithVersion(
                            itemId, request.getCfi(), request.getPercent(), item.getSyncVersion());
                }
                return item.getSyncVersion() + 1;
            default:
                throw new BusinessException(400, "非法的消解选项（cloud/local/lww）");
        }
    }

    private int bumpVersion(BookshelfItem item) {
        bookshelfItemMapper.updateProgressWithVersion(
                item.getId(), item.getProgressCfi(), item.getProgressPercent(), item.getSyncVersion());
        return item.getSyncVersion() + 1;
    }

    private BookshelfItem getOwnedItem(Long userId, Long itemId) {
        BookshelfItem item = bookshelfItemMapper.findById(itemId);
        if (item == null) {
            throw new BusinessException(404, "书架条目不存在");
        }
        ownershipGuard.assertOwnership(item.getUserId());
        return item;
    }

    private <T> T defaultIfNull(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
