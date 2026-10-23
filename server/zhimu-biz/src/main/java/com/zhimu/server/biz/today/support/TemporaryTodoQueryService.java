package com.zhimu.server.biz.today.support;

import com.zhimu.server.biz.today.api.TodoQueryService;
import org.springframework.stereotype.Service;

/** 临时实现：没有待办。第 3 批删除 */
@Service
public class TemporaryTodoQueryService implements TodoQueryService {

    @Override
    public int countPendingForMember(Long farmId, Long userId) {
        return 0;
    }
}