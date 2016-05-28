package ua.org.slovo.securesms.notifications;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import ua.org.slovo.securesms.ApplicationContext;
import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.database.DatabaseFactory;
import ua.org.slovo.securesms.database.MessagingDatabase.SyncMessageId;
import ua.org.slovo.securesms.jobs.MultiDeviceReadUpdateJob;

import java.util.LinkedList;
import java.util.List;

public class MarkReadReceiver extends MasterSecretBroadcastReceiver {

  private static final String TAG              = MarkReadReceiver.class.getSimpleName();
  public static final  String CLEAR_ACTION     = "ua.org.slovo.securesms.notifications.CLEAR";
  public static final  String THREAD_IDS_EXTRA = "thread_ids";

  @Override
  protected void onReceive(final Context context, Intent intent,
                           @Nullable final MasterSecret masterSecret)
  {
    if (!CLEAR_ACTION.equals(intent.getAction()))
      return;

    final long[] threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA);

    if (threadIds != null) {
      Log.w("TAG", "threadIds length: " + threadIds.length);

      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
                                   .cancel(MessageNotifier.NOTIFICATION_ID);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          List<SyncMessageId> messageIdsCollection = new LinkedList<>();

          for (long threadId : threadIds) {
            Log.w(TAG, "Marking as read: " + threadId);
            List<SyncMessageId> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(threadId);
            messageIdsCollection.addAll(messageIds);
          }

          MessageNotifier.updateNotification(context, masterSecret);

          if (!messageIdsCollection.isEmpty()) {
            ApplicationContext.getInstance(context)
                              .getJobManager()
                              .add(new MultiDeviceReadUpdateJob(context, messageIdsCollection));
          }

          return null;
        }
      }.execute();
    }
  }
}
