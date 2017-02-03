package xyz.klinker.messenger.shared.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;
import android.util.Log;

import xyz.klinker.messenger.shared.R;
import xyz.klinker.messenger.api.implementation.Account;
import xyz.klinker.messenger.api.implementation.ApiUtils;
import xyz.klinker.messenger.shared.data.DataSource;
import xyz.klinker.messenger.shared.data.MimeType;
import xyz.klinker.messenger.shared.data.model.Conversation;
import xyz.klinker.messenger.shared.data.model.Message;
import xyz.klinker.messenger.shared.service.ReplyService;
import xyz.klinker.messenger.shared.util.DualSimUtils;
import xyz.klinker.messenger.shared.util.SendUtils;

public class CarReplyReceiver extends BroadcastReceiver {
    private static final String TAG = "CarReplyReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        String reply = null;
        if (remoteInput != null) {
            reply = remoteInput.getCharSequence(ReplyService.EXTRA_REPLY).toString();
        }

        if (reply == null) {
            Log.e(TAG, "could not find attached reply");
            return;
        }

        long conversationId = intent.getLongExtra(ReplyService.EXTRA_CONVERSATION_ID, -1);

        if (conversationId == -1) {
            Log.e(TAG, "could not find attached conversation id");
            return;
        }

        DataSource source = DataSource.getInstance(context);
        source.open();

        Conversation conversation = source.getConversation(conversationId);
        Message m = new Message();
        m.conversationId = conversationId;
        m.type = Message.TYPE_SENDING;
        m.data = reply;
        m.timestamp = System.currentTimeMillis();
        m.mimeType = MimeType.TEXT_PLAIN;
        m.read = true;
        m.seen = true;
        m.from = null;
        m.color = null;
        m.simPhoneNumber = conversation.simSubscriptionId != null ? DualSimUtils.get(context)
                .getPhoneNumberFromSimSubscription(conversation.simSubscriptionId) : null;

        source.insertMessage(context, m, conversationId);
        source.readConversation(context, conversationId);

        Log.v(TAG, "sending message \"" + reply + "\" to \"" + conversation.phoneNumbers + "\"");

        new SendUtils(conversation.simSubscriptionId)
                .send(context, reply, conversation.phoneNumbers);

        // cancel the notification we just replied to or
        // if there are no more notifications, cancel the summary as well
        Cursor unseenMessages = source.getUnseenMessages();
        if (unseenMessages.getCount() <= 0) {
            NotificationManagerCompat.from(context).cancelAll();
        } else {
            NotificationManagerCompat.from(context).cancel((int) conversationId);
        }

        new ApiUtils().dismissNotification(Account.get(context).accountId,
                Account.get(context).deviceId,
                conversationId);

        unseenMessages.close();
        source.close();

        ConversationListUpdatedReceiver.sendBroadcast(context, conversationId, context.getString(R.string.you) + ": " + reply, true);
        MessageListUpdatedReceiver.sendBroadcast(context, conversationId);
    }
}