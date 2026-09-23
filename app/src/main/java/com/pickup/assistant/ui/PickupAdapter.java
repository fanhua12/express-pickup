package com.pickup.assistant.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.pickup.assistant.R;
import com.pickup.assistant.db.PickupDb;
import com.pickup.assistant.model.PickupItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PickupAdapter extends RecyclerView.Adapter<PickupAdapter.VH> {

    public interface OnAction {
        void onToggle(PickupItem item);
        void onDelete(PickupItem item);
        void onQuery(PickupItem item);
    }

    private final List<PickupItem> data = new ArrayList<>();
    private final OnAction action;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public PickupAdapter(OnAction action) {
        this.action = action;
    }

    public void submit(List<PickupItem> list) {
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pickup, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PickupItem it = data.get(position);
        Context ctx = h.itemView.getContext();

        String where = TextUtils.isEmpty(it.station) ? it.carrier : it.station;
        h.carrier.setText(TextUtils.isEmpty(where) ? "快递" : where);
        h.time.setText(sdf.format(new Date(it.receivedAt)));
        h.code.setText(it.code);

        boolean arrival = it.status == PickupItem.STATUS_ARRIVAL;
        // 层级推进: 待取最亮(蓝) -> 已取转灰 -> 回收仓整体变暗
        boolean deleted = it.status == PickupItem.STATUS_DELETED;
        boolean done = it.status == PickupItem.STATUS_DONE;
        h.itemView.setBackgroundResource(deleted ? R.drawable.bg_card_recycle
                : done ? R.drawable.bg_card_done : R.drawable.bg_card);

        if (arrival) {
            // 到件待查: 隐藏取件码盒, 显示一键查询
            h.boxCode.setVisibility(View.GONE);
            h.copy.setVisibility(View.GONE);
            h.boxArrival.setVisibility(View.VISIBLE);
            h.carrier.setTextColor(ContextCompat.getColor(ctx, R.color.badge_orange));
        } else {
            h.boxCode.setVisibility(View.VISIBLE);
            h.copy.setVisibility(View.VISIBLE);
            h.boxArrival.setVisibility(View.GONE);
            h.boxCode.setBackgroundResource(deleted ? R.drawable.bg_code_recycle
                    : done ? R.drawable.bg_code_done : R.drawable.bg_code);
            h.code.setTextColor(ContextCompat.getColor(ctx, deleted ? R.color.code_text_recycle
                    : done ? R.color.code_text_done : R.color.code_text));
            h.codeHint.setTextColor(ContextCompat.getColor(ctx, deleted ? R.color.code_text_recycle
                    : done ? R.color.code_text_done : R.color.primary));
            h.copy.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, deleted ? R.color.text_hint
                            : done ? R.color.text_sub : R.color.primary)));
            h.carrier.setTextColor(ContextCompat.getColor(ctx, deleted
                    ? R.color.text_sub : R.color.text_main));
        }

        String src = "来自: " + (TextUtils.isEmpty(it.sourceApp) ? it.source : it.sourceApp);
        if (it.status == PickupItem.STATUS_DELETED) {
            long left = it.deletedAt + PickupDb.RETENTION_MS - System.currentTimeMillis();
            long hours = left <= 0 ? 0 : (left + 3599999) / 3600000;
            src += hours > 0 ? " · 约" + hours + "小时后彻底删除" : " · 即将彻底删除";
            h.toggle.setText("恢复");
            h.toggle.setVisibility(View.VISIBLE);
            h.del.setVisibility(View.GONE);
        } else if (arrival) {
            h.toggle.setVisibility(View.GONE);
            h.del.setVisibility(View.VISIBLE);
            h.del.setText("忽略");
        } else {
            h.toggle.setText(it.status == PickupItem.STATUS_DONE ? "撤销已取" : "标记已取");
            h.toggle.setVisibility(View.VISIBLE);
            h.del.setVisibility(it.status == PickupItem.STATUS_DONE ? View.VISIBLE : View.GONE);
            h.del.setText("删除");
        }
        h.source.setText(src);

        View.OnClickListener copy = v -> copyCode(ctx, it.code);
        h.code.setOnClickListener(copy);
        h.boxCode.setOnClickListener(copy);
        h.copy.setOnClickListener(copy);
        h.query.setOnClickListener(v -> action.onQuery(it));
        h.toggle.setOnClickListener(v -> action.onToggle(it));
        h.del.setOnClickListener(v -> action.onDelete(it));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static void copyCode(Context ctx, String code) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("pickup_code", code));
            Toast.makeText(ctx, "取件码已复制: " + code, Toast.LENGTH_SHORT).show();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        View boxCode, boxArrival;
        TextView carrier, time, code, codeHint, source;
        MaterialButton copy, query, toggle, del;

        VH(@NonNull View v) {
            super(v);
            boxCode = v.findViewById(R.id.box_code);
            boxArrival = v.findViewById(R.id.box_arrival);
            carrier = v.findViewById(R.id.txt_carrier);
            time = v.findViewById(R.id.txt_time);
            code = v.findViewById(R.id.txt_code);
            codeHint = v.findViewById(R.id.txt_code_hint);
            source = v.findViewById(R.id.txt_source);
            copy = v.findViewById(R.id.btn_copy);
            query = v.findViewById(R.id.btn_query);
            toggle = v.findViewById(R.id.btn_toggle);
            del = v.findViewById(R.id.btn_del);
        }
    }
}
