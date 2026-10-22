package com.zhimu.slmp.today;

import androidx.annotation.NonNull;

import com.zhimu.slmp.network.GsonFactory;

/**
 * 测试数据：用与网络层相同的 Gson（下划线命名）从 JSON 构造 VO，数据与 Mock 一致（今天 2026-10-19 周一）。
 */
public final class TestData {

    private TestData() {
    }

    @NonNull
    public static <T> T parse(@NonNull String json, @NonNull Class<T> cls) {
        return GsonFactory.get().fromJson(json.replace('\'', '"'), cls);
    }

    public static final String OWNER_HOME = "{'server_time':'2026-10-19 09:30:00','greeting':'早上好','user_name':'王建国',"
            + "'avatar_text':'王','role':'OWNER','role_text':'你是本场所有者','farm_status':'NORMAL',"
            + "'farm':{'farm_id':1001,'name':'东山猪场'},'has_pen':true,'unread_count':3,"
            + "'owner_metrics':{'stock_count':870,'today_todo_count':16,'overdue_count':4,'warning_count':1,"
            + "'count_unit':'HEAD','count_unit_text':'头','pen_occupancy_rate':0.544},"
            + "'ai_hint':'3 号育肥舍有猪腹泻，先怎么处理？'}";

    public static final String STAFF_HOME = "{'server_time':'2026-10-19 09:30:00','greeting':'早上好','user_name':'李明',"
            + "'avatar_text':'李','role':'STAFF','role_text':'东山猪场员工','farm_status':'NORMAL',"
            + "'farm':{'farm_id':1001,'name':'东山猪场'},'has_pen':true,'unread_count':2,"
            + "'staff_metrics':{'pen_count':2,'today_todo_count':7,'overdue_count':1,'today_done_count':1,"
            + "'pen_names':['3 号育肥舍','4 号育肥舍'],'today_done_names':[]},"
            + "'my_pens':[{'pen_id':2003,'pen_name':'3 号育肥舍','todo_count':5,'batches':[]},"
            + "{'pen_id':2004,'pen_name':'4 号育肥舍','todo_count':2,'batches':[]}]}";

    public static final String PENDING_HOME = "{'server_time':'2026-10-19 09:30:00','greeting':'早上好','user_name':'王建国',"
            + "'role':'OWNER','role_text':'你是本场所有者','farm_status':'PENDING','farm':{'farm_id':1003,'name':'北湾牛场'},"
            + "'has_pen':false,'unread_count':3}";

    @NonNull
    public static String todo(long id, @NonNull String status, @NonNull String category) {
        return "{'todo_id':" + id + ",'farm_id':1001,'farm_name':'东山猪场','todo_type':'FEEDING','category':'" + category
                + "','display_status':'" + status + "','title':'1 号保育舍 · 早餐饲喂','tail_text':'已逾期','version':1,"
                + "'list_link':{'route':'G02','farm_id':1001,'need_switch':false,'params':{'pen_id':'2001'}}}";
    }

    @NonNull
    public static String message(long id, boolean read, @NonNull String type) {
        return "{'message_id':" + id + ",'category':'AI','msg_type':'" + type + "','title':'AI 诊断：疑似猪流行性腹泻',"
                + "'read':" + read + ",'date_group':'TODAY','sort_time':'2026-10-19 09:21:00','route':'I06'"
                + ("FARM_INVITE".equals(type)
                ? ",'action':{'action_type':'INVITE','action_id':7001,'status':'PENDING','can_accept':true}" : "")
                + "}";
    }
}
