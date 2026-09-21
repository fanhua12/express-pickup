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

/** 本地 SQLite: 数据只存手机, 不上传 */
public class PickupDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "pickups.db";
    private static final int DB_VERSION = 1;
    private static final String T = "pickups";

    private static volatile PickupDb instance;

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
                "raw TEXT," +
                "received_at INTEGER," +
                "status INTEGER DEFAULT 0," +
                "hash TEXT UNIQUE)");
        db.execSQL("CREATE INDEX idx_status ON " + T + "(status, received_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + T);
        onCreate(db);
    }

    /** 不存在则插入; 返回 true 表示新记录(用于触发通知) */
    public boolean insertIfAbsent(PickupItem it) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("code", it.code);
        v.put("carrier", it.carrier);
        v.put("station", it.station);
        v.put("source", it.source);
        v.put("source_app", it.sourceApp);
        v.put("raw", it.raw);
        v.put("received_at", it.receivedAt);
        v.put("status", PickupItem.STATUS_PENDING);
        v.put("hash", md5(it.code + "|" + it.carrier + "|" + it.station));
        long id = db.insertWithOnConflict(T, null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (id >= 0) it.id = id;
        return id >= 0;
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
    }

    public void delete(long id) {
        getWritableDatabase().delete(T, "id=?", new String[]{String.valueOf(id)});
    }

    public int pendingCount() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + T + " WHERE status=0", null);
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
        it.raw = c.getString(c.getColumnIndexOrThrow("raw"));
        it.receivedAt = c.getLong(c.getColumnIndexOrThrow("received_at"));
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
