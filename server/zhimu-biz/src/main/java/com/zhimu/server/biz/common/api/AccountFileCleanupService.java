package com.zhimu.server.biz.common.api;

import java.util.Collection;

/** Cancellation-only outbox: enqueue in the business transaction, erase objects after commit. */
public interface AccountFileCleanupService {
    void queuePersonalFiles(Long userId);
    void queueFarmFiles(Long userId, Long farmId, Collection<Long> fileIds);
    void purgeQueued(Long userId);
    boolean hasPending(Long userId);
}
