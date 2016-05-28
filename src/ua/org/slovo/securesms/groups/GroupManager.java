package ua.org.slovo.securesms.groups;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.protobuf.ByteString;

import ua.org.slovo.securesms.attachments.Attachment;
import ua.org.slovo.securesms.attachments.UriAttachment;
import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.database.AttachmentDatabase;
import ua.org.slovo.securesms.database.DatabaseFactory;
import ua.org.slovo.securesms.database.GroupDatabase;
import ua.org.slovo.securesms.mms.OutgoingGroupMediaMessage;
import ua.org.slovo.securesms.providers.SingleUseBlobProvider;
import ua.org.slovo.securesms.recipients.Recipient;
import ua.org.slovo.securesms.recipients.RecipientFactory;
import ua.org.slovo.securesms.recipients.Recipients;
import ua.org.slovo.securesms.sms.MessageSender;
import ua.org.slovo.securesms.util.BitmapUtil;
import ua.org.slovo.securesms.util.GroupUtil;
import ua.org.slovo.securesms.util.TextSecurePreferences;
import ua.org.slovo.securesms.util.Util;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import ws.com.google.android.mms.ContentType;

public class GroupManager {
  public static @NonNull GroupActionResult createGroup(@NonNull  Context        context,
                                                       @NonNull  MasterSecret   masterSecret,
                                                       @NonNull  Set<Recipient> members,
                                                       @Nullable Bitmap         avatar,
                                                       @Nullable String         name)
      throws InvalidNumberException
  {
    final byte[]        avatarBytes       = BitmapUtil.toByteArray(avatar);
    final GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    final byte[]        groupId           = groupDatabase.allocateGroupId();
    final Set<String>   memberE164Numbers = getE164Numbers(context, members);

    memberE164Numbers.add(TextSecurePreferences.getLocalNumber(context));
    groupDatabase.create(groupId, name, new LinkedList<>(memberE164Numbers), null, null);
    groupDatabase.updateAvatar(groupId, avatarBytes);
    return sendGroupUpdate(context, masterSecret, groupId, memberE164Numbers, name, avatarBytes);
  }

  private static Set<String> getE164Numbers(Context context, Collection<Recipient> recipients)
      throws InvalidNumberException
  {
    final Set<String> results = new HashSet<>();
    for (Recipient recipient : recipients) {
      results.add(Util.canonicalizeNumber(context, recipient.getNumber()));
    }

    return results;
  }

  public static GroupActionResult updateGroup(@NonNull  Context        context,
                                              @NonNull  MasterSecret   masterSecret,
                                              @NonNull  byte[]         groupId,
                                              @NonNull  Set<Recipient> members,
                                              @Nullable Bitmap         avatar,
                                              @Nullable String         name)
      throws InvalidNumberException
  {
    final GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    final Set<String>   memberE164Numbers = getE164Numbers(context, members);
    final byte[]        avatarBytes       = BitmapUtil.toByteArray(avatar);

    memberE164Numbers.add(TextSecurePreferences.getLocalNumber(context));
    groupDatabase.updateMembers(groupId, new LinkedList<>(memberE164Numbers));
    groupDatabase.updateTitle(groupId, name);
    groupDatabase.updateAvatar(groupId, avatarBytes);

    return sendGroupUpdate(context, masterSecret, groupId, memberE164Numbers, name, avatarBytes);
  }

  private static GroupActionResult sendGroupUpdate(@NonNull  Context      context,
                                                   @NonNull  MasterSecret masterSecret,
                                                   @NonNull  byte[]       groupId,
                                                   @NonNull  Set<String>  e164numbers,
                                                   @Nullable String       groupName,
                                                   @Nullable byte[]       avatar)
  {
    Attachment avatarAttachment = null;
    String     groupRecipientId = GroupUtil.getEncodedId(groupId);
    Recipients groupRecipient   = RecipientFactory.getRecipientsFromString(context, groupRecipientId, false);

    GroupContext.Builder groupContextBuilder = GroupContext.newBuilder()
                                                           .setId(ByteString.copyFrom(groupId))
                                                           .setType(GroupContext.Type.UPDATE)
                                                           .addAllMembers(e164numbers);
    if (groupName != null) groupContextBuilder.setName(groupName);
    GroupContext groupContext = groupContextBuilder.build();

    if (avatar != null) {
      Uri avatarUri = SingleUseBlobProvider.getInstance().createUri(avatar);
      avatarAttachment = new UriAttachment(avatarUri, ContentType.IMAGE_JPEG, AttachmentDatabase.TRANSFER_PROGRESS_DONE, avatar.length);
    }

    OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(groupRecipient, groupContext, avatarAttachment, System.currentTimeMillis());
    long                      threadId        = MessageSender.send(context, masterSecret, outgoingMessage, -1, false);

    return new GroupActionResult(groupRecipient, threadId);
  }

  public static class GroupActionResult {
    private Recipients groupRecipient;
    private long       threadId;

    public GroupActionResult(Recipients groupRecipient, long threadId) {
      this.groupRecipient = groupRecipient;
      this.threadId       = threadId;
    }

    public Recipients getGroupRecipient() {
      return groupRecipient;
    }

    public long getThreadId() {
      return threadId;
    }
  }
}
