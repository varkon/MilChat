/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ua.org.slovo.securesms.sms;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import ua.org.slovo.securesms.ApplicationContext;
import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.crypto.MasterSecretUnion;
import ua.org.slovo.securesms.database.DatabaseFactory;
import ua.org.slovo.securesms.database.EncryptingSmsDatabase;
import ua.org.slovo.securesms.database.MmsDatabase;
import ua.org.slovo.securesms.database.NotInDirectoryException;
import ua.org.slovo.securesms.database.TextSecureDirectory;
import ua.org.slovo.securesms.database.ThreadDatabase;
import ua.org.slovo.securesms.database.model.MessageRecord;
import ua.org.slovo.securesms.jobs.MmsSendJob;
import ua.org.slovo.securesms.jobs.PushGroupSendJob;
import ua.org.slovo.securesms.jobs.PushMediaSendJob;
import ua.org.slovo.securesms.jobs.PushTextSendJob;
import ua.org.slovo.securesms.jobs.SmsSendJob;
import ua.org.slovo.securesms.mms.OutgoingMediaMessage;
import ua.org.slovo.securesms.push.TextSecureCommunicationFactory;
import ua.org.slovo.securesms.recipients.Recipient;
import ua.org.slovo.securesms.recipients.Recipients;
import ua.org.slovo.securesms.util.GroupUtil;
import ua.org.slovo.securesms.util.TextSecurePreferences;
import ua.org.slovo.securesms.util.Util;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;

import ws.com.google.android.mms.MmsException;

public class MessageSender {

  private static final String TAG = MessageSender.class.getSimpleName();

  public static long send(final Context context,
                          final MasterSecret masterSecret,
                          final OutgoingTextMessage message,
                          final long threadId,
                          final boolean forceSms)
  {
    EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(context);
    Recipients            recipients  = message.getRecipients();
    boolean               keyExchange = message.isKeyExchange();

    long allocatedThreadId;

    if (threadId == -1) {
      allocatedThreadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    } else {
      allocatedThreadId = threadId;
    }

    long messageId = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), allocatedThreadId,
                                                  message, forceSms, System.currentTimeMillis());

    sendTextMessage(context, recipients, forceSms, keyExchange, messageId);

    return allocatedThreadId;
  }

  public static long send(final Context context,
                          final MasterSecret masterSecret,
                          final OutgoingMediaMessage message,
                          final long threadId,
                          final boolean forceSms)
  {
    try {
      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
      MmsDatabase    database       = DatabaseFactory.getMmsDatabase(context);

      long allocatedThreadId;

      if (threadId == -1) {
        allocatedThreadId = threadDatabase.getThreadIdFor(message.getRecipients(), message.getDistributionType());
      } else {
        allocatedThreadId = threadId;
      }

      Recipients recipients = message.getRecipients();
      long       messageId  = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), message, allocatedThreadId, forceSms);

      sendMediaMessage(context, masterSecret, recipients, forceSms, messageId);

      return allocatedThreadId;
    } catch (MmsException e) {
      Log.w(TAG, e);
      return threadId;
    }
  }

  public static void resendGroupMessage(Context context, MasterSecret masterSecret, MessageRecord messageRecord, long filterRecipientId) {
    if (!messageRecord.isMms()) throw new AssertionError("Not Group");

    Recipients recipients = DatabaseFactory.getMmsAddressDatabase(context).getRecipientsForId(messageRecord.getId());
    sendGroupPush(context, recipients, messageRecord.getId(), filterRecipientId);
  }

  public static void resend(Context context, MasterSecret masterSecret, MessageRecord messageRecord) {
    try {
      long       messageId   = messageRecord.getId();
      boolean    forceSms    = messageRecord.isForcedSms();
      boolean    keyExchange = messageRecord.isKeyExchange();

      if (messageRecord.isMms()) {
        Recipients recipients = DatabaseFactory.getMmsAddressDatabase(context).getRecipientsForId(messageId);
        sendMediaMessage(context, masterSecret, recipients, forceSms, messageId);
      } else {
        Recipients recipients  = messageRecord.getRecipients();
        sendTextMessage(context, recipients, forceSms, keyExchange, messageId);
      }
    } catch (MmsException e) {
      Log.w(TAG, e);
    }
  }

  private static void sendMediaMessage(Context context, MasterSecret masterSecret,
                                       Recipients recipients, boolean forceSms, long messageId)
      throws MmsException
  {
    if (!forceSms && isSelfSend(context, recipients)) {
      sendMediaSelf(context, masterSecret, messageId);
    } else if (isGroupPushSend(recipients)) {
      sendGroupPush(context, recipients, messageId, -1);
    } else if (!forceSms && isPushMediaSend(context, recipients)) {
      sendMediaPush(context, recipients, messageId);
    } else {
      sendMms(context, messageId);
    }
  }

  private static void sendTextMessage(Context context, Recipients recipients,
                                      boolean forceSms, boolean keyExchange, long messageId)
  {
    if (!forceSms && isSelfSend(context, recipients)) {
      sendTextSelf(context, messageId);
    } else if (!forceSms && isPushTextSend(context, recipients, keyExchange)) {
      sendTextPush(context, recipients, messageId);
    } else {
      sendSms(context, recipients, messageId);
    }
  }

  private static void sendTextSelf(Context context, long messageId) {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    database.markAsSent(messageId);
    database.markAsPush(messageId);

    Pair<Long, Long> messageAndThreadId = database.copyMessageInbox(messageId);
    database.markAsPush(messageAndThreadId.first);
  }

  private static void sendMediaSelf(Context context, MasterSecret masterSecret, long messageId)
      throws MmsException
  {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    database.markAsSent(messageId);
    database.markAsPush(messageId);

    long newMessageId = database.copyMessageInbox(masterSecret, messageId);
    database.markAsPush(newMessageId);
  }

  private static void sendTextPush(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushTextSendJob(context, messageId, recipients.getPrimaryRecipient().getNumber()));
  }

  private static void sendMediaPush(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushMediaSendJob(context, messageId, recipients.getPrimaryRecipient().getNumber()));
  }

  private static void sendGroupPush(Context context, Recipients recipients, long messageId, long filterRecipientId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushGroupSendJob(context, messageId, recipients.getPrimaryRecipient().getNumber(), filterRecipientId));
  }

  private static void sendSms(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new SmsSendJob(context, messageId, recipients.getPrimaryRecipient().getName()));
  }

  private static void sendMms(Context context, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new MmsSendJob(context, messageId));
  }

  private static boolean isPushTextSend(Context context, Recipients recipients, boolean keyExchange) {
    try {
      if (!TextSecurePreferences.isPushRegistered(context)) {
        return false;
      }

      if (keyExchange) {
        return false;
      }

      Recipient recipient   = recipients.getPrimaryRecipient();
      String    destination = Util.canonicalizeNumber(context, recipient.getNumber());

      return isPushDestination(context, destination);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  private static boolean isPushMediaSend(Context context, Recipients recipients) {
    try {
      if (!TextSecurePreferences.isPushRegistered(context)) {
        return false;
      }

      if (recipients.getRecipientsList().size() > 1) {
        return false;
      }

      Recipient recipient   = recipients.getPrimaryRecipient();
      String    destination = Util.canonicalizeNumber(context, recipient.getNumber());

      return isPushDestination(context, destination);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  private static boolean isGroupPushSend(Recipients recipients) {
    return GroupUtil.isEncodedGroup(recipients.getPrimaryRecipient().getNumber());
  }

  private static boolean isSelfSend(Context context, Recipients recipients) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return false;
    }

    if (!recipients.isSingleRecipient()) {
      return false;
    }

    if (recipients.isGroupRecipient()) {
      return false;
    }

    return Util.isOwnNumber(context, recipients.getPrimaryRecipient().getNumber());
  }

  private static boolean isPushDestination(Context context, String destination) {
    TextSecureDirectory directory = TextSecureDirectory.getInstance(context);

    try {
      return directory.isSecureTextSupported(destination);
    } catch (NotInDirectoryException e) {
      try {
        SignalServiceAccountManager   accountManager = TextSecureCommunicationFactory.createManager(context);
        Optional<ContactTokenDetails> registeredUser = accountManager.getContact(destination);

        if (!registeredUser.isPresent()) {
          registeredUser = Optional.of(new ContactTokenDetails());
          registeredUser.get().setNumber(destination);
          directory.setNumber(registeredUser.get(), false);
          return false;
        } else {
          registeredUser.get().setNumber(destination);
          directory.setNumber(registeredUser.get(), true);
          return true;
        }
      } catch (IOException e1) {
        Log.w(TAG, e1);
        return false;
      }
    }
  }

}
