package ua.org.slovo.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import ua.org.slovo.securesms.ApplicationContext;
import ua.org.slovo.securesms.attachments.DatabaseAttachment;
import ua.org.slovo.securesms.attachments.PointerAttachment;
import ua.org.slovo.securesms.crypto.IdentityKeyUtil;
import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.crypto.MasterSecretUnion;
import ua.org.slovo.securesms.crypto.MasterSecretUtil;
import ua.org.slovo.securesms.crypto.SecurityEvent;
import ua.org.slovo.securesms.crypto.storage.SignalProtocolStoreImpl;
import ua.org.slovo.securesms.crypto.storage.TextSecureSessionStore;
import ua.org.slovo.securesms.database.DatabaseFactory;
import ua.org.slovo.securesms.database.EncryptingSmsDatabase;
import ua.org.slovo.securesms.database.MessagingDatabase.SyncMessageId;
import ua.org.slovo.securesms.database.MmsDatabase;
import ua.org.slovo.securesms.database.NoSuchMessageException;
import ua.org.slovo.securesms.database.PushDatabase;
import ua.org.slovo.securesms.database.ThreadDatabase;
import ua.org.slovo.securesms.groups.GroupMessageProcessor;
import ua.org.slovo.securesms.mms.IncomingMediaMessage;
import ua.org.slovo.securesms.mms.OutgoingMediaMessage;
import ua.org.slovo.securesms.mms.OutgoingSecureMediaMessage;
import ua.org.slovo.securesms.notifications.MessageNotifier;
import ua.org.slovo.securesms.recipients.RecipientFactory;
import ua.org.slovo.securesms.recipients.Recipients;
import ua.org.slovo.securesms.service.KeyCachingService;
import ua.org.slovo.securesms.sms.IncomingEncryptedMessage;
import ua.org.slovo.securesms.sms.IncomingEndSessionMessage;
import ua.org.slovo.securesms.sms.IncomingPreKeyBundleMessage;
import ua.org.slovo.securesms.sms.IncomingTextMessage;
import ua.org.slovo.securesms.sms.OutgoingTextMessage;
import ua.org.slovo.securesms.util.Base64;
import ua.org.slovo.securesms.util.GroupUtil;
import ua.org.slovo.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.List;
import java.util.concurrent.TimeUnit;

import ws.com.google.android.mms.MmsException;

public class PushDecryptJob extends ContextJob {

  private static final long serialVersionUID = 2L;

  public static final String TAG = PushDecryptJob.class.getSimpleName();

  private final long messageId;
  private final long smsMessageId;

  public PushDecryptJob(Context context, long pushMessageId, String sender) {
    this(context, pushMessageId, -1, sender);
  }

  public PushDecryptJob(Context context, long pushMessageId, long smsMessageId, String sender) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withGroupId("__PUSH_DECRYPT_JOB__")
                                .withWakeLock(true, 5, TimeUnit.SECONDS)
                                .create());
    this.messageId    = pushMessageId;
    this.smsMessageId = smsMessageId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws NoSuchMessageException {

    if (!IdentityKeyUtil.hasIdentityKey(context)) {
      Log.w(TAG, "Skipping job, waiting for migration...");
      MessageNotifier.updateNotification(context, null, true, -2);
      return;
    }

    MasterSecret          masterSecret         = KeyCachingService.getMasterSecret(context);
    PushDatabase          database             = DatabaseFactory.getPushDatabase(context);
    SignalServiceEnvelope envelope             = database.get(messageId);
    Optional<Long>        optionalSmsMessageId = smsMessageId > 0 ? Optional.of(smsMessageId) :
                                                                 Optional.<Long>absent();

    MasterSecretUnion masterSecretUnion;

    if (masterSecret == null) masterSecretUnion = new MasterSecretUnion(MasterSecretUtil.getAsymmetricMasterSecret(context, null));
    else                      masterSecretUnion = new MasterSecretUnion(masterSecret);

    handleMessage(masterSecretUnion, envelope, optionalSmsMessageId);
    database.delete(messageId);
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {

  }

  private void handleMessage(MasterSecretUnion masterSecret, SignalServiceEnvelope envelope, Optional<Long> smsMessageId) {
    try {
      SignalProtocolStore  axolotlStore = new SignalProtocolStoreImpl(context);
      SignalServiceAddress localAddress = new SignalServiceAddress(TextSecurePreferences.getLocalNumber(context));
      SignalServiceCipher  cipher       = new SignalServiceCipher(localAddress, axolotlStore);

      SignalServiceContent content = cipher.decrypt(envelope);

      if (content.getDataMessage().isPresent()) {
        SignalServiceDataMessage message = content.getDataMessage().get();

        if      (message.isEndSession())               handleEndSessionMessage(masterSecret, envelope, message, smsMessageId);
        else if (message.isGroupUpdate())              handleGroupMessage(masterSecret, envelope, message, smsMessageId);
        else if (message.getAttachments().isPresent()) handleMediaMessage(masterSecret, envelope, message, smsMessageId);
        else                                           handleTextMessage(masterSecret, envelope, message, smsMessageId);
      } else if (content.getSyncMessage().isPresent()) {
        SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

        if      (syncMessage.getSent().isPresent())    handleSynchronizeSentMessage(masterSecret, envelope, syncMessage.getSent().get(), smsMessageId);
        else if (syncMessage.getRequest().isPresent()) handleSynchronizeRequestMessage(masterSecret, syncMessage.getRequest().get());
        else if (syncMessage.getRead().isPresent())    handleSynchronizeReadMessage(masterSecret, syncMessage.getRead().get());
        else                                           Log.w(TAG, "Contains no known sync types...");
      }

      if (envelope.isPreKeySignalMessage()) {
        ApplicationContext.getInstance(context).getJobManager().add(new RefreshPreKeysJob(context));
      }
    } catch (InvalidVersionException e) {
      Log.w(TAG, e);
      handleInvalidVersionMessage(masterSecret, envelope, smsMessageId);
    } catch (InvalidMessageException | InvalidKeyIdException | InvalidKeyException | MmsException e) {
      Log.w(TAG, e);
      handleCorruptMessage(masterSecret, envelope, smsMessageId);
    } catch (NoSessionException e) {
      Log.w(TAG, e);
      handleNoSessionMessage(masterSecret, envelope, smsMessageId);
    } catch (LegacyMessageException e) {
      Log.w(TAG, e);
      handleLegacyMessage(masterSecret, envelope, smsMessageId);
    } catch (DuplicateMessageException e) {
      Log.w(TAG, e);
      handleDuplicateMessage(masterSecret, envelope, smsMessageId);
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, e);
      handleUntrustedIdentityMessage(masterSecret, envelope, smsMessageId);
    }
  }

  private void handleEndSessionMessage(@NonNull MasterSecretUnion        masterSecret,
                                       @NonNull SignalServiceEnvelope    envelope,
                                       @NonNull SignalServiceDataMessage message,
                                       @NonNull Optional<Long>           smsMessageId)
  {
    EncryptingSmsDatabase smsDatabase         = DatabaseFactory.getEncryptingSmsDatabase(context);
    IncomingTextMessage   incomingTextMessage = new IncomingTextMessage(envelope.getSource(),
                                                                        envelope.getSourceDevice(),
                                                                        message.getTimestamp(),
                                                                        "", Optional.<SignalServiceGroup>absent());

    long threadId;

    if (!smsMessageId.isPresent()) {
      IncomingEndSessionMessage incomingEndSessionMessage = new IncomingEndSessionMessage(incomingTextMessage);
      Pair<Long, Long>          messageAndThreadId        = smsDatabase.insertMessageInbox(masterSecret, incomingEndSessionMessage);

      threadId = messageAndThreadId.second;
    } else {
      smsDatabase.markAsEndSession(smsMessageId.get());
      threadId = smsDatabase.getThreadIdForMessage(smsMessageId.get());
    }

    SessionStore sessionStore = new TextSecureSessionStore(context);
    sessionStore.deleteAllSessions(envelope.getSource());

    SecurityEvent.broadcastSecurityUpdateEvent(context);
    MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), threadId);
  }

  private void handleGroupMessage(@NonNull MasterSecretUnion masterSecret,
                                  @NonNull SignalServiceEnvelope envelope,
                                  @NonNull SignalServiceDataMessage message,
                                  @NonNull Optional<Long> smsMessageId)
  {
    GroupMessageProcessor.process(context, masterSecret, envelope, message, false);

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }
  }

  private void handleSynchronizeSentMessage(@NonNull MasterSecretUnion masterSecret,
                                            @NonNull SignalServiceEnvelope envelope,
                                            @NonNull SentTranscriptMessage message,
                                            @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    Long threadId;

    if (message.getMessage().isGroupUpdate()) {
      threadId = GroupMessageProcessor.process(context, masterSecret, envelope, message.getMessage(), true);
    } else if (message.getMessage().getAttachments().isPresent()) {
      threadId = handleSynchronizeSentMediaMessage(masterSecret, message, smsMessageId);
    } else {
      threadId = handleSynchronizeSentTextMessage(masterSecret, message, smsMessageId);
    }

    if (threadId != null) {
      DatabaseFactory.getThreadDatabase(getContext()).setRead(threadId);
      MessageNotifier.updateNotification(getContext(), masterSecret.getMasterSecret().orNull());
    }
  }

  private void handleSynchronizeRequestMessage(@NonNull MasterSecretUnion masterSecret,
                                               @NonNull RequestMessage message)
  {
    if (message.isContactsRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceContactUpdateJob(getContext()));
    }

    if (message.isGroupsRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceGroupUpdateJob(getContext()));
    }
  }

  private void handleSynchronizeReadMessage(@NonNull MasterSecretUnion masterSecret,
                                            @NonNull List<ReadMessage> readMessages)
  {
    for (ReadMessage readMessage : readMessages) {
      DatabaseFactory.getSmsDatabase(context).setTimestampRead(new SyncMessageId(readMessage.getSender(), readMessage.getTimestamp()));
      DatabaseFactory.getMmsDatabase(context).setTimestampRead(new SyncMessageId(readMessage.getSender(), readMessage.getTimestamp()));
    }

    MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull());
  }

  private void handleMediaMessage(@NonNull MasterSecretUnion masterSecret,
                                  @NonNull SignalServiceEnvelope envelope,
                                  @NonNull SignalServiceDataMessage message,
                                  @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
    String               localNumber  = TextSecurePreferences.getLocalNumber(context);
    IncomingMediaMessage mediaMessage = new IncomingMediaMessage(masterSecret, envelope.getSource(),
                                                                 localNumber, message.getTimestamp(), -1,
                                                                 Optional.fromNullable(envelope.getRelay()),
                                                                 message.getBody(),
                                                                 message.getGroupInfo(),
                                                                 message.getAttachments());

    Pair<Long, Long>         messageAndThreadId = database.insertSecureDecryptedMessageInbox(masterSecret, mediaMessage, -1);
    List<DatabaseAttachment> attachments        = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageAndThreadId.first);

    for (DatabaseAttachment attachment : attachments) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new AttachmentDownloadJob(context, messageAndThreadId.first,
                                                       attachment.getAttachmentId()));
    }

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }

    MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), messageAndThreadId.second);
  }

  private long handleSynchronizeSentMediaMessage(@NonNull MasterSecretUnion masterSecret,
                                                 @NonNull SentTranscriptMessage message,
                                                 @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    MmsDatabase           database     = DatabaseFactory.getMmsDatabase(context);
    Recipients            recipients   = getSyncMessageDestination(message);
    OutgoingMediaMessage  mediaMessage = new OutgoingMediaMessage(recipients, message.getMessage().getBody().orNull(),
                                                                  PointerAttachment.forPointers(masterSecret, message.getMessage().getAttachments()),
                                                                  message.getTimestamp(), -1, ThreadDatabase.DistributionTypes.DEFAULT);

    mediaMessage = new OutgoingSecureMediaMessage(mediaMessage);

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    long messageId = database.insertMessageOutbox(masterSecret, mediaMessage, threadId, false);

    database.markAsSent(messageId);
    database.markAsPush(messageId);

    for (DatabaseAttachment attachment : DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageId)) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new AttachmentDownloadJob(context, messageId, attachment.getAttachmentId()));
    }

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }

    return threadId;
  }

  private void handleTextMessage(@NonNull MasterSecretUnion masterSecret,
                                 @NonNull SignalServiceEnvelope envelope,
                                 @NonNull SignalServiceDataMessage message,
                                 @NonNull Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
    String                body     = message.getBody().isPresent() ? message.getBody().get() : "";

    Pair<Long, Long> messageAndThreadId;

    if (smsMessageId.isPresent() && !message.getGroupInfo().isPresent()) {
      messageAndThreadId = database.updateBundleMessageBody(masterSecret, smsMessageId.get(), body);
    } else {
      IncomingTextMessage textMessage = new IncomingTextMessage(envelope.getSource(),
                                                                envelope.getSourceDevice(),
                                                                message.getTimestamp(), body,
                                                                message.getGroupInfo());

      textMessage = new IncomingEncryptedMessage(textMessage, body);
      messageAndThreadId = database.insertMessageInbox(masterSecret, textMessage);

      if (smsMessageId.isPresent()) database.deleteMessage(smsMessageId.get());
    }

    MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), messageAndThreadId.second);
  }

  private long handleSynchronizeSentTextMessage(@NonNull MasterSecretUnion masterSecret,
                                                @NonNull SentTranscriptMessage message,
                                                @NonNull Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase database            = DatabaseFactory.getEncryptingSmsDatabase(context);
    Recipients            recipients          = getSyncMessageDestination(message);
    String                body                = message.getMessage().getBody().or("");
    OutgoingTextMessage   outgoingTextMessage = new OutgoingTextMessage(recipients, body, -1);

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    long messageId = database.insertMessageOutbox(masterSecret, threadId, outgoingTextMessage, false, message.getTimestamp());

    database.markAsSent(messageId);
    database.markAsPush(messageId);
    database.markAsSecure(messageId);

    if (smsMessageId.isPresent()) {
      database.deleteMessage(smsMessageId.get());
    }

    return threadId;
  }

  private void handleInvalidVersionMessage(@NonNull MasterSecretUnion masterSecret,
                                           @NonNull SignalServiceEnvelope envelope,
                                           @NonNull Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Pair<Long, Long> messageAndThreadId = insertPlaceholder(envelope);
      smsDatabase.markAsInvalidVersionKeyExchange(messageAndThreadId.first);
      MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), messageAndThreadId.second);
    } else {
      smsDatabase.markAsInvalidVersionKeyExchange(smsMessageId.get());
    }
  }

  private void handleCorruptMessage(@NonNull MasterSecretUnion masterSecret,
                                    @NonNull SignalServiceEnvelope envelope,
                                    @NonNull Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Pair<Long, Long> messageAndThreadId = insertPlaceholder(envelope);
      smsDatabase.markAsDecryptFailed(messageAndThreadId.first);
      MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), messageAndThreadId.second);
    } else {
      smsDatabase.markAsDecryptFailed(smsMessageId.get());
    }
  }

  private void handleNoSessionMessage(@NonNull MasterSecretUnion masterSecret,
                                      @NonNull SignalServiceEnvelope envelope,
                                      @NonNull Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Pair<Long, Long> messageAndThreadId = insertPlaceholder(envelope);
      smsDatabase.markAsNoSession(messageAndThreadId.first);
      MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), messageAndThreadId.second);
    } else {
      smsDatabase.markAsNoSession(smsMessageId.get());
    }
  }

  private void handleLegacyMessage(@NonNull MasterSecretUnion masterSecret,
                                   @NonNull SignalServiceEnvelope envelope,
                                   @NonNull Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Pair<Long, Long> messageAndThreadId = insertPlaceholder(envelope);
      smsDatabase.markAsLegacyVersion(messageAndThreadId.first);
      MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), messageAndThreadId.second);
    } else {
      smsDatabase.markAsLegacyVersion(smsMessageId.get());
    }
  }

  private void handleDuplicateMessage(@NonNull MasterSecretUnion masterSecret,
                                      @NonNull SignalServiceEnvelope envelope,
                                      @NonNull Optional<Long> smsMessageId)
  {
    // Let's start ignoring these now
//    SmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
//
//    if (smsMessageId <= 0) {
//      Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, envelope);
//      smsDatabase.markAsDecryptDuplicate(messageAndThreadId.first);
//      MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
//    } else {
//      smsDatabase.markAsDecryptDuplicate(smsMessageId);
//    }
  }

  private void handleUntrustedIdentityMessage(@NonNull MasterSecretUnion masterSecret,
                                              @NonNull SignalServiceEnvelope envelope,
                                              @NonNull Optional<Long> smsMessageId)
  {
    try {
      EncryptingSmsDatabase database       = DatabaseFactory.getEncryptingSmsDatabase(context);
      Recipients            recipients     = RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
      long                  recipientId    = recipients.getPrimaryRecipient().getRecipientId();
      PreKeySignalMessage   whisperMessage = new PreKeySignalMessage(envelope.getLegacyMessage());
      IdentityKey           identityKey    = whisperMessage.getIdentityKey();
      String                encoded        = Base64.encodeBytes(envelope.getLegacyMessage());
      IncomingTextMessage   textMessage    = new IncomingTextMessage(envelope.getSource(), envelope.getSourceDevice(),
                                                                     envelope.getTimestamp(), encoded,
                                                                     Optional.<SignalServiceGroup>absent());

      if (!smsMessageId.isPresent()) {
        IncomingPreKeyBundleMessage bundleMessage      = new IncomingPreKeyBundleMessage(textMessage, encoded);
        Pair<Long, Long>            messageAndThreadId = database.insertMessageInbox(masterSecret, bundleMessage);

        database.setMismatchedIdentity(messageAndThreadId.first, recipientId, identityKey);
        MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), messageAndThreadId.second);
      } else {
        database.updateMessageBody(masterSecret, smsMessageId.get(), encoded);
        database.markAsPreKeyBundle(smsMessageId.get());
        database.setMismatchedIdentity(smsMessageId.get(), recipientId, identityKey);
      }
    } catch (InvalidMessageException | InvalidVersionException e) {
      throw new AssertionError(e);
    }
  }

  private Pair<Long, Long> insertPlaceholder(@NonNull SignalServiceEnvelope envelope) {
    EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(context);
    IncomingTextMessage   textMessage = new IncomingTextMessage(envelope.getSource(), envelope.getSourceDevice(),
                                                                envelope.getTimestamp(), "",
                                                                Optional.<SignalServiceGroup>absent());

    textMessage = new IncomingEncryptedMessage(textMessage, "");
    return database.insertMessageInbox(textMessage);
  }

  private Recipients getSyncMessageDestination(SentTranscriptMessage message) {
    if (message.getMessage().getGroupInfo().isPresent()) {
      return RecipientFactory.getRecipientsFromString(context, GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get().getGroupId()), false);
    } else {
      return RecipientFactory.getRecipientsFromString(context, message.getDestination().get(), false);
    }
  }
}
