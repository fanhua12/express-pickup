package com.pickup.assistant.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/** 监听收到的短信, 提取快递取件码 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        try {
            SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (msgs == null || msgs.length == 0) return;

            StringBuilder body = new StringBuilder();
            String sender = msgs[0].getOriginatingAddress();
            for (SmsMessage m : msgs) {
                String part = m.getDisplayMessageBody();
                if (part != null) body.append(part);
            }
            // 长短信可能分多条, onReceive 是有序广播, 这里按单次处理
            Ingestor.handle(context, body.toString(), "sms", sender == null ? "短信" : sender);
        } catch (Exception ignored) {
        }
    }
}
