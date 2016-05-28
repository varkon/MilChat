package ua.org.slovo.securesms.push;

import android.content.Context;

import ua.org.slovo.securesms.R;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;

import java.io.InputStream;

public class TextSecurePushTrustStore implements TrustStore {

  private final Context context;

  public TextSecurePushTrustStore(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public InputStream getKeyStoreInputStream() {
    return context.getResources().openRawResource(R.raw.whisper);
  }

  @Override
  public String getKeyStorePassword() {
    return "whisper";
  }
}
