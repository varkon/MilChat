package ua.org.slovo.securesms.dependencies;

import android.content.Context;

import ua.org.slovo.securesms.BuildConfig;
import ua.org.slovo.securesms.DeviceListFragment;
import ua.org.slovo.securesms.crypto.storage.SignalProtocolStoreImpl;
import ua.org.slovo.securesms.jobs.AttachmentDownloadJob;
import ua.org.slovo.securesms.jobs.CleanPreKeysJob;
import ua.org.slovo.securesms.jobs.CreateSignedPreKeyJob;
import ua.org.slovo.securesms.jobs.DeliveryReceiptJob;
import ua.org.slovo.securesms.jobs.GcmRefreshJob;
import ua.org.slovo.securesms.jobs.MultiDeviceContactUpdateJob;
import ua.org.slovo.securesms.jobs.MultiDeviceGroupUpdateJob;
import ua.org.slovo.securesms.jobs.MultiDeviceReadUpdateJob;
import ua.org.slovo.securesms.jobs.PushGroupSendJob;
import ua.org.slovo.securesms.jobs.PushMediaSendJob;
import ua.org.slovo.securesms.jobs.PushNotificationReceiveJob;
import ua.org.slovo.securesms.jobs.PushTextSendJob;
import ua.org.slovo.securesms.jobs.RefreshAttributesJob;
import ua.org.slovo.securesms.jobs.RefreshPreKeysJob;
import ua.org.slovo.securesms.push.SecurityEventListener;
import ua.org.slovo.securesms.push.TextSecurePushTrustStore;
import ua.org.slovo.securesms.service.MessageRetrievalService;
import ua.org.slovo.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.util.CredentialsProvider;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {CleanPreKeysJob.class,
                                     CreateSignedPreKeyJob.class,
                                     DeliveryReceiptJob.class,
                                     PushGroupSendJob.class,
                                     PushTextSendJob.class,
                                     PushMediaSendJob.class,
                                     AttachmentDownloadJob.class,
                                     RefreshPreKeysJob.class,
                                     MessageRetrievalService.class,
                                     PushNotificationReceiveJob.class,
                                     MultiDeviceContactUpdateJob.class,
                                     MultiDeviceGroupUpdateJob.class,
                                     MultiDeviceReadUpdateJob.class,
                                     DeviceListFragment.class,
                                     RefreshAttributesJob.class,
                                     GcmRefreshJob.class})
public class TextSecureCommunicationModule {

  private final Context context;

  public TextSecureCommunicationModule(Context context) {
    this.context = context;
  }

  @Provides SignalServiceAccountManager provideTextSecureAccountManager() {
    return new SignalServiceAccountManager(BuildConfig.TEXTSECURE_URL,
                                           new TextSecurePushTrustStore(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           BuildConfig.USER_AGENT);
  }

  @Provides TextSecureMessageSenderFactory provideTextSecureMessageSenderFactory() {
    return new TextSecureMessageSenderFactory() {
      @Override
      public SignalServiceMessageSender create() {
        return new SignalServiceMessageSender(BuildConfig.TEXTSECURE_URL,
                                              new TextSecurePushTrustStore(context),
                                              TextSecurePreferences.getLocalNumber(context),
                                              TextSecurePreferences.getPushServerPassword(context),
                                              new SignalProtocolStoreImpl(context),
                                              BuildConfig.USER_AGENT,
                                              Optional.<SignalServiceMessageSender.EventListener>of(new SecurityEventListener(context)));
      }
    };
  }

  @Provides SignalServiceMessageReceiver provideTextSecureMessageReceiver() {
    return new SignalServiceMessageReceiver(BuildConfig.TEXTSECURE_URL,
                                         new TextSecurePushTrustStore(context),
                                         new DynamicCredentialsProvider(context),
                                         BuildConfig.USER_AGENT);
  }

  public static interface TextSecureMessageSenderFactory {
    public SignalServiceMessageSender create();
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public String getUser() {
      return TextSecurePreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return TextSecurePreferences.getSignalingKey(context);
    }
  }

}
