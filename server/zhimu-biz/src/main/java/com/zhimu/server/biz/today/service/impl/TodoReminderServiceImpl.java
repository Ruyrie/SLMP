package com.zhimu.server.biz.today.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.today.api.TodoReminderService;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.entity.TodoRemind;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.biz.today.mapper.TodoRemindMapper;
import com.zhimu.server.common.enums.MsgType;
import com.zhimu.server.common.enums.TodoStatus;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** {@link TodoReminderService}：唯一键 uk_tdr_cycle（todo_id, remind_stage, cycle_key）保证每周期只登记一次。 */
@Service
@RequiredArgsConstructor
public class TodoReminderServiceImpl implements TodoReminderService {

    private final TodoMapper todoMapper;
    private final TodoRemindMapper remindMapper;

    @Override
    @Transactional
    public Long claim(Long farmId, String todoType, String dedupKey, String remindStage, String cycleKey,
                      String receiverScope, int receiverCount) {
        if (farmId == null || todoType == null || dedupKey == null || remindStage == null || cycleKey == null) {
            return null;
        }
        return TenantSkip.call(() -> {
            Todo todo = todoMapper.selectByDedupIncludingDeleted(farmId, todoType, dedupKey);
            if (todo == null || Boolean.TRUE.equals(todo.getDeleted())
                    || !TodoStatus.PENDING.name().equals(todo.getStatus())) {
                return null;
            }
            Long exists = remindMapper.selectCount(Wrappers.<TodoRemind>lambdaQuery()
                    .eq(TodoRemind::getTodoId, todo.getId())
                    .eq(TodoRemind::getRemindStage, remindStage)
                    .eq(TodoRemind::getCycleKey, cycleKey));
            if (exists != null && exists > 0) {
                return null;
            }
            LocalDateTime now = BeijingTime.now();
            TodoRemind remind = new TodoRemind();
            remind.setTodoId(todo.getId());
            remind.setFarmId(farmId);
            remind.setRemindStage(remindStage);
            remind.setCycleKey(cycleKey);
            remind.setRemindWay("PLAN");
            remind.setRemindAt(now);
            remind.setReceiverScope(receiverScope == null ? "MANAGERS" : receiverScope);
            remind.setMsgType(MsgType.TODO_REMIND.name());
            remind.setStatus("SENT");
            remind.setSentAt(now);
            remind.setReceiverCount(Math.max(0, receiverCount));
            try {
                remindMapper.insert(remind);
            } catch (DuplicateKeyException e) {
                return null;
            }
            todoMapper.update(new Todo(), Wrappers.<Todo>lambdaUpdate()
                    .eq(Todo::getId, todo.getId())
                    .setSql("remind_count = remind_count + 1")
                    .set(Todo::getLastRemindAt, now));
            return todo.getId();
        });
    }
}
