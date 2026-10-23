package com.zhimu.server.biz.today.convert;

import com.zhimu.server.biz.today.entity.ReminderSetting;
import com.zhimu.server.biz.today.support.TodayText;
import com.zhimu.server.biz.today.vo.ReminderSettingVO;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/** 提醒设置 Entity → VO：HH:mm、结束早于开始为次日、A07 副文字。 */
@Component
public class ReminderSettingConvert {

    public ReminderSettingVO toVO(ReminderSetting setting) {
        ReminderSettingVO vo = new ReminderSettingVO();
        vo.setTodoRemindEnabled(Boolean.TRUE.equals(setting.getTodoRemindEnabled()));
        vo.setDigestEnabled(Boolean.TRUE.equals(setting.getDigestEnabled()));
        vo.setDigestTime(TodayText.hm(setting.getDigestTime()));
        vo.setQuietEnabled(Boolean.TRUE.equals(setting.getQuietEnabled()));
        vo.setQuietStart(TodayText.hm(setting.getQuietStart()));
        vo.setQuietEnd(TodayText.hm(setting.getQuietEnd()));
        boolean nextDay = setting.getQuietStart() != null && setting.getQuietEnd() != null
                && !setting.getQuietEnd().isAfter(setting.getQuietStart());
        vo.setQuietEndNextDay(nextDay);
        vo.setSummaryText(summaryText(setting, nextDay));
        return vo;
    }

    /** “待办提醒开 · 每日汇总 07:00 · 免打扰 22:00–次日 07:00” */
    static String summaryText(ReminderSetting setting, boolean nextDay) {
        String todo = Boolean.TRUE.equals(setting.getTodoRemindEnabled()) ? "待办提醒开" : "待办提醒关";
        String digest = Boolean.TRUE.equals(setting.getDigestEnabled())
                ? "每日汇总 " + TodayText.hm(setting.getDigestTime()) : "每日汇总关闭";
        String quiet;
        if (Boolean.TRUE.equals(setting.getQuietEnabled())) {
            quiet = "免打扰 " + TodayText.hm(setting.getQuietStart())
                    + (nextDay ? "–次日 " : "–") + TodayText.hm(setting.getQuietEnd());
        } else {
            quiet = "免打扰关闭";
        }
        return todo + " · " + digest + " · " + quiet;
    }

    /** 默认设置：全部开启、汇总 07:00、免打扰 22:00–07:00 */
    public static ReminderSetting defaults(Long userId) {
        ReminderSetting setting = new ReminderSetting();
        setting.setUserId(userId);
        setting.setTodoRemindEnabled(true);
        setting.setDigestEnabled(true);
        setting.setDigestTime(LocalTime.of(7, 0));
        setting.setQuietEnabled(true);
        setting.setQuietStart(LocalTime.of(22, 0));
        setting.setQuietEnd(LocalTime.of(7, 0));
        return setting;
    }
}
