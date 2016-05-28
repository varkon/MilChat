package ua.org.slovo.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import ua.org.slovo.securesms.database.DatabaseFactory;
import ua.org.slovo.securesms.util.AbstractCursorLoader;

public class BlockedContactsLoader extends AbstractCursorLoader {

  public BlockedContactsLoader(Context context) {
    super(context);
  }

  @Override
  public Cursor getCursor() {
    return DatabaseFactory.getRecipientPreferenceDatabase(getContext())
                          .getBlocked();
  }

}
