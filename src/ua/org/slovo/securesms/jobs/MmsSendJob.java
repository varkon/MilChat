package ua.org.slovo.securesms.jobs;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import ua.org.slovo.securesms.attachments.Attachment;
import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.database.DatabaseFactory;
import ua.org.slovo.securesms.database.MmsDatabase;
import ua.org.slovo.securesms.database.NoSuchMessageException;
import ua.org.slovo.securesms.database.ThreadDatabase.DistributionTypes;
import ua.org.slovo.securesms.jobs.requirements.MasterSecretRequirement;
import ua.org.slovo.securesms.mms.CompatMmsConnection;
import ua.org.slovo.securesms.mms.MediaConstraints;
import ua.org.slovo.securesms.mms.MmsSendResult;
import ua.org.slovo.securesms.mms.OutgoingMediaMessage;
import ua.org.slovo.securesms.mms.PartAuthority;
import ua.org.slovo.securesms.notifications.MessageNotifier;
import ua.org.slovo.securesms.recipients.Recipients;
import ua.org.slovo.securesms.transport.InsecureFallbackApprovalException;
import ua.org.slovo.securesms.transport.UndeliverableMessageException;
import ua.org.slovo.securesms.util.Hex;
import ua.org.slovo.securesms.util.NumberUtil;
import ua.org.slovo.securesms.util.SmilUtil;
import ua.org.slovo.securesms.util.TelephonyUtil;
import ua.org.slovo.securesms.util.Util;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.CharacterSets;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduComposer;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.SendConf;
import ws.com.google.android.mms.pdu.SendReq;

public class MmsSendJob extends SendJob {

  private static final long serialVersionUID = 0L;

  private static final String TAG = MmsSendJob.class.getSimpleName();

  private final long messageId;

  public MmsSendJob(Context context, long messageId) {
    super(context, JobParameters.newBuilder()
                                .withGroupId("mms-operation")
                                .withRequirement(new NetworkRequirement(context))
                                .withRequirement(new MasterSecretRequirement(context))
                                .withPersistence()
                                .create());

    this.messageId = messageId;
  }

  @Override
  public void onAdded() {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    database.markAsSending(messageId);
  }

  @Override
  public void onSend(MasterSecret masterSecret) throws MmsException, NoSuchMessageException, IOException {
    MmsDatabase          database = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage message  = database.getOutgoingMessage(masterSecret, messageId);

    try {
      SendReq pdu = constructSendPdu(masterSecret, message);

      validateDestinations(message, pdu);

      final byte[]        pduBytes = getPduBytes(pdu);
      final SendConf      sendConf = new CompatMmsConnection(context).send(pduBytes, message.getSubscriptionId());
      final MmsSendResult result   = getSendResult(sendConf, pdu);

      database.markAsSent(messageId);
      markAttachmentsUploaded(messageId, message.getAttachments());
    } catch (UndeliverableMessageException | IOException e) {
      Log.w(TAG, e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (InsecureFallbackApprovalException e) {
      Log.w(TAG, e);
      database.markAsPendingInsecureSmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }

  private byte[] getPduBytes(SendReq message)
      throws IOException, UndeliverableMessageException, InsecureFallbackApprovalException
  {
    String number = TelephonyUtil.getManager(context).getLine1Number();

    message.setBody(SmilUtil.getSmilBody(message.getBody()));

    if (!TextUtils.isEmpty(number)) {
      message.setFrom(new EncodedStringValue(number));
    }

    byte[] pduBytes = new PduComposer(context, message).make();

    if (pduBytes == null) {
      throw new UndeliverableMessageException("PDU composition failed, null payload");
    }

    return pduBytes;
  }

  private MmsSendResult getSendResult(SendConf conf, SendReq message)
      throws UndeliverableMessageException
  {
    if (conf == null) {
      throw new UndeliverableMessageException("No M-Send.conf received in response to send.");
    } else if (conf.getResponseStatus() != PduHeaders.RESPONSE_STATUS_OK) {
      throw new UndeliverableMessageException("Got bad response: " + conf.getResponseStatus());
    } else if (isInconsistentResponse(message, conf)) {
      throw new UndeliverableMessageException("Mismatched response!");
    } else {
      return new MmsSendResult(conf.getMessageId(), conf.getResponseStatus());
    }
  }

  private boolean isInconsistentResponse(SendReq message, SendConf response) {
    Log.w(TAG, "Comparing: " + Hex.toString(message.getTransactionId()));
    Log.w(TAG, "With:      " + Hex.toString(response.getTransactionId()));
    return !Arrays.equals(message.getTransactionId(), response.getTransactionId());
  }

  private void validateDestinations(EncodedStringValue[] destinations) throws UndeliverableMessageException {
    if (destinations == null) return;

    for (EncodedStringValue destination : destinations) {
      if (destination == null || !NumberUtil.isValidSmsOrEmail(destination.getString())) {
        throw new UndeliverableMessageException("Invalid destination: " +
                                                (destination == null ? null : destination.getString()));
      }
    }
  }

  private void validateDestinations(OutgoingMediaMessage media, SendReq message) throws UndeliverableMessageException {
    validateDestinations(message.getTo());
    validateDestinations(message.getCc());
    validateDestinations(message.getBcc());

    if (message.getTo() == null && message.getCc() == null && message.getBcc() == null) {
      throw new UndeliverableMessageException("No to, cc, or bcc specified!");
    }

    if (media.isSecure()) {
      throw new UndeliverableMessageException("Attempt to send encrypted MMS?");
    }
  }

  private SendReq constructSendPdu(MasterSecret masterSecret, OutgoingMediaMessage message)
      throws UndeliverableMessageException
  {
    SendReq      sendReq = new SendReq();
    PduBody      body    = new PduBody();
    List<String> numbers = message.getRecipients().toNumberStringList(true);

    for (String number : numbers) {
      if (message.getDistributionType() == DistributionTypes.CONVERSATION) {
        sendReq.addTo(new EncodedStringValue(Util.toIsoBytes(number)));
      } else {
        sendReq.addBcc(new EncodedStringValue(Util.toIsoBytes(number)));
      }
    }

    sendReq.setDate(message.getSentTimeMillis() / 1000L);

    if (!TextUtils.isEmpty(message.getBody())) {
      PduPart part = new PduPart();
      part.setData(Util.toUtf8Bytes(message.getBody()));
      part.setCharset(CharacterSets.UTF_8);
      part.setContentType(ContentType.TEXT_PLAIN.getBytes());
      part.setContentId((System.currentTimeMillis()+"").getBytes());
      part.setName(("Text"+System.currentTimeMillis()).getBytes());

      body.addPart(part);
    }

    List<Attachment> scaledAttachments = scaleAttachments(masterSecret, MediaConstraints.MMS_CONSTRAINTS, message.getAttachments());

    for (Attachment attachment : scaledAttachments) {
      try {
        if (attachment.getDataUri() == null) throw new IOException("Assertion failed, attachment for outgoing MMS has no data!");

        PduPart part = new PduPart();
        part.setData(Util.readFully(PartAuthority.getAttachmentStream(context, masterSecret, attachment.getDataUri())));
        part.setContentType(Util.toIsoBytes(attachment.getContentType()));
        part.setContentId((System.currentTimeMillis() + "").getBytes());
        part.setName((System.currentTimeMillis() + "").getBytes());

        body.addPart(part);
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }

    sendReq.setBody(body);
    return sendReq;
  }

  private void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long       threadId   = DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId);
    Recipients recipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);

    if (recipients != null) {
      MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
    }
  }
}
