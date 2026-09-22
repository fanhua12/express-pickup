package com.pickup.assistant.service;

import android.content.Context;

import com.pickup.assistant.db.PickupDb;
import com.pickup.assistant.model.PickupItem;
import com.pickup.assistant.notify.LocalNotifier;
import com.pickup.assistant.parser.ParseResult;
import com.pickup.assistant.parser.PickupParser;

/** 统一处理短信/通知文本: 解析 -> 去重入库 -> 新取件码发本地通知 */
public final class Ingestor {

    private Ingestor() {}

    public static void handle(Context ctx, String text, String source, String sourceApp) {
        if (text == null || text.isEmpty()) return;
        ParseResult r = PickupParser.parse(ctx, text);
        if (!r.matched) return;

        PickupItem it = new PickupItem();
        it.code = r.code;
        it.carrier = r.carrier;
        it.station = r.station;
        it.source = source;
        it.sourceApp = sourceApp;
        it.raw = text.length() > 500 ? text.substring(0, 500) : text;
        it.receivedAt = System.currentTimeMillis();
        it.status = PickupItem.STATUS_PENDING;

        boolean isNew = PickupDb.get(ctx).insertIfAbsent(it);
        if (isNew) LocalNotifier.notifyNew(ctx, it);
    }
}
