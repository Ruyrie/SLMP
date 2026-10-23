package com.zhimu.server.admin.system.api;

import com.zhimu.server.admin.system.query.AdminMessageQuery;
import com.zhimu.server.admin.system.request.MessageReadRequest;
import com.zhimu.server.admin.system.vo.AdminMessageVO;
import com.zhimu.server.common.api.PageVO;

/**
 * 管理端站内消息（顶栏铃铛）。各管理端模块调用 send / sendToPermission 写入；
 * unreadCount 供 ADM-S41 与 ADM-S05 使用；merge_key 用于去重。
 */
public interface AdminMessageService {

    /** 发给单个管理员；merge_key 非空时同一接收人同一键只保留一条 */
    void send(AdminMessageCommand command);

    /** 发给拥有该权限码的全部正常管理员（超管始终拥有全部权限） */
    void sendToPermission(String permCode, AdminMessageCommand command);

    /** 当前管理员未读数 */
    int unreadCount();

    int unreadCount(Long adminId);

    /** ADM-S40 本人消息分页，按时间倒序，tab_counts：all、unread */
    PageVO<AdminMessageVO> page(AdminMessageQuery query);

    /** ADM-S42 标记已读：message_ids 与 all 二选一，返回更新条数 */
    int markRead(MessageReadRequest request);
}
