package ua.org.slovo.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.CursorLoader;

import ua.org.slovo.securesms.database.DatabaseFactory;

public class IdentityLoader extends CursorLoader {

  private final Context context;

  public IdentityLoader(Context context) {
    super(context);
    this.context      = context.getApplicationContext();
  }

  @Override
  public Cursor loadInBackground() {
    return DatabaseFactory.getIdentityDatabase(context).getIdentities();
  }

}
