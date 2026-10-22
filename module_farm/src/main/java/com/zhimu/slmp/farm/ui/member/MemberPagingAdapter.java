package com.zhimu.slmp.farm.ui.member;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.model.vo.PenBriefVO;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.databinding.FarmItemMemberBinding;
import com.zhimu.slmp.farm.model.vo.MemberItemVO;
import com.zhimu.slmp.farm.ui.FarmUi;

import java.util.ArrayList;
import java.util.List;

/**
 * F07 成员分组：姓名（本人加“（你）”）、“手机号 + 两个空格 + 加入日期创建 / 加入”、负责范围（全部栏舍或“负责 3 号育肥舍”）、角色标签。
 */
public class MemberPagingAdapter extends PagingDataAdapter<MemberItemVO, MemberPagingAdapter.Holder> {

    public interface OnMemberClickListener {
        void onMemberClick(@NonNull MemberItemVO item);
    }

    private static final DiffUtil.ItemCallback<MemberItemVO> DIFF = new DiffUtil.ItemCallback<MemberItemVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull MemberItemVO a, @NonNull MemberItemVO b) {
            return a.getMemberId() == b.getMemberId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull MemberItemVO a, @NonNull MemberItemVO b) {
            return a.equals(b);
        }
    };

    private final OnMemberClickListener listener;

    public MemberPagingAdapter(@NonNull OnMemberClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(FarmItemMemberBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MemberItemVO item = getItem(position);
        if (item == null) {
            return;
        }
        FarmItemMemberBinding b = holder.binding;
        android.content.Context ctx = b.getRoot().getContext();
        GroupPosition.apply(b.getRoot(), GroupPosition.of(position, getItemCount()));
        b.memberAvatar.setName(item.getName());
        b.memberName.setText(item.isMe() ? ctx.getString(R.string.farm_members_me, item.getName()) : item.getName());
        String joined = FarmUi.monthDay(item.getJoinedAt()) + ("CREATE".equals(item.getJoinType()) ? "创建" : "加入");
        b.memberLine2.setText((item.getPhoneMasked() == null ? "" : item.getPhoneMasked() + "  ") + joined);
        b.memberLine3.setText(scopeText(ctx, item));
        b.memberRole.setStatus(item.getRoleText(), FarmContextVO.ROLE_OWNER.equals(item.getRole())
                ? StatusTagView.PRIMARY : StatusTagView.NEUTRAL);
        b.getRoot().setOnClickListener(v -> listener.onMemberClick(item));
    }

    @NonNull
    static String scopeText(@NonNull android.content.Context ctx, @NonNull MemberItemVO item) {
        if (item.isAllPens() || !FarmContextVO.ROLE_STAFF.equals(item.getRole())) {
            return ctx.getString(R.string.farm_members_all_pens);
        }
        List<String> names = new ArrayList<>();
        for (PenBriefVO p : item.getPens()) {
            if (p.getName() != null) {
                names.add(p.getName());
            }
        }
        return names.isEmpty() ? ctx.getString(R.string.farm_members_no_pens)
                : ctx.getString(R.string.farm_members_pens, FarmUi.joinNames(names));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FarmItemMemberBinding binding;

        Holder(@NonNull FarmItemMemberBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
