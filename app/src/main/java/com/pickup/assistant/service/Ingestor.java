package com.pickup.assistant.service;

import android.content.Context;

import com.pickup.assistant.db.PickupDb;
import com.pickup.assistant.model.PickupItem;
import com.pickup.assistant.notify.LocalNotifier;
import com.pickup.assistant.parser.ParseResult;
import com.pickup.assistant.parser.PickupParser;

import java.util.List;

/** 统一处理短信/通知/截图文本: 解析取件码; 无码但到件则建"到件待查" */
public final class Ingestor {

    /** 处理结果, 供界面给用户反馈 */
    public static final int NONE = 0;          // 既没取件码也不像到件
    public static final int NEW_CODE = 1;      // 识别到新取件码, 已入库+提醒
    public static final int DUP_CODE = 2;      // 取件码已存在
    public static final int NEW_ARRIVAL = 3;   // 到件待查(新)
    public static final int DUP_ARRIVAL = 4;   // 到件待查(重复)

    /** 批量处理结果 */
    public static class BatchResult {
        public int newCount;
        public int dupCount;
        public List<String> newCodes;
    }

    private Ingestor() {}

    public static int handle(Context ctx, String text, String source, String sourceApp) {
        return handle(ctx, text, source, sourceApp, null);
    }

    public static int handle(Context ctx, String text, String source, String sourceApp, String sourcePkg) {
        if (text == null || text.isEmpty()) return NONE;
        ParseResult r = PickupParser.parse(ctx, text);
        long now = System.currentTimeMillis();
        String raw = text.length() > 500 ? text.substring(0, 500) : text;

        if (r.matched) {
            PickupItem it = new PickupItem();
            it.code = r.code;
            it.carrier = r.carrier;
            it.station = r.station;
            it.source = source;
            it.sourceApp = sourceApp;
            it.sourcePkg = sourcePkg;
            it.raw = raw;
            it.receivedAt = now;
            it.status = PickupItem.STATUS_PENDING;

            boolean isNew = PickupDb.get(ctx).insertIfAbsent(it);
            if (isNew) {
                LocalNotifier.notifyNew(ctx, it);
                // 真实取件码到手, 撤掉对应的"到件待查"
                PickupDb.get(ctx).removeMatchedArrivals(r.carrier, r.station);
                return NEW_CODE;
            }
            return DUP_CODE;
        }

        // 无取件码但明确是"快递到了": 建待查记录并高优提醒一键查询
        if (PickupParser.looksLikeArrival(text)) {
            PickupItem it = new PickupItem();
            it.code = "";
            it.carrier = r.carrier;
            it.station = r.station;
            it.source = source;
            it.sourceApp = sourceApp;
            it.sourcePkg = sourcePkg;
            it.raw = raw;
            it.receivedAt = now;
            it.status = PickupItem.STATUS_ARRIVAL;

            if (PickupDb.get(ctx).insertArrival(it)) {
                LocalNotifier.notifyArrival(ctx, it);
                return NEW_ARRIVAL;
            }
            return DUP_ARRIVAL;
        }
        return NONE;
    }

    /** 批量处理(截图多码): 返回新/重复计数 */
    public static BatchResult handleBatch(Context ctx, String text, String source, String sourceApp) {
        BatchResult br = new BatchResult();
        br.newCodes = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return br;

        List<ParseResult> results = PickupParser.parseAll(ctx, text);
        long now = System.currentTimeMillis();
        String raw = text.length() > 500 ? text.substring(0, 500) : text;

        for (ParseResult r : results) {
            PickupItem it = new PickupItem();
            it.code = r.code;
            it.carrier = r.carrier;
            it.station = r.station;
            it.source = source;
            it.sourceApp = sourceApp;
            it.raw = raw;
            it.receivedAt = now;
            it.status = PickupItem.STATUS_PENDING;
            if (PickupDb.get(ctx).insertIfAbsent(it)) {
                LocalNotifier.notifyNew(ctx, it);
                br.newCount++;
                br.newCodes.add(r.code);
            } else {
                br.dupCount++;
            }
        }

        // 没找到任何码, 但像到件通知 -> 建到件待查
        if (results.isEmpty() && PickupParser.looksLikeArrival(text)) {
            ParseResult r = PickupParser.parse(ctx, text);
            PickupItem it = new PickupItem();
            it.code = "";
            it.carrier = r.carrier;
            it.station = r.station;
            it.source = source;
            it.sourceApp = sourceApp;
            it.raw = raw;
            it.receivedAt = now;
            it.status = PickupItem.STATUS_ARRIVAL;
            if (PickupDb.get(ctx).insertArrival(it)) {
                LocalNotifier.notifyArrival(ctx, it);
                br.newCount = 1; // 算作找到1个到件
            }
        }
        return br;
    }
}
