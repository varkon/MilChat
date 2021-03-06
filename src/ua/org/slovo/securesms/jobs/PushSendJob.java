package ua.org.slovo.securesms.jobs;

import android.content.Context;
import android.util.Log;

import ua.org.slovo.securesms.attachments.Attachment;
import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.database.DatabaseFactory;
import ua.org.slovo.securesms.database.TextSecureDirectory;
import ua.org.slovo.securesms.events.PartProgressEvent;
import ua.org.slovo.securesms.jobs.requirements.MasterSecretRequirement;
import ua.org.slovo.securesms.mms.PartAuthority;
import ua.org.slovo.securesms.notifications.MessageNotifier;
import ua.org.slovo.securesms.recipients.Recipients;
import ua.org.slovo.securesms.util.Util;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import de.greenrobot.event.EventBus;
import ws.com.google.android.mms.ContentType;

public abstract class PushSendJob extends SendJob {

  private static final String TAG = PushSendJob.class.getSimpleName();

  protected PushSendJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  protected static JobParameters constructParameters(Context context, String destination) {
    JobParameters.Builder builder = JobParameters.newBuilder();
    builder.withPersistence();
    builder.withGroupId(destination);
    builder.withRequirement(new MasterSecretRequirement(context));
    builder.withRequirement(new NetworkRequirement(context));
    builder.withRetryCount(5);

    return builder.create();
  }

  protected SignalServiceAddress getPushAddress(String number) throws InvalidNumberException {
    String e164number = Util.canonicalizeNumber(context, number);
    String relay      = TextSecureDirectory.getInstance(context).getRelay(e164number);
    return new SignalServiceAddress(e164number, Optional.fromNullable(relay));
  }

  protected List<SignalServiceAttachment> getAttachmentsFor(MasterSecret masterSecret, List<Attachment> parts) {
    List<SignalServiceAttachment> attachments = new LinkedList<>();

    for (final Attachment attachment : parts) {
      if (ContentType.isImageType(attachment.getContentType()) ||
          ContentType.isAudioType(attachment.getContentType()) ||
          ContentType.isVideoType(attachment.getContentType()))
      {
        try {
          if (attachment.getDataUri() == null) throw new IOException("Assertion failed, outgoing attachment has no data!");
          InputStream is = PartAuthority.getAttachmentStream(context, masterSecret, attachment.getDataUri());
          attachments.add(SignalServiceAttachment.newStreamBuilder()
                                                 .withStream(is)
                                                 .withContentType(attachment.getContentType())
                                                 .withLength(attachment.getSize())
                                                 .withListener(new ProgressListener() {
                                                   @Override
                                                   public void onAttachmentProgress(long total, long progress) {
                                                     EventBus.getDefault().postSticky(new PartProgressEvent(attachment, total, progress));
                                                   }
                                                 })
                                                 .build());
        } catch (IOException ioe) {
          Log.w(TAG, "Couldn't open attachment", ioe);
        }
      }
    }

    return attachments;
  }

  protected void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long       threadId   = DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId);
    Recipients recipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);

    if (threadId != -1 && recipients != null) {
      MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
    }
  }
}
