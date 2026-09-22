package com.pickup.assistant.model;

/** 一条取件记录 */
public class PickupItem {
    public long id;
    public String code;
    public String carrier;
    public String station;
    public String source;      // sms / notification
    public String sourceApp;   // 来源 App 名或包名/短信发送方
    public String raw;         // 原始文本
    public long receivedAt;    // 收到时间(毫秒)
    public long deletedAt;     // 移入回收仓时间(毫秒, 0=不在回收仓)
    public int status;         // 0 待取, 1 已取, 2 回收仓
    public String hash;        // 去重哈希

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DONE = 1;
    public static final int STATUS_DELETED = 2;
}
