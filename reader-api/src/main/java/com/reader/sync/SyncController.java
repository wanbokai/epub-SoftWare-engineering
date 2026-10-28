package com.reader.sync;

import com.reader.auth.OwnershipGuard;
import com.reader.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 同步接口控制器：上行进度/配置、下行拉取、冲突消解。
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService syncService;
    private final OwnershipGuard ownershipGuard;

    public SyncController(SyncService syncService, OwnershipGuard ownershipGuard) {
        this.syncService = syncService;
        this.ownershipGuard = ownershipGuard;
    }

    /**
     * 上行进度（乐观锁，版本落后返回 409）。
     */
    @PutMapping("/progress/{itemId}")
    public ApiResponse<Integer> pushProgress(@PathVariable("itemId") Long itemId,
                                             @RequestBody PushProgressRequest request) {
        Long userId = ownershipGuard.requireCurrentUserId();
        return ApiResponse.ok(syncService.pushProgress(userId, itemId, request));
    }

    /**
     * 上行配置（乐观锁）。
     */
    @PutMapping("/config")
    public ApiResponse<Integer> pushConfig(@RequestBody PushConfigRequest request) {
        Long userId = ownershipGuard.requireCurrentUserId();
        return ApiResponse.ok(syncService.pushConfig(userId, request));
    }

    /**
     * 下行拉取：进度列表 + 配置。
     */
    @GetMapping("/pull")
    public ApiResponse<SyncPullResponse> pull() {
        Long userId = ownershipGuard.requireCurrentUserId();
        return ApiResponse.ok(syncService.pull(userId));
    }

    /**
     * 冲突消解。
     */
    @PostMapping("/resolve/{itemId}")
    public ApiResponse<Integer> resolve(@PathVariable("itemId") Long itemId,
                                        @RequestBody ResolveRequest request) {
        Long userId = ownershipGuard.requireCurrentUserId();
        return ApiResponse.ok(syncService.resolve(userId, itemId, request));
    }
}
