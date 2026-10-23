package com.zhimu.slmp.today.ui.calendar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.model.vo.CalendarDayVO;
import com.zhimu.slmp.today.util.CalendarRange;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.zhimu.slmp.base.util.DateFormatter;

/**
 * 月历网格（技术文档 2.7.4）：月视图画含本月的完整周（5 或 6 行，行高 48 dp），单周模式只画选中日期所在的一周（兼作周日期条）。
 * <ul>
 * <li>先画今天的 32 dp primary 圆底或选中日的 secondary-container 圆底，再画 14 sp 日期（非本月为 outline 色），
 * 日期下方约 10 dp 按红（有已逾期）、蓝（有待处理）、灰（已完成或已忽略）顺序画 6 dp 圆点，间距 2 dp；加载中只画数字；</li>
 * <li>单击按坐标换算日期后回调；横向位移大于纵向时请求父布局不拦截，超过半列宽或速度阈值时翻页，否则回弹（300 毫秒）；</li>
 * <li>ExploreByTouchHelper 为每格生成虚拟节点，朗读“10月19日，今天，已逾期 1 项”；格子坐标预先计算，绘制中不创建对象。</li>
 * </ul>
 * 数据由 BindingAdapter 从 LiveData 绑定，View 不持有 ViewModel。
 */
public class MonthCalendarView extends View {

    /** 点击与翻页回调 */
    public interface Listener {
        void onDateClick(@NonNull LocalDate date);

        /** direction：-1 上一页，1 下一页 */
        void onPageChange(int direction);
    }

    private static final int MAX_CELLS = 42;

    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float rowHeight;
    private final float circleRadius;
    private final float dotRadius;
    private final float dotGap;
    private final float dotOffset;
    private final int colorOnSurface;
    private final int colorOutline;
    private final int colorPrimary;
    private final int colorOnPrimary;
    private final int colorSecondaryContainer;
    private final int colorOnSecondaryContainer;
    private final int colorError;
    private final String[] dayTexts = new String[32];

    /** 预先计算的格子：日期与中心点 */
    private final LocalDate[] cellDates = new LocalDate[MAX_CELLS];
    private final float[] cellCx = new float[MAX_CELLS];
    private final float[] cellCy = new float[MAX_CELLS];
    private final boolean[] cellInMonth = new boolean[MAX_CELLS];
    private int cellCount;

    @NonNull
    private YearMonth month = YearMonth.from(DateFormatter.today());
    @NonNull
    private LocalDate selected = DateFormatter.today();
    @Nullable
    private LocalDate today;
    @NonNull
    private Map<LocalDate, CalendarDayVO> days = Collections.emptyMap();
    private boolean weekMode;
    private boolean loading = true;
    @Nullable
    private Listener listener;

    private final GestureDetector gestures;
    private final int touchSlop;
    private float downX;
    private float downY;
    private boolean dragging;
    private float offsetX;
    @Nullable
    private ValueAnimator animator;
    private final CellHelper a11y;

    public MonthCalendarView(@NonNull Context context) {
        this(context, null);
    }

    public MonthCalendarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        rowHeight = dp(48);
        circleRadius = dp(16);
        dotRadius = dp(3);
        dotGap = dp(2);
        dotOffset = dp(17);
        colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        colorOutline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline);
        colorPrimary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        colorOnPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary);
        colorSecondaryContainer = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSecondaryContainer);
        colorOnSecondaryContainer = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSecondaryContainer);
        colorError = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError);
        numberPaint.setTextAlign(Paint.Align.CENTER);
        numberPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14,
                getResources().getDisplayMetrics()));
        for (int i = 1; i <= 31; i++) {
            dayTexts[i] = String.valueOf(i);
        }
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                LocalDate d = dateAt(e.getX(), e.getY());
                if (d != null && listener != null) {
                    playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    listener.onDateClick(d);
                }
                return true;
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float vx, float vy) {
                if (dragging && Math.abs(vx) > Math.abs(vy) && Math.abs(vx) > dp(600)) {
                    settle(vx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
        a11y = new CellHelper(this);
        ViewCompat.setAccessibilityDelegate(this, a11y);
        layoutCells();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // ===================== 输入 =====================

    public void setListener(@Nullable Listener l) {
        listener = l;
    }

    public void setMonth(@NonNull YearMonth ym) {
        if (!ym.equals(month)) {
            month = ym;
            relayout();
        }
    }

    public void setDays(@Nullable Map<LocalDate, CalendarDayVO> map) {
        days = map == null ? Collections.emptyMap() : map;
        invalidateCells();
    }

    public void setSelectedDate(@NonNull LocalDate date) {
        boolean weekChanged = weekMode && !CalendarRange.weekStart(date).equals(CalendarRange.weekStart(selected));
        selected = date;
        if (weekChanged) {
            relayout();
        } else {
            invalidateCells();
        }
    }

    public void setToday(@Nullable LocalDate date) {
        today = date;
        invalidateCells();
    }

    /** 单周模式（周、日视图的周日期条） */
    public void setWeekMode(boolean week) {
        if (weekMode != week) {
            weekMode = week;
            relayout();
        }
    }

    /** 加载中只画数字 */
    public void setLoading(boolean value) {
        if (loading != value) {
            loading = value;
            invalidate();
        }
    }

    private void relayout() {
        layoutCells();
        requestLayout();
        invalidateCells();
    }

    private void invalidateCells() {
        a11y.invalidateRoot();
        invalidate();
    }

    // ===================== 测量与格子 =====================

    private int rows() {
        return weekMode ? 1 : CalendarRange.rowsOf(month);
    }

    private void layoutCells() {
        CalendarRange range = weekMode ? CalendarRange.week(selected) : CalendarRange.month(month);
        LocalDate d = range.getStart();
        cellCount = (int) Math.min(MAX_CELLS, range.days());
        for (int i = 0; i < cellCount; i++) {
            cellDates[i] = d;
            cellInMonth[i] = weekMode || YearMonth.from(d).equals(month);
            d = d.plusDays(1);
        }
        computeCenters();
    }

    private void computeCenters() {
        float col = getWidth() / 7f;
        for (int i = 0; i < cellCount; i++) {
            cellCx[i] = col * (i % 7) + col / 2f;
            cellCy[i] = rowHeight * (i / 7) + rowHeight / 2f - dp(4);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (rows() * rowHeight);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeCenters();
    }

    @Nullable
    private LocalDate dateAt(float x, float y) {
        if (getWidth() == 0) {
            return null;
        }
        int col = (int) (x / (getWidth() / 7f));
        int row = (int) (y / rowHeight);
        int index = row * 7 + Math.max(0, Math.min(6, col));
        return index >= 0 && index < cellCount ? cellDates[index] : null;
    }

    private int indexAt(float x, float y) {
        LocalDate d = dateAt(x, y);
        if (d == null) {
            return ExploreByTouchHelper.INVALID_ID;
        }
        for (int i = 0; i < cellCount; i++) {
            if (cellDates[i].equals(d)) {
                return i;
            }
        }
        return ExploreByTouchHelper.INVALID_ID;
    }

    // ===================== 绘制 =====================

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (offsetX != 0f) {
            canvas.save();
            canvas.translate(offsetX, 0);
            numberPaint.setAlpha((int) (255 * Math.max(0.2f, 1f - Math.abs(offsetX) / Math.max(1, getWidth()))));
        } else {
            numberPaint.setAlpha(255);
        }
        Paint.FontMetrics fm = numberPaint.getFontMetrics();
        float textOffset = -(fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < cellCount; i++) {
            LocalDate d = cellDates[i];
            float cx = cellCx[i];
            float cy = cellCy[i];
            boolean isToday = d.equals(today);
            boolean isSelected = d.equals(selected);
            int textColor = cellInMonth[i] ? colorOnSurface : colorOutline;
            if (isToday) {
                circlePaint.setColor(colorPrimary);
                canvas.drawCircle(cx, cy, circleRadius, circlePaint);
                textColor = colorOnPrimary;
            } else if (isSelected) {
                circlePaint.setColor(colorSecondaryContainer);
                canvas.drawCircle(cx, cy, circleRadius, circlePaint);
                textColor = colorOnSecondaryContainer;
            }
            numberPaint.setColor(textColor);
            canvas.drawText(dayTexts[d.getDayOfMonth()], cx, cy + textOffset, numberPaint);
            if (!loading) {
                drawDots(canvas, days.get(d), cx, cy + dotOffset);
            }
        }
        if (offsetX != 0f) {
            canvas.restore();
        }
    }

    private void drawDots(@NonNull Canvas canvas, @Nullable CalendarDayVO day, float cx, float cy) {
        if (day == null) {
            return;
        }
        boolean red = day.getOverdueCount() > 0;
        boolean blue = day.getPendingCount() > 0;
        boolean grey = day.getClosedCount() > 0;
        int n = (red ? 1 : 0) + (blue ? 1 : 0) + (grey ? 1 : 0);
        if (n == 0) {
            return;
        }
        float total = n * dotRadius * 2 + (n - 1) * dotGap;
        float x = cx - total / 2f + dotRadius;
        if (red) {
            dotPaint.setColor(colorError);
            canvas.drawCircle(x, cy, dotRadius, dotPaint);
            x += dotRadius * 2 + dotGap;
        }
        if (blue) {
            dotPaint.setColor(colorPrimary);
            canvas.drawCircle(x, cy, dotRadius, dotPaint);
            x += dotRadius * 2 + dotGap;
        }
        if (grey) {
            dotPaint.setColor(colorOutline);
            canvas.drawCircle(x, cy, dotRadius, dotPaint);
        }
    }

    // ===================== 触摸 =====================

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (animator != null && animator.isRunning()) {
            return true;
        }
        boolean handled = gestures.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!dragging && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    offsetX = dx;
                    invalidate();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging && (animator == null || !animator.isRunning())) {
                    float half = getWidth() / 14f;
                    if (Math.abs(offsetX) > half && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        settle(offsetX < 0 ? 1 : -1);
                    } else {
                        animateOffset(0f, null);
                    }
                }
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            default:
                break;
        }
        return handled || dragging;
    }

    /** 翻页：内容滑出后回调，新数据到达前从另一侧回到原位 */
    private void settle(int direction) {
        float target = direction > 0 ? -getWidth() : getWidth();
        animateOffset(target, () -> {
            offsetX = 0f;
            invalidate();
            if (listener != null) {
                listener.onPageChange(direction);
            }
        });
    }

    private void animateOffset(float target, @Nullable Runnable end) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(offsetX, target);
        animator.setDuration(300);
        animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        animator.addUpdateListener(a -> {
            offsetX = (float) a.getAnimatedValue();
            invalidate();
        });
        if (end != null) {
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    end.run();
                }
            });
        }
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        offsetX = 0f;
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        return a11y.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    // ===================== 无障碍 =====================

    /** “10月19日，今天，已逾期 1 项” */
    @NonNull
    CharSequence describe(int index) {
        LocalDate d = cellDates[index];
        StringBuilder sb = new StringBuilder();
        sb.append(d.getMonthValue()).append("月").append(d.getDayOfMonth()).append("日");
        if (d.equals(today)) {
            sb.append(getContext().getString(R.string.today_calendar_cd_today));
        }
        CalendarDayVO day = days.get(d);
        if (day != null) {
            if (day.getOverdueCount() > 0) {
                sb.append(getContext().getString(R.string.today_calendar_cd_overdue, day.getOverdueCount()));
            }
            if (day.getPendingCount() > 0) {
                sb.append(getContext().getString(R.string.today_calendar_cd_pending, day.getPendingCount()));
            }
            if (day.getClosedCount() > 0) {
                sb.append(getContext().getString(R.string.today_calendar_cd_closed, day.getClosedCount()));
            }
        }
        return sb;
    }

    private final class CellHelper extends ExploreByTouchHelper {

        private final Rect bounds = new Rect();

        CellHelper(@NonNull View host) {
            super(host);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            return indexAt(x, y);
        }

        @Override
        protected void getVisibleVirtualViews(@NonNull List<Integer> ids) {
            for (int i = 0; i < cellCount; i++) {
                ids.add(i);
            }
        }

        @Override
        protected void onPopulateNodeForVirtualView(int id, @NonNull AccessibilityNodeInfoCompat node) {
            if (id < 0 || id >= cellCount) {
                node.setContentDescription("");
                node.setBoundsInParent(new Rect());
                return;
            }
            float col = getWidth() / 7f;
            int left = (int) (col * (id % 7));
            int top = (int) (rowHeight * (id / 7));
            bounds.set(left, top, (int) (left + col), (int) (top + rowHeight));
            node.setBoundsInParent(bounds);
            node.setContentDescription(describe(id));
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            node.setSelected(cellDates[id].equals(selected));
        }

        @Override
        protected boolean onPerformActionForVirtualView(int id, int action, @Nullable Bundle arguments) {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK && id >= 0 && id < cellCount && listener != null) {
                listener.onDateClick(cellDates[id]);
                return true;
            }
            return false;
        }
    }
}
