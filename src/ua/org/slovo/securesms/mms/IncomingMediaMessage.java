package ua.org.slovo.securesms.mms;

import ua.org.slovo.securesms.attachments.Attachment;
import ua.org.slovo.securesms.attachments.PointerAttachment;
import ua.org.slovo.securesms.crypto.MasterSecretUnion;
import ua.org.slovo.securesms.database.MmsAddresses;
import ua.org.slovo.securesms.util.GroupUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;

import java.util.LinkedList;
import java.util.List;

public class IncomingMediaMessage {

  private final String  from;
  private final String  body;
  private final String  groupId;
  private final boolean push;
  private final long    sentTimeMillis;
  private final int     subscriptionId;

  private final List<String>     to          = new LinkedList<>();
  private final List<String>     cc          = new LinkedList<>();
  private final List<Attachment> attachments = new LinkedList<>();

  public IncomingMediaMessage(String from, List<String> to, List<String> cc,
                              String body, long sentTimeMillis,
                              List<Attachment> attachments, int subscriptionId)
  {
    this.from           = from;
    this.sentTimeMillis = sentTimeMillis;
    this.body           = body;
    this.groupId        = null;
    this.push           = false;
    this.subscriptionId = subscriptionId;

    this.to.addAll(to);
    this.cc.addAll(cc);
    this.attachments.addAll(attachments);
  }

  public IncomingMediaMessage(MasterSecretUnion masterSecret,
                              String from,
                              String to,
                              long sentTimeMillis,
                              int subscriptionId,
                              Optional<String> relay,
                              Optional<String> body,
                              Optional<SignalServiceGroup> group,
                              Optional<List<SignalServiceAttachment>> attachments)
  {
    this.push           = true;
    this.from           = from;
    this.sentTimeMillis = sentTimeMillis;
    this.body           = body.orNull();
    this.subscriptionId = subscriptionId;

    if (group.isPresent()) this.groupId = GroupUtil.getEncodedId(group.get().getGroupId());
    else                   this.groupId = null;

    this.to.add(to);
    this.attachments.addAll(PointerAttachment.forPointers(masterSecret, attachments));
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public String getBody() {
    return body;
  }

  public MmsAddresses getAddresses() {
    return new MmsAddresses(from, to, cc, new LinkedList<String>());
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public String getGroupId() {
    return groupId;
  }

  public boolean isPushMessage() {
    return push;
  }

  public long getSentTimeMillis() {
    return sentTimeMillis;
  }

  public boolean isGroupMessage() {
    return groupId != null || to.size() > 1 || cc.size() > 0;
  }
}
