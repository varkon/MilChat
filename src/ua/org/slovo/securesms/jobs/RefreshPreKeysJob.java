package ua.org.slovo.securesms.jobs;

import android.content.Context;
import android.util.Log;

import ua.org.slovo.securesms.ApplicationContext;
import ua.org.slovo.securesms.crypto.IdentityKeyUtil;
import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.crypto.PreKeyUtil;
import ua.org.slovo.securesms.dependencies.InjectableType;
import ua.org.slovo.securesms.jobs.requirements.MasterSecretRequirement;
import ua.org.slovo.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

public class RefreshPreKeysJob extends MasterSecretJob implements InjectableType {

  private static final String TAG = RefreshPreKeysJob.class.getSimpleName();

  private static final int PREKEY_MINIMUM = 10;

  @Inject transient SignalServiceAccountManager accountManager;

  public RefreshPreKeysJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withGroupId(RefreshPreKeysJob.class.getSimpleName())
                                .withRequirement(new NetworkRequirement(context))
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRetryCount(5)
                                .create());
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onRun(MasterSecret masterSecret) throws IOException {
    if (!TextSecurePreferences.isPushRegistered(context)) return;

    int availableKeys = accountManager.getPreKeysCount();

    if (availableKeys >= PREKEY_MINIMUM && TextSecurePreferences.isSignedPreKeyRegistered(context)) {
      Log.w(TAG, "Available keys sufficient: " + availableKeys);
      return;
    }

    List<PreKeyRecord> preKeyRecords       = PreKeyUtil.generatePreKeys(context);
    PreKeyRecord       lastResortKeyRecord = PreKeyUtil.generateLastResortKey(context);
    IdentityKeyPair    identityKey         = IdentityKeyUtil.getIdentityKeyPair(context);
    SignedPreKeyRecord signedPreKeyRecord  = PreKeyUtil.generateSignedPreKey(context, identityKey);

    Log.w(TAG, "Registering new prekeys...");

    accountManager.setPreKeys(identityKey.getPublicKey(), lastResortKeyRecord, signedPreKeyRecord, preKeyRecords);

    TextSecurePreferences.setSignedPreKeyRegistered(context, true);

    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new CleanPreKeysJob(context));
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof NonSuccessfulResponseCodeException) return false;
    if (exception instanceof PushNetworkException)               return true;

    return false;
  }

  @Override
  public void onCanceled() {

  }

}
