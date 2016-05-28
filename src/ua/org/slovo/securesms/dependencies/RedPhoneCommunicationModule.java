package ua.org.slovo.securesms.dependencies;

import android.content.Context;

import ua.org.slovo.redphone.signaling.RedPhoneAccountManager;
import ua.org.slovo.redphone.signaling.RedPhoneTrustStore;
import ua.org.slovo.securesms.BuildConfig;
import ua.org.slovo.securesms.jobs.GcmRefreshJob;
import ua.org.slovo.securesms.jobs.RefreshAttributesJob;
import ua.org.slovo.securesms.util.TextSecurePreferences;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {GcmRefreshJob.class,
                                     RefreshAttributesJob.class})
public class RedPhoneCommunicationModule {

  private final Context context;

  public RedPhoneCommunicationModule(Context context) {
    this.context = context;
  }

  @Provides RedPhoneAccountManager provideRedPhoneAccountManager() {
    return new RedPhoneAccountManager(BuildConfig.REDPHONE_MASTER_URL,
                                      new RedPhoneTrustStore(context),
                                      TextSecurePreferences.getLocalNumber(context),
                                      TextSecurePreferences.getPushServerPassword(context));
  }

}
