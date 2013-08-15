package net.rolisoft.textforwarder;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmsReceiver extends BroadcastReceiver {

    private final String TAG = this.toString();

    @Override
    public void onReceive(final Context context, Intent intent)
    {
        Log.i(TAG, "Received SMS broadcast...");

        final SharedPreferences sp = context.getSharedPreferences("fwd", 0);
        if (!sp.getBoolean("forward", true) || !sp.getBoolean("forward_sms", true) || !MainActivity.isConnectedToInternet(context)) {
            Log.w(TAG, "Forwarding disabled or no internet connection.");
            return;
        }

        WakeLocker.pushd(context);

        try {
            List<TextMessage> messages = getMessagesFrom(context, intent);

            for (TextMessage msg : messages) {
                MainActivity.sendMessageAsync(context, sp, "send", msg.from, msg.body);
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error while extracting messages from intent.", ex);
            MainActivity.displayNotification(context, "Request to send failed", "Local error: " + ex.getClass().getName() + ": " + ex.getMessage());
        } finally {
            WakeLocker.popd();
        }

        setResultCode(Activity.RESULT_OK);
    }

    public static List<TextMessage> getMessagesFrom(Context context, Intent intent)
    {
        Bundle bundle = intent.getExtras();
        List<TextMessage> messages = new ArrayList<TextMessage>();
        Map<String, Integer> msgidx = new HashMap<String, Integer>();

        if (bundle == null || !bundle.containsKey("pdus")) {
            return messages;
        }

        Object[] pdu = (Object[])bundle.get("pdus");

        if (pdu == null || pdu.length == 0) {
            return messages;
        }

        for (int i = 0; i < pdu.length; i++) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[])pdu[i]);
            String from = sms.getOriginatingAddress();
            String body = sms.getMessageBody();

            if (msgidx.containsKey(from)) {
                messages.get(msgidx.get(from)).append(body);
            } else {
                msgidx.put(from, messages.size());
                messages.add(new TextMessage(from, (double)sms.getTimestampMillis() / 1000.0, body));
            }
        }

        return messages;
    }

}
