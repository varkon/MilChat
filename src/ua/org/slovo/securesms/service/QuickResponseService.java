package ua.org.slovo.securesms.service;

import android.content.Intent;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import ua.org.slovo.securesms.R;
import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.database.DatabaseFactory;
import ua.org.slovo.securesms.database.RecipientPreferenceDatabase.RecipientsPreferences;
import ua.org.slovo.securesms.database.ThreadDatabase;
import ua.org.slovo.securesms.mms.OutgoingMediaMessage;
import ua.org.slovo.securesms.mms.SlideDeck;
import ua.org.slovo.securesms.recipients.RecipientFactory;
import ua.org.slovo.securesms.recipients.Recipients;
import ua.org.slovo.securesms.sms.MessageSender;
import ua.org.slovo.securesms.sms.OutgoingTextMessage;
import ua.org.slovo.securesms.util.Rfc5724Uri;
import org.whispersystems.libsignal.util.guava.Optional;

import java.net.URISyntaxException;
import java.net.URLDecoder;

public class QuickResponseService extends MasterSecretIntentService {

  private static final String TAG = QuickResponseService.class.getSimpleName();

  public QuickResponseService() {
    super("QuickResponseService");
  }

  @Override
  protected void onHandleIntent(Intent intent, @Nullable MasterSecret masterSecret) {
    if (!TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(intent.getAction())) {
      Log.w(TAG, "Received unknown intent: " + intent.getAction());
      return;
    }

    if (masterSecret == null) {
      Log.w(TAG, "Got quick response request when locked...");
      Toast.makeText(this, R.string.QuickResponseService_quick_response_unavailable_when_Signal_is_locked, Toast.LENGTH_LONG).show();
      return;
    }

    try {
      Rfc5724Uri uri        = new Rfc5724Uri(intent.getDataString());
      String     content    = intent.getStringExtra(Intent.EXTRA_TEXT);
      String     numbers    = uri.getPath();
      if(numbers.contains("%")){
        numbers = URLDecoder.decode(numbers);
      }

      Recipients                      recipients     = RecipientFactory.getRecipientsFromString(this, numbers, false);
      Optional<RecipientsPreferences> preferences    = DatabaseFactory.getRecipientPreferenceDatabase(this).getRecipientsPreferences(recipients.getIds());
      int                             subscriptionId = preferences.isPresent() ? preferences.get().getDefaultSubscriptionId().or(-1) : -1;

      if (!TextUtils.isEmpty(content)) {
        if (recipients.isSingleRecipient()) {
          MessageSender.send(this, masterSecret, new OutgoingTextMessage(recipients, content, subscriptionId), -1, false);
        } else {
          MessageSender.send(this, masterSecret, new OutgoingMediaMessage(recipients, new SlideDeck(), content, System.currentTimeMillis(),
                                                                          subscriptionId, ThreadDatabase.DistributionTypes.DEFAULT), -1, false);
        }
      }
    } catch (URISyntaxException e) {
      Toast.makeText(this, R.string.QuickResponseService_problem_sending_message, Toast.LENGTH_LONG).show();
      Log.w(TAG, e);
    }
  }
}
