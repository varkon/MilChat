package ua.org.slovo.securesms;

import android.support.annotation.NonNull;

import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.database.model.MessageRecord;
import ua.org.slovo.securesms.recipients.Recipients;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationItem extends Unbindable {
  void bind(@NonNull MasterSecret masterSecret,
            @NonNull MessageRecord messageRecord,
            @NonNull Locale locale,
            @NonNull Set<MessageRecord> batchSelected,
            @NonNull Recipients recipients);
}
