package ua.org.slovo.securesms.mms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import ua.org.slovo.securesms.attachments.Attachment;
import ua.org.slovo.securesms.database.ThreadDatabase;
import ua.org.slovo.securesms.recipients.Recipients;
import ua.org.slovo.securesms.util.Base64;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class OutgoingGroupMediaMessage extends OutgoingSecureMediaMessage {

  private final GroupContext group;

  public OutgoingGroupMediaMessage(@NonNull Recipients recipients,
                                   @NonNull String encodedGroupContext,
                                   @NonNull List<Attachment> avatar,
                                   long sentTimeMillis)
      throws IOException
  {
    super(recipients, encodedGroupContext, avatar, sentTimeMillis,
          ThreadDatabase.DistributionTypes.CONVERSATION);

    this.group = GroupContext.parseFrom(Base64.decode(encodedGroupContext));
  }

  public OutgoingGroupMediaMessage(@NonNull Recipients recipients,
                                   @NonNull GroupContext group,
                                   @Nullable final Attachment avatar,
                                   long sentTimeMillis)
  {
    super(recipients, Base64.encodeBytes(group.toByteArray()),
          new LinkedList<Attachment>() {{if (avatar != null) add(avatar);}},
          System.currentTimeMillis(),
          ThreadDatabase.DistributionTypes.CONVERSATION);

    this.group = group;
  }

  @Override
  public boolean isGroup() {
    return true;
  }

  public boolean isGroupUpdate() {
    return group.getType().getNumber() == GroupContext.Type.UPDATE_VALUE;
  }

  public boolean isGroupQuit() {
    return group.getType().getNumber() == GroupContext.Type.QUIT_VALUE;
  }

  public GroupContext getGroupContext() {
    return group;
  }
}
