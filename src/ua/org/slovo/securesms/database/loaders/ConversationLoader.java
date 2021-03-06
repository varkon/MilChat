package ua.org.slovo.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import ua.org.slovo.securesms.database.DatabaseFactory;
import ua.org.slovo.securesms.util.AbstractCursorLoader;

public class ConversationLoader extends AbstractCursorLoader {
  private final long threadId;
  private       long limit;

  public ConversationLoader(Context context, long threadId, long limit) {
    super(context);
    this.threadId = threadId;
    this.limit  = limit;
  }

  public boolean hasLimit() {
    return limit > 0;
  }

  @Override
  public Cursor getCursor() {
    return DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId, limit);
  }
}
