package com.pickup.assistant.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.pickup.assistant.R;
import com.pickup.assistant.model.PickupItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PickupAdapter extends RecyclerView.Adapter<PickupAdapter.VH> {

    public interface OnAction {
        void onToggle(PickupItem item);
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
        h.source.setText("来自: " + (TextUtils.isEmpty(it.sourceApp) ? it.source : it.sourceApp));
        h.toggle.setText(it.status == PickupItem.STATUS_DONE ? "撤销已取" : "标记已取");

        View.OnClickListener copy = v -> copyCode(ctx, it.code);
        h.code.setOnClickListener(copy);
        h.copy.setOnClickListener(copy);
        h.toggle.setOnClickListener(v -> action.onToggle(it));
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
        TextView carrier, time, code, source;
        MaterialButton copy, toggle;

        VH(@NonNull View v) {
            super(v);
            carrier = v.findViewById(R.id.txt_carrier);
            time = v.findViewById(R.id.txt_time);
            code = v.findViewById(R.id.txt_code);
            source = v.findViewById(R.id.txt_source);
            copy = v.findViewById(R.id.btn_copy);
            toggle = v.findViewById(R.id.btn_toggle);
        }
    }
}
