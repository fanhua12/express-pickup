package com.pickup.assistant.service;

import android.content.Context;

import com.pickup.assistant.db.PickupDb;
import com.pickup.assistant.model.PickupItem;
import com.pickup.assistant.notify.LocalNotifier;
import com.pickup.assistant.parser.ParseResult;
import com.pickup.assistant.parser.PickupParser;

import java.util.List;

/** 短信、通知、截图都走这里：能解析出码就入库，没码但看着像到件就建条"到件待查" */
public final class Ingestor {

    public static final int NONE = 0;          // 既没取件码也不像到件
    public static final int NEW_CODE = 1;      // 识别到新取件码, 已入库+提醒
    public static final int DUP_CODE = 2;      // 取件码已存在
    public static final int NEW_ARRIVAL = 3;   // 到件待查(新)
    public static final int DUP_ARRIVAL = 4;   // 到件待查(重复)

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
                // 真码到手了，顺手把之前那条"到件待查"撤掉
                PickupDb.get(ctx).removeMatchedArrivals(r.carrier, r.station);
                return NEW_CODE;
            }
            return DUP_CODE;
        }

        // 没码但明确是"到了"，先建条待查，再高优提醒去一键查询
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

    /** 截图里可能一堆码，批量过一遍，返回新增和重复的条数 */
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

        // 一个码都没找到，不过像是到件通知，那就建条待查
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
