package ua.org.slovo.securesms.attachments;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import ua.org.slovo.securesms.crypto.MasterSecretUnion;
import ua.org.slovo.securesms.crypto.MediaKey;
import ua.org.slovo.securesms.database.AttachmentDatabase;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import java.util.LinkedList;
import java.util.List;

public class PointerAttachment extends Attachment {

  public PointerAttachment(@NonNull String contentType, int transferState, long size,
                           @NonNull String location, @NonNull String key, @NonNull String relay)
  {
    super(contentType, transferState, size, location, key, relay);
  }

  @Nullable
  @Override
  public Uri getDataUri() {
    return null;
  }

  @Nullable
  @Override
  public Uri getThumbnailUri() {
    return null;
  }


  public static List<Attachment> forPointers(@NonNull MasterSecretUnion masterSecret, Optional<List<SignalServiceAttachment>> pointers) {
    List<Attachment> results = new LinkedList<>();

    if (pointers.isPresent()) {
      for (SignalServiceAttachment pointer : pointers.get()) {
        if (pointer.isPointer()) {
          String encryptedKey = MediaKey.getEncrypted(masterSecret, pointer.asPointer().getKey());
          results.add(new PointerAttachment(pointer.getContentType(),
                                            AttachmentDatabase.TRANSFER_PROGRESS_AUTO_PENDING,
                                            pointer.asPointer().getSize().or(0),
                                            String.valueOf(pointer.asPointer().getId()),
                                            encryptedKey, pointer.asPointer().getRelay().orNull()));
        }
      }
    }

    return results;
  }
}
