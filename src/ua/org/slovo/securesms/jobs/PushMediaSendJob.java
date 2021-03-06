package ua.org.slovo.securesms.jobs;

import android.content.Context;
import android.util.Log;

import ua.org.slovo.securesms.ApplicationContext;
import ua.org.slovo.securesms.attachments.Attachment;
import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.database.DatabaseFactory;
import ua.org.slovo.securesms.database.MmsDatabase;
import ua.org.slovo.securesms.database.NoSuchMessageException;
import ua.org.slovo.securesms.dependencies.InjectableType;
import ua.org.slovo.securesms.mms.MediaConstraints;
import ua.org.slovo.securesms.mms.OutgoingMediaMessage;
import ua.org.slovo.securesms.recipients.RecipientFactory;
import ua.org.slovo.securesms.recipients.Recipients;
import ua.org.slovo.securesms.transport.InsecureFallbackApprovalException;
import ua.org.slovo.securesms.transport.RetryLaterException;
import ua.org.slovo.securesms.transport.UndeliverableMessageException;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import ws.com.google.android.mms.MmsException;

import static ua.org.slovo.securesms.dependencies.TextSecureCommunicationModule.TextSecureMessageSenderFactory;

public class PushMediaSendJob extends PushSendJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = PushMediaSendJob.class.getSimpleName();

  @Inject transient TextSecureMessageSenderFactory messageSenderFactory;

  private final long messageId;

  public PushMediaSendJob(Context context, long messageId, String destination) {
    super(context, constructParameters(context, destination));
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {
    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    mmsDatabase.markAsSending(messageId);
    mmsDatabase.markAsPush(messageId);
  }

  @Override
  public void onSend(MasterSecret masterSecret)
      throws RetryLaterException, MmsException, NoSuchMessageException,
             UndeliverableMessageException
  {
    MmsDatabase          database = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage message  = database.getOutgoingMessage(masterSecret, messageId);

    try {
      deliver(masterSecret, message);
      database.markAsPush(messageId);
      database.markAsSecure(messageId);
      database.markAsSent(messageId);
      markAttachmentsUploaded(messageId, message.getAttachments());
    } catch (InsecureFallbackApprovalException ifae) {
      Log.w(TAG, ifae);
      database.markAsPendingInsecureSmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
      ApplicationContext.getInstance(context).getJobManager().add(new DirectoryRefreshJob(context));
    } catch (UntrustedIdentityException uie) {
      Log.w(TAG, uie);
      Recipients recipients  = RecipientFactory.getRecipientsFromString(context, uie.getE164Number(), false);
      long       recipientId = recipients.getPrimaryRecipient().getRecipientId();

      database.addMismatchedIdentity(messageId, recipientId, uie.getIdentityKey());
      database.markAsSentFailed(messageId);
      database.markAsPush(messageId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof RequirementNotMetException) return true;
    if (exception instanceof RetryLaterException)        return true;

    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }

  private void deliver(MasterSecret masterSecret, OutgoingMediaMessage message)
      throws RetryLaterException, InsecureFallbackApprovalException, UntrustedIdentityException,
             UndeliverableMessageException
  {
    if (message.getRecipients() == null                       ||
        message.getRecipients().getPrimaryRecipient() == null ||
        message.getRecipients().getPrimaryRecipient().getNumber() == null)
    {
      throw new UndeliverableMessageException("No destination address.");
    }

    SignalServiceMessageSender messageSender = messageSenderFactory.create();

    try {
      SignalServiceAddress          address           = getPushAddress(message.getRecipients().getPrimaryRecipient().getNumber());
      List<Attachment>              scaledAttachments = scaleAttachments(masterSecret, MediaConstraints.PUSH_CONSTRAINTS, message.getAttachments());
      List<SignalServiceAttachment> attachmentStreams = getAttachmentsFor(masterSecret, scaledAttachments);
      SignalServiceDataMessage      mediaMessage      = SignalServiceDataMessage.newBuilder()
                                                                                .withBody(message.getBody())
                                                                                .withAttachments(attachmentStreams)
                                                                                .withTimestamp(message.getSentTimeMillis())
                                                                                .build();

      messageSender.sendMessage(address, mediaMessage);
    } catch (InvalidNumberException | UnregisteredUserException e) {
      Log.w(TAG, e);
      throw new InsecureFallbackApprovalException(e);
    } catch (FileNotFoundException e) {
      Log.w(TAG, e);
      throw new UndeliverableMessageException(e);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new RetryLaterException(e);
    }
  }
}
