package ua.org.slovo.securesms.jobs;

import android.content.Context;
import android.telephony.SmsMessage;
import android.util.Log;
import android.util.Pair;

import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.crypto.MasterSecretUnion;
import ua.org.slovo.securesms.crypto.MasterSecretUtil;
import ua.org.slovo.securesms.database.DatabaseFactory;
import ua.org.slovo.securesms.database.EncryptingSmsDatabase;
import ua.org.slovo.securesms.notifications.MessageNotifier;
import ua.org.slovo.securesms.recipients.RecipientFactory;
import ua.org.slovo.securesms.recipients.Recipients;
import ua.org.slovo.securesms.service.KeyCachingService;
import ua.org.slovo.securesms.sms.IncomingTextMessage;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

public class SmsReceiveJob extends ContextJob {

  private static final long serialVersionUID = 1L;

  private static final String TAG = SmsReceiveJob.class.getSimpleName();

  private final Object[] pdus;
  private final int      subscriptionId;

  public SmsReceiveJob(Context context, Object[] pdus, int subscriptionId) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withWakeLock(true)
                                .create());

    this.pdus           = pdus;
    this.subscriptionId = subscriptionId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() {
    Optional<IncomingTextMessage> message      = assembleMessageFragments(pdus, subscriptionId);
    MasterSecret                  masterSecret = KeyCachingService.getMasterSecret(context);

    MasterSecretUnion masterSecretUnion;

    if (masterSecret == null) {
      masterSecretUnion = new MasterSecretUnion(MasterSecretUtil.getAsymmetricMasterSecret(context, null));
    } else {
      masterSecretUnion = new MasterSecretUnion(masterSecret);
    }

    if (message.isPresent() && !isBlocked(message.get())) {
      Pair<Long, Long> messageAndThreadId = storeMessage(masterSecretUnion, message.get());
      MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
    } else if (message.isPresent()) {
      Log.w(TAG, "*** Received blocked SMS, ignoring...");
    }
  }

  @Override
  public void onCanceled() {

  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }

  private boolean isBlocked(IncomingTextMessage message) {
    if (message.getSender() != null) {
      Recipients recipients = RecipientFactory.getRecipientsFromString(context, message.getSender(), false);
      return recipients.isBlocked();
    }

    return false;
  }

  private Pair<Long, Long> storeMessage(MasterSecretUnion masterSecret, IncomingTextMessage message) {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    Pair<Long, Long> messageAndThreadId;

    if (message.isSecureMessage()) {
      IncomingTextMessage placeholder = new IncomingTextMessage(message, "");
      messageAndThreadId = database.insertMessageInbox(placeholder);
      database.markAsLegacyVersion(messageAndThreadId.first);
    } else {
      messageAndThreadId = database.insertMessageInbox(masterSecret, message);
    }

    return messageAndThreadId;
  }

  private Optional<IncomingTextMessage> assembleMessageFragments(Object[] pdus, int subscriptionId) {
    List<IncomingTextMessage> messages = new LinkedList<>();

    for (Object pdu : pdus) {
      messages.add(new IncomingTextMessage(SmsMessage.createFromPdu((byte[])pdu), subscriptionId));
    }

    if (messages.isEmpty()) {
      return Optional.absent();
    }

    return Optional.of(new IncomingTextMessage(messages));
  }
}
