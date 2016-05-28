package ua.org.slovo.securesms.service;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;

import ua.org.slovo.securesms.crypto.MasterSecret;

public abstract class MasterSecretIntentService extends IntentService {

  public MasterSecretIntentService(String name) {
    super(name);
  }

  @Override
  protected final void onHandleIntent(Intent intent) {
    onHandleIntent(intent, KeyCachingService.getMasterSecret(this));
  }

  protected abstract void onHandleIntent(Intent intent, @Nullable MasterSecret masterSecret);
}
