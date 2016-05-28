package ua.org.slovo.securesms;

import android.support.annotation.NonNull;

import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.database.model.ThreadRecord;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationListItem extends Unbindable {

  public void bind(@NonNull MasterSecret masterSecret, @NonNull ThreadRecord thread,
                   @NonNull Locale locale, @NonNull Set<Long> selectedThreads, boolean batchMode);
}
