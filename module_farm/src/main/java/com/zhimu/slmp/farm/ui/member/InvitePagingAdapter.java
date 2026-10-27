package com.zhimu.slmp.farm.ui.member;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.model.vo.PenBriefVO;
import com.zhimu.slmp.farm.databinding.FarmItemInviteBinding;
import com.zhimu.slmp.farm.model.vo.InviteItemVO;
import com.zhimu.slmp.farm.ui.FarmUi;

import java.util.ArrayList;
import java.util.List;

/**
 * F07 邀请分组：待接受 person_add、已过期 schedule、已拒绝 person_off；第三行“10月17日邀请 · 10月24日前有效”
 * “10月3日邀请 · 7 天未处理”“10月18日拒绝”；所有者可“重新邀请”（can_reinvite）。邀请条目本身不可点。
 */
public class InvitePagingAdapter extends PagingDataAdapter<InviteItemVO, InvitePagingAdapter.Holder> {

    public interface OnReinviteListener {
        void onReinvite(@NonNull InviteItemVO item);
    }

    private static final DiffUtil.ItemCallback<InviteItemVO> DIFF = new DiffUtil.ItemCallback<InviteItemVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull InviteItemVO a, @NonNull InviteItemVO b) {
            return a.getInviteId() == b.getInviteId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull InviteItemVO a, @NonNull InviteItemVO b) {
            return a.equals(b);
        }
    };

    private final OnReinviteListener listener;
    private boolean canInvite;

    public InvitePagingAdapter(@NonNull OnReinviteListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setCanInvite(boolean canInvite) {
        if (this.canInvite != canInvite) {
            this.canInvite = canInvite;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(FarmItemInviteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        InviteItemVO item = getItem(position);
        if (item == null) {
            return;
        }
        FarmItemInviteBinding b = holder.binding;
        GroupPosition.apply(b.getRoot(), GroupPosition.of(position, getItemCount()));
        String status = item.getStatus();
        int icon = InviteItemVO.STATUS_EXPIRED.equals(status) ? com.zhimu.slmp.base.R.drawable.ic_schedule
                : InviteItemVO.STATUS_REJECTED.equals(status) ? com.zhimu.slmp.base.R.drawable.ic_person_off
                : com.zhimu.slmp.base.R.drawable.ic_person_add;
        b.inviteIcon.setImageResource(icon);
        b.invitePhone.setText(item.getInviteePhoneMasked());
        List<String> pens = new ArrayList<>();
        for (PenBriefVO p : item.getPens()) {
            if (p.getName() != null) {
                pens.add(p.getName());
            }
        }
        b.inviteLine2.setText(pens.isEmpty() ? item.getRoleText()
                : item.getRoleText() + " · 负责 " + FarmUi.joinNames(pens));
        b.inviteLine3.setText(line3(item));
        int type = InviteItemVO.STATUS_PENDING.equals(status) ? StatusTagView.PRIMARY
                : InviteItemVO.STATUS_EXPIRED.equals(status) ? StatusTagView.ERROR
                : InviteItemVO.STATUS_ACCEPTED.equals(status) ? StatusTagView.SUCCESS : StatusTagView.NEUTRAL;
        b.inviteStatus.setStatus(InviteItemVO.STATUS_ACCEPTED.equals(status) ? "邀请成功" : item.getStatusText(), type);
        boolean reinvite = canInvite && item.isCanReinvite() && !InviteItemVO.STATUS_ACCEPTED.equals(status);
        b.inviteReinvite.setVisibility(reinvite ? View.VISIBLE : View.GONE);
        b.inviteReinvite.setOnClickListener(reinvite ? v -> listener.onReinvite(item) : null);
    }

    @NonNull
    static String line3(@NonNull InviteItemVO item) {
        String status = item.getStatus();
        if (InviteItemVO.STATUS_ACCEPTED.equals(status)) {
            return item.getHandledAt() == null ? "已加入养殖场" : FarmUi.monthDay(item.getHandledAt()) + "已加入养殖场";
        }
        if (InviteItemVO.STATUS_REJECTED.equals(status)) {
            return FarmUi.monthDay(item.getHandledAt()) + "拒绝";
        }
        String invited = FarmUi.monthDay(item.getInvitedAt()) + "邀请";
        if (InviteItemVO.STATUS_EXPIRED.equals(status)) {
            return invited + " · 7 天未处理";
        }
        return invited + " · " + FarmUi.monthDay(item.getExpiresAt()) + "前有效";
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FarmItemInviteBinding binding;

        Holder(@NonNull FarmItemInviteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
