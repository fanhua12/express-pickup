package com.pickup.assistant.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.pickup.assistant.model.PickupItem;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** 本地 SQLite，数据只躺在手机上，不上传 */
public class PickupDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "pickups.db";
    private static final int DB_VERSION = 3;
    private static final String T = "pickups";

    public static final long RETENTION_MS = 24 * 60 * 60 * 1000L;

    private static volatile PickupDb instance;

    /** 数据一变就回调，界面靠它实时刷新 */
    public interface Listener {
        void onDataChanged();
    }

    private final List<Listener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        for (Listener l : listeners) l.onDataChanged();
    }

    public static PickupDb get(Context ctx) {
        if (instance == null) {
            synchronized (PickupDb.class) {
                if (instance == null) instance = new PickupDb(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    private PickupDb(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "code TEXT NOT NULL," +
                "carrier TEXT," +
                "station TEXT," +
                "source TEXT," +
                "source_app TEXT," +
                "source_pkg TEXT," +
                "raw TEXT," +
                "received_at INTEGER," +
                "status INTEGER DEFAULT 0," +
                "deleted_at INTEGER DEFAULT 0," +
                "hash TEXT UNIQUE)");
        db.execSQL("CREATE INDEX idx_status ON " + T + "(status, received_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // v2 加了回收仓，老数据不能丢，只加列
        if (oldV < 2) {
            db.execSQL("ALTER TABLE " + T + " ADD COLUMN deleted_at INTEGER DEFAULT 0");
        }
        // v3 记下来源包名，一键查询要用
        if (oldV < 3) {
            db.execSQL("ALTER TABLE " + T + " ADD COLUMN source_pkg TEXT");
        }
    }

    /** 这个窗口内同一个码算同一条，合并补全就行，别重复提醒 */
    private static final long MERGE_WINDOW_MS = 3 * 24 * 60 * 60 * 1000L;

    /** 没有就插，返回 true 说明是新记录、要提醒 */
    public boolean insertIfAbsent(PickupItem it) {
        PickupItem exist = findByCode(it.code);
        if (exist != null) {
            it.id = exist.id;
            // 同码之前在回收仓，现在又来通知，当新包裹捞回待取并提醒
            if (exist.status == PickupItem.STATUS_DELETED) {
                update(it, PickupItem.STATUS_PENDING);
                return true;
            }
            if (it.receivedAt - exist.receivedAt <= MERGE_WINDOW_MS) {
                merge(exist, it);
                return false;
            }
            update(it, PickupItem.STATUS_PENDING);
            return true;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("code", it.code);
        v.put("carrier", it.carrier);
        v.put("station", it.station);
        v.put("source", it.source);
        v.put("source_app", it.sourceApp);
        v.put("source_pkg", it.sourcePkg);
        v.put("raw", it.raw);
        v.put("received_at", it.receivedAt);
        v.put("status", PickupItem.STATUS_PENDING);
        v.put("hash", md5(it.code + "|" + it.carrier + "|" + it.station));
        long id = db.insertWithOnConflict(T, null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (id >= 0) {
            it.id = id;
            notifyChanged();
            return true;
        }
        return false;
    }

    public PickupItem findByCode(String code) {
        Cursor c = getReadableDatabase().query(T, null, "code=?",
                new String[]{code}, null, null, "received_at DESC", "1");
        try {
            return c.moveToFirst() ? fromCursor(c) : null;
        } finally {
            c.close();
        }
    }

    /** 重复通知就合并，缺的字段补上；原状态保留，已取的别退回待取 */
    private void merge(PickupItem exist, PickupItem it) {
        ContentValues v = new ContentValues();
        if (isEmpty(exist.carrier) && !isEmpty(it.carrier)) v.put("carrier", it.carrier);
        if (isEmpty(exist.station) && !isEmpty(it.station)) v.put("station", it.station);
        if (it.receivedAt > exist.receivedAt) {
            v.put("received_at", it.receivedAt);
            v.put("raw", it.raw);
        }
        if (v.size() == 0) return;
        getWritableDatabase().update(T, v, "id=?", new String[]{String.valueOf(exist.id)});
    }

    /** 同码当新包裹，整行覆盖掉 */
    private void update(PickupItem it, int status) {
        ContentValues v = new ContentValues();
        v.put("carrier", it.carrier);
        v.put("station", it.station);
        v.put("source", it.source);
        v.put("source_app", it.sourceApp);
        v.put("source_pkg", it.sourcePkg);
        v.put("raw", it.raw);
        v.put("received_at", it.receivedAt);
        v.put("status", status);
        v.put("deleted_at", 0);
        getWritableDatabase().update(T, v, "id=?", new String[]{String.valueOf(it.id)});
        notifyChanged();
    }

    /**
     * 到件待查，通知里没码那种。同一个包名+快递+驿站+自然日只提醒一次
     * 返回 true 就是新记录，得发高优提醒
     */
    public boolean insertArrival(PickupItem it) {
        long day = it.receivedAt / (24 * 60 * 60 * 1000L);
        ContentValues v = new ContentValues();
        v.put("code", "");
        v.put("carrier", it.carrier);
        v.put("station", it.station);
        v.put("source", it.source);
        v.put("source_app", it.sourceApp);
        v.put("source_pkg", it.sourcePkg);
        v.put("raw", it.raw);
        v.put("received_at", it.receivedAt);
        v.put("status", PickupItem.STATUS_ARRIVAL);
        v.put("hash", md5("ARR|" + safe(it.sourcePkg) + "|" + safe(it.carrier)
                + "|" + safe(it.station) + "|" + day));
        long id = getWritableDatabase().insertWithOnConflict(T, null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (id >= 0) {
            it.id = id;
            notifyChanged();
            return true;
        }
        return false;
    }

    /** 真码抓到了，就把同快递/同驿站近 3 天的"到件待查"清掉 */
    public int removeMatchedArrivals(String carrier, String station) {
        long since = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L;
        String where = "status=" + PickupItem.STATUS_ARRIVAL + " AND received_at>=?";
        String[] args;
        if (!isEmpty(station)) {
            where += " AND station=?";
            args = new String[]{String.valueOf(since), station};
        } else if (!isEmpty(carrier)) {
            where += " AND carrier=?";
            args = new String[]{String.valueOf(since), carrier};
        } else {
            return 0;
        }
        int n = getWritableDatabase().delete(T, where, args);
        if (n > 0) notifyChanged();
        return n;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /** 待取页里既有识别出的待取(0)，也有到件待查(3) */
    public List<PickupItem> listPending() {
        List<PickupItem> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query(T, null, "status IN (0,3)",
                null, null, null, "received_at DESC");
        try {
            while (c.moveToNext()) out.add(fromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    public List<PickupItem> list(int status) {
        List<PickupItem> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T, null, "status=?", new String[]{String.valueOf(status)},
                null, null, "received_at DESC");
        try {
            while (c.moveToNext()) out.add(fromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    public void setStatus(long id, int status) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        getWritableDatabase().update(T, v, "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    /** 软删除，先丢进回收仓，满 1 天 purgeExpired 再彻底删 */
    public void softDelete(long id) {
        ContentValues v = new ContentValues();
        v.put("status", PickupItem.STATUS_DELETED);
        v.put("deleted_at", System.currentTimeMillis());
        getWritableDatabase().update(T, v, "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    public void restore(long id) {
        ContentValues v = new ContentValues();
        v.put("status", PickupItem.STATUS_DONE);
        v.put("deleted_at", 0);
        getWritableDatabase().update(T, v, "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    public List<PickupItem> listDeleted() {
        List<PickupItem> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query(T, null, "status=?",
                new String[]{String.valueOf(PickupItem.STATUS_DELETED)},
                null, null, "deleted_at DESC");
        try {
            while (c.moveToNext()) out.add(fromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    public int purgeExpired() {
        int n = getWritableDatabase().delete(T,
                "status=? AND deleted_at>0 AND deleted_at<?",
                new String[]{String.valueOf(PickupItem.STATUS_DELETED),
                        String.valueOf(System.currentTimeMillis() - RETENTION_MS)});
        if (n > 0) notifyChanged();
        return n;
    }

    public int pendingCount() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + T + " WHERE status IN (0,3)", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private PickupItem fromCursor(Cursor c) {
        PickupItem it = new PickupItem();
        it.id = c.getLong(c.getColumnIndexOrThrow("id"));
        it.code = c.getString(c.getColumnIndexOrThrow("code"));
        it.carrier = c.getString(c.getColumnIndexOrThrow("carrier"));
        it.station = c.getString(c.getColumnIndexOrThrow("station"));
        it.source = c.getString(c.getColumnIndexOrThrow("source"));
        it.sourceApp = c.getString(c.getColumnIndexOrThrow("source_app"));
        it.sourcePkg = c.getString(c.getColumnIndexOrThrow("source_pkg"));
        it.raw = c.getString(c.getColumnIndexOrThrow("raw"));
        it.receivedAt = c.getLong(c.getColumnIndexOrThrow("received_at"));
        it.deletedAt = c.getLong(c.getColumnIndexOrThrow("deleted_at"));
        it.status = c.getInt(c.getColumnIndexOrThrow("status"));
        it.hash = c.getString(c.getColumnIndexOrThrow("hash"));
        return it;
    }

    public static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
