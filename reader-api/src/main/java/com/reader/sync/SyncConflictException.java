package com.reader.sync;

/**
 * 同步版本冲突异常，携带云端/本端冲突摘要，映射为 HTTP 409。
 */
public class SyncConflictException extends RuntimeException {

    private final ConflictResponse conflict;

    public SyncConflictException(ConflictResponse conflict) {
        super("版本冲突");
        this.conflict = conflict;
    }

    public ConflictResponse getConflict() {
        return conflict;
    }
}
