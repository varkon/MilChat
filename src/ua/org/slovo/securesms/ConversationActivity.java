/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ua.org.slovo.securesms;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.view.WindowCompat;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.protobuf.ByteString;

import ua.org.slovo.redphone.RedPhone;
import ua.org.slovo.redphone.RedPhoneService;
import ua.org.slovo.securesms.TransportOptions.OnTransportChangedListener;
import ua.org.slovo.securesms.audio.AudioRecorder;
import ua.org.slovo.securesms.audio.AudioSlidePlayer;
import ua.org.slovo.securesms.color.MaterialColor;
import ua.org.slovo.securesms.components.AnimatingToggle;
import ua.org.slovo.securesms.components.AttachmentTypeSelector;
import ua.org.slovo.securesms.components.ComposeText;
import ua.org.slovo.securesms.components.HidingLinearLayout;
import ua.org.slovo.securesms.components.InputAwareLayout;
import ua.org.slovo.securesms.components.InputPanel;
import ua.org.slovo.securesms.components.KeyboardAwareLinearLayout.OnKeyboardShownListener;
import ua.org.slovo.securesms.components.SendButton;
import ua.org.slovo.securesms.components.camera.QuickAttachmentDrawer;
import ua.org.slovo.securesms.components.camera.QuickAttachmentDrawer.AttachmentDrawerListener;
import ua.org.slovo.securesms.components.camera.QuickAttachmentDrawer.DrawerState;
import ua.org.slovo.securesms.components.emoji.EmojiDrawer;
import ua.org.slovo.securesms.components.location.SignalPlace;
import ua.org.slovo.securesms.components.reminder.InviteReminder;
import ua.org.slovo.securesms.components.reminder.ReminderView;
import ua.org.slovo.securesms.contacts.ContactAccessor;
import ua.org.slovo.securesms.contacts.ContactAccessor.ContactData;
import ua.org.slovo.securesms.crypto.MasterCipher;
import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.crypto.SecurityEvent;
import ua.org.slovo.securesms.database.DatabaseFactory;
import ua.org.slovo.securesms.database.DraftDatabase;
import ua.org.slovo.securesms.database.DraftDatabase.Draft;
import ua.org.slovo.securesms.database.DraftDatabase.Drafts;
import ua.org.slovo.securesms.database.GroupDatabase;
import ua.org.slovo.securesms.database.MessagingDatabase;
import ua.org.slovo.securesms.database.MessagingDatabase.SyncMessageId;
import ua.org.slovo.securesms.database.MmsSmsColumns.Types;
import ua.org.slovo.securesms.database.RecipientPreferenceDatabase.RecipientsPreferences;
import ua.org.slovo.securesms.database.ThreadDatabase;
import ua.org.slovo.securesms.jobs.MultiDeviceReadUpdateJob;
import ua.org.slovo.securesms.mms.AttachmentManager;
import ua.org.slovo.securesms.mms.AttachmentManager.MediaType;
import ua.org.slovo.securesms.mms.AttachmentTypeSelectorAdapter;
import ua.org.slovo.securesms.mms.AudioSlide;
import ua.org.slovo.securesms.mms.LocationSlide;
import ua.org.slovo.securesms.mms.MediaConstraints;
import ua.org.slovo.securesms.mms.OutgoingGroupMediaMessage;
import ua.org.slovo.securesms.mms.OutgoingMediaMessage;
import ua.org.slovo.securesms.mms.OutgoingSecureMediaMessage;
import ua.org.slovo.securesms.mms.Slide;
import ua.org.slovo.securesms.mms.SlideDeck;
import ua.org.slovo.securesms.notifications.MessageNotifier;
import ua.org.slovo.securesms.providers.PersistentBlobProvider;
import ua.org.slovo.securesms.recipients.Recipient;
import ua.org.slovo.securesms.recipients.RecipientFactory;
import ua.org.slovo.securesms.recipients.RecipientFormattingException;
import ua.org.slovo.securesms.recipients.Recipients;
import ua.org.slovo.securesms.recipients.Recipients.RecipientsModifiedListener;
import ua.org.slovo.securesms.service.KeyCachingService;
import ua.org.slovo.securesms.sms.MessageSender;
import ua.org.slovo.securesms.sms.OutgoingEncryptedMessage;
import ua.org.slovo.securesms.sms.OutgoingEndSessionMessage;
import ua.org.slovo.securesms.sms.OutgoingTextMessage;
import ua.org.slovo.securesms.util.CharacterCalculator.CharacterState;
import ua.org.slovo.securesms.util.Dialogs;
import ua.org.slovo.securesms.util.DirectoryHelper;
import ua.org.slovo.securesms.util.DirectoryHelper.UserCapabilities;
import ua.org.slovo.securesms.util.DirectoryHelper.UserCapabilities.Capability;
import ua.org.slovo.securesms.util.DynamicLanguage;
import ua.org.slovo.securesms.util.DynamicTheme;
import ua.org.slovo.securesms.util.GroupUtil;
import ua.org.slovo.securesms.util.MediaUtil;
import ua.org.slovo.securesms.util.TextSecurePreferences;
import ua.org.slovo.securesms.util.Util;
import ua.org.slovo.securesms.util.ViewUtil;
import ua.org.slovo.securesms.util.concurrent.AssertedSuccessListener;
import ua.org.slovo.securesms.util.concurrent.ListenableFuture;
import ua.org.slovo.securesms.util.concurrent.SettableFuture;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.ExecutionException;

import ws.com.google.android.mms.ContentType;

import static ua.org.slovo.securesms.TransportOption.Type;
import static ua.org.slovo.securesms.database.GroupDatabase.GroupRecord;
import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

/**
 * Activity for displaying a message thread, as well as
 * composing/sending a new message into that thread.
 *
 * @author Moxie Marlinspike
 *
 */
public class ConversationActivity extends PassphraseRequiredActionBarActivity
    implements ConversationFragment.ConversationFragmentListener,
               AttachmentManager.AttachmentListener,
               RecipientsModifiedListener,
               OnKeyboardShownListener,
               AttachmentDrawerListener,
               InputPanel.Listener
{
  private static final String TAG = ConversationActivity.class.getSimpleName();

  public static final String RECIPIENTS_EXTRA        = "recipients";
  public static final String THREAD_ID_EXTRA         = "thread_id";
  public static final String IS_ARCHIVED_EXTRA       = "is_archived";
  public static final String TEXT_EXTRA              = "draft_text";
  public static final String DISTRIBUTION_TYPE_EXTRA = "distribution_type";

  private static final int PICK_IMAGE        = 1;
  private static final int PICK_VIDEO        = 2;
  private static final int PICK_AUDIO        = 3;
  private static final int PICK_CONTACT_INFO = 4;
  private static final int GROUP_EDIT        = 5;
  private static final int TAKE_PHOTO        = 6;
  private static final int ADD_CONTACT       = 7;
  private static final int PICK_LOCATION     = 8;

  private   MasterSecret          masterSecret;
  protected ComposeText           composeText;
  private   AnimatingToggle       buttonToggle;
  private   SendButton            sendButton;
  private   ImageButton           attachButton;
  protected ConversationTitleView titleView;
  private   TextView              charactersLeft;
  private   ConversationFragment  fragment;
  private   Button                unblockButton;
  private   InputAwareLayout      container;
  private   View                  composePanel;
  protected ReminderView          reminderView;

  private   AttachmentTypeSelector attachmentTypeSelector;
  private   AttachmentManager      attachmentManager;
  private   AudioRecorder          audioRecorder;
  private   BroadcastReceiver      securityUpdateReceiver;
  private   BroadcastReceiver      groupUpdateReceiver;
  private   EmojiDrawer            emojiDrawer;
  protected HidingLinearLayout     quickAttachmentToggle;
  private   QuickAttachmentDrawer  quickAttachmentDrawer;
  private   InputPanel             inputPanel;

  private Recipients recipients;
  private long       threadId;
  private int        distributionType;
  private boolean    archived;
  private boolean    isSecureText;
  private boolean    isSecureVoice;
  private boolean    isMmsEnabled = true;

  private DynamicTheme    dynamicTheme    = new DynamicTheme();
  private DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
    Log.w(TAG, "onCreate()");
    this.masterSecret = masterSecret;

    supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY);
    setContentView(R.layout.conversation_activity);

    fragment = initFragment(R.id.fragment_content, new ConversationFragment(),
                            masterSecret, dynamicLanguage.getCurrentLocale());

    initializeReceivers();
    initializeActionBar();
    initializeViews();
    initializeResources();
    initializeSecurity(false, false).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        initializeDraft();
      }
    });
  }

  @Override
  protected void onNewIntent(Intent intent) {
    Log.w(TAG, "onNewIntent()");
    
    if (isFinishing()) {
      Log.w(TAG, "Activity is finishing...");
      return;
    }

    if (!Util.isEmpty(composeText) || attachmentManager.isAttachmentPresent()) {
      saveDraft();
      attachmentManager.clear();
      composeText.setText("");
    }

    setIntent(intent);
    initializeResources();
    initializeSecurity(false, false).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        initializeDraft();
      }
    });

    if (fragment != null) {
      fragment.onNewIntent();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    quickAttachmentDrawer.onResume();

    initializeEnabledCheck();
    initializeMmsEnabledCheck();
    composeText.setTransport(sendButton.getSelectedTransport());

    titleView.setTitle(recipients);
    setActionBarColor(recipients.getColor());
    setBlockedUserState(recipients);
    calculateCharactersRemaining();

    MessageNotifier.setVisibleThread(threadId);
    markThreadAsRead();
  }

  @Override
  protected void onPause() {
    super.onPause();
    MessageNotifier.setVisibleThread(-1L);
    if (isFinishing()) overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_right);
    quickAttachmentDrawer.onPause();
    inputPanel.onPause();

    AudioSlidePlayer.stopAll();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    Log.w(TAG, "onConfigurationChanged(" + newConfig.orientation + ")");
    super.onConfigurationChanged(newConfig);
    composeText.setTransport(sendButton.getSelectedTransport());
    quickAttachmentDrawer.onConfigurationChanged();
    if (container.getCurrentInput() == emojiDrawer) container.hideAttachedInput(true);
  }

  @Override
  protected void onDestroy() {
    saveDraft();
    if (recipients != null) recipients.removeListener(this);
    if (securityUpdateReceiver != null) unregisterReceiver(securityUpdateReceiver);
    if (groupUpdateReceiver != null) unregisterReceiver(groupUpdateReceiver);
    super.onDestroy();
  }

  @Override
  public void onActivityResult(final int reqCode, int resultCode, Intent data) {
    Log.w(TAG, "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);

    if (data == null && reqCode != TAKE_PHOTO || resultCode != RESULT_OK) return;

    switch (reqCode) {
    case PICK_IMAGE:
      boolean isGif = MediaUtil.isGif(MediaUtil.getMimeType(this, data.getData()));
      setMedia(data.getData(), isGif ? MediaType.GIF : MediaType.IMAGE);
      break;
    case PICK_VIDEO:
      setMedia(data.getData(), MediaType.VIDEO);
      break;
    case PICK_AUDIO:
      setMedia(data.getData(), MediaType.AUDIO);
      break;
    case PICK_CONTACT_INFO:
      addAttachmentContactInfo(data.getData());
      break;
    case GROUP_EDIT:
      recipients = RecipientFactory.getRecipientsForIds(this, data.getLongArrayExtra(GroupCreateActivity.GROUP_RECIPIENT_EXTRA), true);
      recipients.addListener(this);
      titleView.setTitle(recipients);
      setBlockedUserState(recipients);
      supportInvalidateOptionsMenu();
      break;
    case TAKE_PHOTO:
      if (attachmentManager.getCaptureUri() != null) {
        setMedia(attachmentManager.getCaptureUri(), MediaType.IMAGE);
      }
      break;
    case ADD_CONTACT:
      recipients = RecipientFactory.getRecipientsForIds(ConversationActivity.this, recipients.getIds(), true);
      recipients.addListener(this);
      fragment.reloadList();
      break;
    case PICK_LOCATION:
      SignalPlace place = new SignalPlace(PlacePicker.getPlace(data, this));
      attachmentManager.setLocation(masterSecret, place, getCurrentMediaConstraints());
      break;
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    if (isSingleConversation()) {
      if (isSecureVoice) inflater.inflate(R.menu.conversation_callable_secure, menu);
      else               inflater.inflate(R.menu.conversation_callable_insecure, menu);
    } else if (isGroupConversation()) {
      inflater.inflate(R.menu.conversation_group_options, menu);

      if (!isPushGroupConversation()) {
        inflater.inflate(R.menu.conversation_mms_group_options, menu);
        if (distributionType == ThreadDatabase.DistributionTypes.BROADCAST) {
          menu.findItem(R.id.menu_distribution_broadcast).setChecked(true);
        } else {
          menu.findItem(R.id.menu_distribution_conversation).setChecked(true);
        }
      } else if (isActiveGroup()) {
        inflater.inflate(R.menu.conversation_push_group_options, menu);
      }
    }

    inflater.inflate(R.menu.conversation, menu);

    if (isSingleConversation() && isSecureText) {
      inflater.inflate(R.menu.conversation_secure, menu);
    } else if (isSingleConversation()) {
      inflater.inflate(R.menu.conversation_insecure, menu);
    }

    if (recipients != null && recipients.isMuted()) inflater.inflate(R.menu.conversation_muted, menu);
    else                                            inflater.inflate(R.menu.conversation_unmuted, menu);

    if (isSingleConversation() && getRecipients().getPrimaryRecipient().getContactUri() == null) {
      inflater.inflate(R.menu.conversation_add_to_contacts, menu);
    }

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_call_secure:
    case R.id.menu_call_insecure:             handleDial(getRecipients().getPrimaryRecipient()); return true;
    case R.id.menu_add_attachment:            handleAddAttachment();                             return true;
    case R.id.menu_view_media:                handleViewMedia();                                 return true;
    case R.id.menu_add_to_contacts:           handleAddToContacts();                             return true;
    case R.id.menu_reset_secure_session:      handleResetSecureSession();                        return true;
    case R.id.menu_group_recipients:          handleDisplayGroupRecipients();                    return true;
    case R.id.menu_distribution_broadcast:    handleDistributionBroadcastEnabled(item);          return true;
    case R.id.menu_distribution_conversation: handleDistributionConversationEnabled(item);       return true;
    case R.id.menu_edit_group:                handleEditPushGroup();                             return true;
    case R.id.menu_leave:                     handleLeavePushGroup();                            return true;
    case R.id.menu_invite:                    handleInviteLink();                                return true;
    case R.id.menu_mute_notifications:        handleMuteNotifications();                         return true;
    case R.id.menu_unmute_notifications:      handleUnmuteNotifications();                       return true;
    case R.id.menu_conversation_settings:     handleConversationSettings();                      return true;
    case android.R.id.home:                   handleReturnToConversationList();                  return true;
    }

    return false;
  }

  @Override
  public void onBackPressed() {
    Log.w(TAG, "onBackPressed()");
    if (container.isInputOpen()) container.hideCurrentInput(composeText);
    else                         super.onBackPressed();
  }

  @Override
  public void onKeyboardShown() {
    inputPanel.onKeyboardShown();
  }

  //////// Event Handlers

  private void handleReturnToConversationList() {
    Intent intent = new Intent(this, (archived ? ConversationListArchiveActivity.class : ConversationListActivity.class));
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startActivity(intent);
    finish();
  }

  private void handleMuteNotifications() {
    MuteDialog.show(this, new MuteDialog.MuteSelectionListener() {
      @Override
      public void onMuted(final long until) {
        recipients.setMuted(until);

        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                           .setMuted(recipients, until);

            return null;
          }
        }.execute();
      }
    });
  }

  private void handleConversationSettings() {
    titleView.performClick();
  }

  private void handleUnmuteNotifications() {
    recipients.setMuted(0);

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                       .setMuted(recipients, 0);

        return null;
      }
    }.execute();
  }

  private void handleUnblock() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.ConversationActivity_unblock_question)
        .setMessage(R.string.ConversationActivity_are_you_sure_you_want_to_unblock_this_contact)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.ConversationActivity_unblock, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            recipients.setBlocked(false);

            new AsyncTask<Void, Void, Void>() {
              @Override
              protected Void doInBackground(Void... params) {
                DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                               .setBlocked(recipients, false);
                return null;
              }
            }.execute();
          }
        }).show();
  }

  private void handleInviteLink() {
    try {
      boolean a = SecureRandom.getInstance("SHA1PRNG").nextBoolean();
      if (a) composeText.appendInvite(getString(R.string.ConversationActivity_lets_switch_to_signal, "http://sgnl.link/1LoIMUl"));
      else   composeText.appendInvite(getString(R.string.ConversationActivity_lets_use_this_to_chat, "http://sgnl.link/1MF56H1"));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private void handleResetSecureSession() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_reset_secure_session_question);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationActivity_this_may_help_if_youre_having_encryption_problems);
    builder.setPositiveButton(R.string.ConversationActivity_reset, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        if (isSingleConversation()) {
          final Context context = getApplicationContext();

          OutgoingEndSessionMessage endSessionMessage =
              new OutgoingEndSessionMessage(new OutgoingTextMessage(getRecipients(), "TERMINATE", -1));

          new AsyncTask<OutgoingEndSessionMessage, Void, Long>() {
            @Override
            protected Long doInBackground(OutgoingEndSessionMessage... messages) {
              return MessageSender.send(context, masterSecret, messages[0], threadId, false);
            }

            @Override
            protected void onPostExecute(Long result) {
              sendComplete(result);
            }
          }.execute(endSessionMessage);
        }
      }
    });
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private void handleViewMedia() {
    Intent intent = new Intent(this, MediaOverviewActivity.class);
    intent.putExtra(MediaOverviewActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(MediaOverviewActivity.RECIPIENT_EXTRA, recipients.getPrimaryRecipient().getRecipientId());
    startActivity(intent);
  }

  private void handleLeavePushGroup() {
    if (getRecipients() == null) {
      Toast.makeText(this, getString(R.string.ConversationActivity_invalid_recipient),
                     Toast.LENGTH_LONG).show();
      return;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(getString(R.string.ConversationActivity_leave_group));
    builder.setIconAttribute(R.attr.dialog_info_icon);
    builder.setCancelable(true);
    builder.setMessage(getString(R.string.ConversationActivity_are_you_sure_you_want_to_leave_this_group));
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        Context self = ConversationActivity.this;
        try {
          byte[] groupId = GroupUtil.getDecodedId(getRecipients().getPrimaryRecipient().getNumber());
          DatabaseFactory.getGroupDatabase(self).setActive(groupId, false);

          GroupContext context = GroupContext.newBuilder()
                                             .setId(ByteString.copyFrom(groupId))
                                             .setType(GroupContext.Type.QUIT)
                                             .build();

          OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(getRecipients(), context, null, System.currentTimeMillis());
          MessageSender.send(self, masterSecret, outgoingMessage, threadId, false);
          DatabaseFactory.getGroupDatabase(self).remove(groupId, TextSecurePreferences.getLocalNumber(self));
          initializeEnabledCheck();
        } catch (IOException e) {
          Log.w(TAG, e);
          Toast.makeText(self, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show();
        }
      }
    });

    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleEditPushGroup() {
    Intent intent = new Intent(ConversationActivity.this, GroupCreateActivity.class);
    intent.putExtra(GroupCreateActivity.GROUP_RECIPIENT_EXTRA, recipients.getPrimaryRecipient().getRecipientId());
    startActivityForResult(intent, GROUP_EDIT);
  }

  private void handleDistributionBroadcastEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.BROADCAST;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.BROADCAST);
          return null;
        }
      }.execute();
    }
  }

  private void handleDistributionConversationEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.CONVERSATION;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.CONVERSATION);
          return null;
        }
      }.execute();
    }
  }

  private void handleDial(final Recipient recipient) {
    if (recipient == null) return;

    if (isSecureVoice) {
      Intent intent = new Intent(this, RedPhoneService.class);
      intent.setAction(RedPhoneService.ACTION_OUTGOING_CALL);
      intent.putExtra(RedPhoneService.EXTRA_REMOTE_NUMBER, recipient.getNumber());
      startService(intent);

      Intent activityIntent = new Intent(this, RedPhone.class);
      activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(activityIntent);
    } else {
      try {
        Intent dialIntent = new Intent(Intent.ACTION_DIAL,
                                       Uri.parse("tel:" + recipient.getNumber()));
        startActivity(dialIntent);
      } catch (ActivityNotFoundException anfe) {
        Log.w(TAG, anfe);
        Dialogs.showAlertDialog(this,
                                getString(R.string.ConversationActivity_calls_not_supported),
                                getString(R.string.ConversationActivity_this_device_does_not_appear_to_support_dial_actions));
      }
    }
  }

  private void handleDisplayGroupRecipients() {
    new GroupMembersDialog(this, getRecipients()).display();
  }

  private void handleAddToContacts() {
    try {
      final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
      intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipients.getPrimaryRecipient().getNumber());
      intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
      startActivityForResult(intent, ADD_CONTACT);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
    }
  }

  private void handleAddAttachment() {
    if (this.isMmsEnabled || isSecureText) {
      attachmentTypeSelector.show(this, attachButton);
    } else {
      handleManualMmsRequired();
    }
  }

  private void handleManualMmsRequired() {
    Toast.makeText(this, R.string.MmsDownloader_error_reading_mms_settings, Toast.LENGTH_LONG).show();

    Intent intent = new Intent(this, PromptMmsActivity.class);
    intent.putExtras(getIntent().getExtras());
    startActivity(intent);
  }

  private void handleSecurityChange(boolean isSecureText, boolean isSecureVoice) {
    this.isSecureText  = isSecureText;
    this.isSecureVoice = isSecureVoice;

    boolean isMediaMessage = !recipients.isSingleRecipient() || attachmentManager.isAttachmentPresent();

    sendButton.resetAvailableTransports(isMediaMessage);

    if (!isSecureText)                 sendButton.disableTransport(Type.TEXTSECURE);
    if (recipients.isGroupRecipient()) sendButton.disableTransport(Type.SMS);

    if (isSecureText) sendButton.setDefaultTransport(Type.TEXTSECURE);
    else              sendButton.setDefaultTransport(Type.SMS);

    calculateCharactersRemaining();
    supportInvalidateOptionsMenu();
  }

  ///// Initializers

  private void initializeDraft() {
    final String    draftText      = getIntent().getStringExtra(TEXT_EXTRA);
    final Uri       draftMedia     = getIntent().getData();
    final MediaType draftMediaType = MediaType.from(getIntent().getType());

    if (draftText != null)                            composeText.setText(draftText);
    if (draftMedia != null && draftMediaType != null) setMedia(draftMedia, draftMediaType);

    if (draftText == null && draftMedia == null && draftMediaType == null) {
      initializeDraftFromDatabase();
    } else {
      updateToggleButtonState();
    }
  }

  private void initializeEnabledCheck() {
    boolean enabled = !(isPushGroupConversation() && !isActiveGroup());
    inputPanel.setEnabled(enabled);
    sendButton.setEnabled(enabled);
    attachButton.setEnabled(enabled);
  }

  private void initializeDraftFromDatabase() {
    new AsyncTask<Void, Void, List<Draft>>() {
      @Override
      protected List<Draft> doInBackground(Void... params) {
        MasterCipher masterCipher   = new MasterCipher(masterSecret);
        DraftDatabase draftDatabase = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        List<Draft> results         = draftDatabase.getDrafts(masterCipher, threadId);

        draftDatabase.clearDrafts(threadId);

        return results;
      }

      @Override
      protected void onPostExecute(List<Draft> drafts) {
        for (Draft draft : drafts) {
          try {
            if (draft.getType().equals(Draft.TEXT)) {
              composeText.setText(draft.getValue());
            } else if (draft.getType().equals(Draft.LOCATION)) {
              attachmentManager.setLocation(masterSecret, SignalPlace.deserialize(draft.getValue()), getCurrentMediaConstraints());
            } else if (draft.getType().equals(Draft.IMAGE)) {
              setMedia(Uri.parse(draft.getValue()), MediaType.IMAGE);
            } else if (draft.getType().equals(Draft.AUDIO)) {
              setMedia(Uri.parse(draft.getValue()), MediaType.AUDIO);
            } else if (draft.getType().equals(Draft.VIDEO)) {
              setMedia(Uri.parse(draft.getValue()), MediaType.VIDEO);
            }
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        updateToggleButtonState();
      }
    }.execute();
  }

  private ListenableFuture<Boolean> initializeSecurity(final boolean currentSecureText,
                                                       final boolean currentSecureVoice)
  {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    handleSecurityChange(currentSecureText || isPushGroupConversation(),
                         currentSecureVoice && !isGroupConversation());

    new AsyncTask<Recipients, Void, Pair<Boolean, Boolean>>() {
      @Override
      protected Pair<Boolean, Boolean> doInBackground(Recipients... params) {
        try {
          Context           context      = ConversationActivity.this;
          Recipients        recipients   = params[0];
          UserCapabilities  capabilities = DirectoryHelper.getUserCapabilities(context, recipients);

          if (capabilities.getTextCapability() == Capability.UNKNOWN ||
              capabilities.getVoiceCapability() == Capability.UNKNOWN)
          {
            capabilities = DirectoryHelper.refreshDirectoryFor(context, masterSecret, recipients,
                                                               TextSecurePreferences.getLocalNumber(context));
          }

          return new Pair<>(capabilities.getTextCapability() == Capability.SUPPORTED,
                            capabilities.getVoiceCapability() == Capability.SUPPORTED &&
                            !isSelfConversation());
        } catch (IOException e) {
          Log.w(TAG, e);
          return new Pair<>(false, false);
        }
      }

      @Override
      protected void onPostExecute(Pair<Boolean, Boolean> result) {
        if (result.first != currentSecureText || result.second != currentSecureVoice) {
          handleSecurityChange(result.first, result.second);
        }
        future.set(true);
        onSecurityUpdated();
      }
    }.execute(recipients);

    return future;
  }

  private void onSecurityUpdated() {
    updateRecipientPreferences();
  }

  private void updateRecipientPreferences() {
    if (recipients.getPrimaryRecipient() != null &&
        recipients.getPrimaryRecipient().getContactUri() != null)
    {
      new RecipientPreferencesTask().execute(recipients);
    }
  }

  protected void updateInviteReminder(boolean seenInvite) {
    Log.w(TAG, "updateInviteReminder(" + seenInvite+")");
    if (TextSecurePreferences.isPushRegistered(this) &&
        !isSecureText                                &&
        !seenInvite                                  &&
        recipients.isSingleRecipient())
    {
      InviteReminder reminder = new InviteReminder(this, recipients);
      reminder.setOkListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          handleInviteLink();
          reminderView.requestDismiss();
        }
      });
      reminderView.showReminder(reminder);
    } else {
      reminderView.hide();
    }
  }

  private void updateDefaultSubscriptionId(Optional<Integer> defaultSubscriptionId) {
    Log.w(TAG, "updateDefaultSubscriptionId(" + defaultSubscriptionId.orNull() + ")");
    sendButton.setDefaultSubscriptionId(defaultSubscriptionId);
  }

  private void initializeMmsEnabledCheck() {
    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Void... params) {
        return Util.isMmsCapable(ConversationActivity.this);
      }

      @Override
      protected void onPostExecute(Boolean isMmsEnabled) {
        ConversationActivity.this.isMmsEnabled = isMmsEnabled;
      }
    }.execute();
  }

  private void initializeViews() {
    titleView             = (ConversationTitleView) getSupportActionBar().getCustomView();
    buttonToggle          = ViewUtil.findById(this, R.id.button_toggle);
    sendButton            = ViewUtil.findById(this, R.id.send_button);
    attachButton          = ViewUtil.findById(this, R.id.attach_button);
    composeText           = ViewUtil.findById(this, R.id.embedded_text_editor);
    charactersLeft        = ViewUtil.findById(this, R.id.space_left);
    emojiDrawer           = ViewUtil.findById(this, R.id.emoji_drawer);
    unblockButton         = ViewUtil.findById(this, R.id.unblock_button);
    composePanel          = ViewUtil.findById(this, R.id.bottom_panel);
    container             = ViewUtil.findById(this, R.id.layout_container);
    reminderView          = ViewUtil.findById(this, R.id.reminder);
    quickAttachmentDrawer = ViewUtil.findById(this, R.id.quick_attachment_drawer);
    quickAttachmentToggle = ViewUtil.findById(this, R.id.quick_attachment_toggle);
    inputPanel            = ViewUtil.findById(this, R.id.bottom_panel);

    ImageButton quickCameraToggle = ViewUtil.findById(this, R.id.quick_camera_toggle);
    View        composeBubble     = ViewUtil.findById(this, R.id.compose_bubble);

    container.addOnKeyboardShownListener(this);
    inputPanel.setListener(this, emojiDrawer);

    int[]      attributes   = new int[]{R.attr.conversation_item_bubble_background};
    TypedArray colors       = obtainStyledAttributes(attributes);
    int        defaultColor = colors.getColor(0, Color.WHITE);
    composeBubble.getBackground().setColorFilter(defaultColor, PorterDuff.Mode.MULTIPLY);
    colors.recycle();

    attachmentTypeSelector = new AttachmentTypeSelector(this, new AttachmentTypeListener());
    attachmentManager      = new AttachmentManager(this, this);
    audioRecorder          = new AudioRecorder(this, masterSecret);

    SendButtonListener        sendButtonListener        = new SendButtonListener();
    ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

    emojiDrawer.setEmojiEventListener(inputPanel);
    composeText.setOnEditorActionListener(sendButtonListener);
    attachButton.setOnClickListener(new AttachButtonListener());
    attachButton.setOnLongClickListener(new AttachButtonLongClickListener());
    sendButton.setOnClickListener(sendButtonListener);
    sendButton.setEnabled(true);
    sendButton.addOnTransportChangedListener(new OnTransportChangedListener() {
      @Override
      public void onChange(TransportOption newTransport, boolean manuallySelected) {
        calculateCharactersRemaining();
        composeText.setTransport(newTransport);
        buttonToggle.getBackground().setColorFilter(newTransport.getBackgroundColor(), Mode.MULTIPLY);
        buttonToggle.getBackground().invalidateSelf();
        if (manuallySelected) recordSubscriptionIdPreference(newTransport.getSimSubscriptionId());
      }
    });

    titleView.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(ConversationActivity.this, RecipientPreferenceActivity.class);
        intent.putExtra(RecipientPreferenceActivity.RECIPIENTS_EXTRA, recipients.getIds());

        startActivitySceneTransition(intent, titleView.findViewById(R.id.title), "recipient_name");
      }
    });

    unblockButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        handleUnblock();
      }
    });

    composeText.setOnKeyListener(composeKeyPressedListener);
    composeText.addTextChangedListener(composeKeyPressedListener);
    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setOnClickListener(composeKeyPressedListener);
    composeText.setOnFocusChangeListener(composeKeyPressedListener);

    if (QuickAttachmentDrawer.isDeviceSupported(this)) {
      quickAttachmentDrawer.setListener(this);
      quickCameraToggle.setOnClickListener(new QuickCameraToggleListener());
    } else {
      quickCameraToggle.setVisibility(View.GONE);
      quickCameraToggle.setEnabled(false);
    }
  }

  protected void initializeActionBar() {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setCustomView(R.layout.conversation_title_view);
    getSupportActionBar().setDisplayShowCustomEnabled(true);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
  }

  private void initializeResources() {
    if (recipients != null) recipients.removeListener(this);
    
    recipients       = RecipientFactory.getRecipientsForIds(this, getIntent().getLongArrayExtra(RECIPIENTS_EXTRA), true);
    threadId         = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
    archived         = getIntent().getBooleanExtra(IS_ARCHIVED_EXTRA, false);
    distributionType = getIntent().getIntExtra(DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
      LinearLayout conversationContainer = ViewUtil.findById(this, R.id.conversation_container);
      conversationContainer.setClipChildren(true);
      conversationContainer.setClipToPadding(true);
    }

    recipients.addListener(this);
  }

  @Override
  public void onModified(final Recipients recipients) {
    titleView.post(new Runnable() {
      @Override
      public void run() {
        titleView.setTitle(recipients);
        setBlockedUserState(recipients);
        setActionBarColor(recipients.getColor());
        updateRecipientPreferences();
      }
    });
  }

  private void initializeReceivers() {
    securityUpdateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        initializeSecurity(isSecureText, isSecureVoice);
        calculateCharactersRemaining();
      }
    };

    groupUpdateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        Log.w("ConversationActivity", "Group update received...");
        if (recipients != null) {
          long[] ids = recipients.getIds();
          Log.w("ConversationActivity", "Looking up new recipients...");
          recipients = RecipientFactory.getRecipientsForIds(context, ids, true);
          recipients.addListener(ConversationActivity.this);
          titleView.setTitle(recipients);
        }
      }
    };

    registerReceiver(securityUpdateReceiver,
                     new IntentFilter(SecurityEvent.SECURITY_UPDATE_EVENT),
                     KeyCachingService.KEY_PERMISSION, null);

    registerReceiver(groupUpdateReceiver,
                     new IntentFilter(GroupDatabase.DATABASE_UPDATE_ACTION));
  }

  //////// Helper Methods

  private void addAttachment(int type) {
    Log.w("ComposeMessageActivity", "Selected: " + type);
    switch (type) {
    case AttachmentTypeSelectorAdapter.ADD_IMAGE:
      AttachmentManager.selectImage(this, PICK_IMAGE); break;
    case AttachmentTypeSelectorAdapter.ADD_VIDEO:
      AttachmentManager.selectVideo(this, PICK_VIDEO); break;
    case AttachmentTypeSelectorAdapter.ADD_SOUND:
      AttachmentManager.selectAudio(this, PICK_AUDIO); break;
    case AttachmentTypeSelectorAdapter.ADD_CONTACT_INFO:
      AttachmentManager.selectContactInfo(this, PICK_CONTACT_INFO); break;
    case AttachmentTypeSelector.ADD_LOCATION:
      AttachmentManager.selectLocation(this, PICK_LOCATION); break;
    case AttachmentTypeSelectorAdapter.TAKE_PHOTO:
      attachmentManager.capturePhoto(this, TAKE_PHOTO); break;
    }
  }

  private void setMedia(Uri uri, MediaType mediaType) {
    attachmentManager.setMedia(masterSecret, uri, mediaType, getCurrentMediaConstraints());
  }

  private void addAttachmentContactInfo(Uri contactUri) {
    ContactAccessor contactDataList = ContactAccessor.getInstance();
    ContactData contactData = contactDataList.getContactData(this, contactUri);

    if      (contactData.numbers.size() == 1) composeText.append(contactData.numbers.get(0).number);
    else if (contactData.numbers.size() > 1)  selectContactInfo(contactData);
  }

  private void selectContactInfo(ContactData contactData) {
    final CharSequence[] numbers     = new CharSequence[contactData.numbers.size()];
    final CharSequence[] numberItems = new CharSequence[contactData.numbers.size()];

    for (int i = 0; i < contactData.numbers.size(); i++) {
      numbers[i]     = contactData.numbers.get(i).number;
      numberItems[i] = contactData.numbers.get(i).type + ": " + contactData.numbers.get(i).number;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIconAttribute(R.attr.conversation_attach_contact_info);
    builder.setTitle(R.string.ConversationActivity_select_contact_info);

    builder.setItems(numberItems, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        composeText.append(numbers[which]);
      }
    });
    builder.show();
  }

  private Drafts getDraftsForCurrentState() {
    Drafts drafts = new Drafts();

    if (!Util.isEmpty(composeText)) {
      drafts.add(new Draft(Draft.TEXT, composeText.getText().toString()));
    }

    for (Slide slide : attachmentManager.buildSlideDeck().getSlides()) {
      if      (slide.hasAudio())    drafts.add(new Draft(Draft.AUDIO, slide.getUri().toString()));
      else if (slide.hasVideo())    drafts.add(new Draft(Draft.VIDEO, slide.getUri().toString()));
      else if (slide.hasLocation()) drafts.add(new Draft(Draft.LOCATION, ((LocationSlide)slide).getPlace().serialize()));
      else if (slide.hasImage())    drafts.add(new Draft(Draft.IMAGE, slide.getUri().toString()));
    }

    return drafts;
  }

  protected ListenableFuture<Long> saveDraft() {
    final SettableFuture<Long> future = new SettableFuture<>();

    if (this.recipients == null || this.recipients.isEmpty()) {
      future.set(threadId);
      return future;
    }

    final Drafts       drafts               = getDraftsForCurrentState();
    final long         thisThreadId         = this.threadId;
    final MasterSecret thisMasterSecret     = this.masterSecret.parcelClone();
    final int          thisDistributionType = this.distributionType;

    new AsyncTask<Long, Void, Long>() {
      @Override
      protected Long doInBackground(Long... params) {
        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(ConversationActivity.this);
        DraftDatabase  draftDatabase  = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        long           threadId       = params[0];

        if (drafts.size() > 0) {
          if (threadId == -1) threadId = threadDatabase.getThreadIdFor(getRecipients(), thisDistributionType);

          draftDatabase.insertDrafts(new MasterCipher(thisMasterSecret), threadId, drafts);
          threadDatabase.updateSnippet(threadId, drafts.getSnippet(ConversationActivity.this),
                                       drafts.getUriSnippet(ConversationActivity.this),
                                       System.currentTimeMillis(), Types.BASE_DRAFT_TYPE, true);
        } else if (threadId > 0) {
          threadDatabase.update(threadId, false);
        }

        return threadId;
      }

      @Override
      protected void onPostExecute(Long result) {
        future.set(result);
      }

    }.execute(thisThreadId);

    return future;
  }

  private void setActionBarColor(MaterialColor color) {
    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));
    setStatusBarColor(color.toStatusBarColor(this));
  }

  private void setBlockedUserState(Recipients recipients) {
    if (recipients.isBlocked()) {
      unblockButton.setVisibility(View.VISIBLE);
      composePanel.setVisibility(View.GONE);
    } else {
      composePanel.setVisibility(View.VISIBLE);
      unblockButton.setVisibility(View.GONE);
    }
  }

  private void calculateCharactersRemaining() {
    int             charactersSpent = composeText.getText().toString().length();
    TransportOption transportOption = sendButton.getSelectedTransport();
    CharacterState  characterState  = transportOption.calculateCharacters(charactersSpent);

    if (characterState.charactersRemaining <= 15 || characterState.messagesSpent > 1) {
      charactersLeft.setText(characterState.charactersRemaining + "/" + characterState.maxMessageSize
                                 + " (" + characterState.messagesSpent + ")");
      charactersLeft.setVisibility(View.VISIBLE);
    } else {
      charactersLeft.setVisibility(View.GONE);
    }
  }

  private boolean isSingleConversation() {
    return getRecipients() != null && getRecipients().isSingleRecipient() && !getRecipients().isGroupRecipient();
  }

  private boolean isActiveGroup() {
    if (!isGroupConversation()) return false;

    try {
      byte[]      groupId = GroupUtil.getDecodedId(getRecipients().getPrimaryRecipient().getNumber());
      GroupRecord record  = DatabaseFactory.getGroupDatabase(this).getGroup(groupId);

      return record != null && record.isActive();
    } catch (IOException e) {
      Log.w("ConversationActivity", e);
      return false;
    }
  }

  private boolean isSelfConversation() {
    if (!TextSecurePreferences.isPushRegistered(this))       return false;
    if (!recipients.isSingleRecipient())                     return false;
    if (recipients.getPrimaryRecipient().isGroupRecipient()) return false;

    return Util.isOwnNumber(this, recipients.getPrimaryRecipient().getNumber());
  }

  private boolean isGroupConversation() {
    return getRecipients() != null &&
        (!getRecipients().isSingleRecipient() || getRecipients().isGroupRecipient());
  }

  private boolean isPushGroupConversation() {
    return getRecipients() != null && getRecipients().isGroupRecipient();
  }

  protected Recipients getRecipients() {
    return this.recipients;
  }

  protected long getThreadId() {
    return this.threadId;
  }

  private String getMessage() throws InvalidMessageException {
    String rawText = composeText.getText().toString();

    if (rawText.length() < 1 && !attachmentManager.isAttachmentPresent())
      throw new InvalidMessageException(getString(R.string.ConversationActivity_message_is_empty_exclamation));

    return rawText;
  }

  private MediaConstraints getCurrentMediaConstraints() {
    return sendButton.getSelectedTransport().getType() == Type.TEXTSECURE
           ? MediaConstraints.PUSH_CONSTRAINTS
           : MediaConstraints.MMS_CONSTRAINTS;
  }

  private void markThreadAsRead() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        Context             context    = ConversationActivity.this;
        List<SyncMessageId> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(params[0]);

        MessageNotifier.updateNotification(context, masterSecret);

        if (!messageIds.isEmpty()) {
          ApplicationContext.getInstance(context).getJobManager().add(new MultiDeviceReadUpdateJob(context, messageIds));
        }

        return null;
      }
    }.execute(threadId);
  }

  protected void sendComplete(long threadId) {
    boolean refreshFragment = (threadId != this.threadId);
    this.threadId = threadId;

    if (fragment == null || !fragment.isVisible() || isFinishing()) {
      return;
    }

    if (refreshFragment) {
      fragment.reload(recipients, threadId);
      MessageNotifier.setVisibleThread(threadId);
    }

    fragment.scrollToBottom();
    attachmentManager.cleanup();
  }

  private void sendMessage() {
    try {
      Recipients recipients     = getRecipients();
      boolean    forceSms       = sendButton.isManualSelection() && sendButton.getSelectedTransport().isSms();
      int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);

      Log.w(TAG, "isManual Selection: " + sendButton.isManualSelection());
      Log.w(TAG, "forceSms: " + forceSms);

      if (recipients == null) {
        throw new RecipientFormattingException("Badly formatted");
      }

      if ((!recipients.isSingleRecipient() || recipients.isEmailRecipient()) && !isMmsEnabled) {
        handleManualMmsRequired();
      } else if (attachmentManager.isAttachmentPresent() || !recipients.isSingleRecipient() || recipients.isGroupRecipient() || recipients.isEmailRecipient()) {
        sendMediaMessage(forceSms, subscriptionId);
      } else {
        sendTextMessage(forceSms, subscriptionId);
      }
    } catch (RecipientFormattingException ex) {
      Toast.makeText(ConversationActivity.this,
                     R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation,
                     Toast.LENGTH_LONG).show();
      Log.w(TAG, ex);
    } catch (InvalidMessageException ex) {
      Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_message_is_empty_exclamation,
                     Toast.LENGTH_SHORT).show();
      Log.w(TAG, ex);
    }
  }

  private void sendMediaMessage(final boolean forceSms, final int subscriptionId)
      throws InvalidMessageException
  {
    sendMediaMessage(forceSms, getMessage(), attachmentManager.buildSlideDeck(), subscriptionId);
  }

  private ListenableFuture<Void> sendMediaMessage(final boolean forceSms, String body, SlideDeck slideDeck, final int subscriptionId)
      throws InvalidMessageException
  {
    final SettableFuture<Void> future          = new SettableFuture<>();
    final Context              context         = getApplicationContext();
          OutgoingMediaMessage outgoingMessage = new OutgoingMediaMessage(recipients,
                                                                          slideDeck,
                                                                          body,
                                                                          System.currentTimeMillis(),
                                                                          subscriptionId,
                                                                          distributionType);

    if (isSecureText && !forceSms) {
      outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessage);
    }

    attachmentManager.clear();
    composeText.setText("");

    new AsyncTask<OutgoingMediaMessage, Void, Long>() {
      @Override
      protected Long doInBackground(OutgoingMediaMessage... messages) {
        return MessageSender.send(context, masterSecret, messages[0], threadId, forceSms);
      }

      @Override
      protected void onPostExecute(Long result) {
        sendComplete(result);
        future.set(null);
      }
    }.execute(outgoingMessage);

    return future;
  }

  private void sendTextMessage(final boolean forceSms, final int subscriptionId)
      throws InvalidMessageException
  {
    final Context context = getApplicationContext();
    OutgoingTextMessage message;

    if (isSecureText && !forceSms) {
      message = new OutgoingEncryptedMessage(recipients, getMessage());
    } else {
      message = new OutgoingTextMessage(recipients, getMessage(), subscriptionId);
    }

    this.composeText.setText("");

    new AsyncTask<OutgoingTextMessage, Void, Long>() {
      @Override
      protected Long doInBackground(OutgoingTextMessage... messages) {
        return MessageSender.send(context, masterSecret, messages[0], threadId, forceSms);
      }

      @Override
      protected void onPostExecute(Long result) {
        sendComplete(result);
      }
    }.execute(message);
  }

  private void updateToggleButtonState() {
    if (composeText.getText().length() == 0 && !attachmentManager.isAttachmentPresent()) {
      buttonToggle.display(attachButton);
      quickAttachmentToggle.show();
    } else {
      buttonToggle.display(sendButton);
      quickAttachmentToggle.hide();
    }
  }

  private void recordSubscriptionIdPreference(final Optional<Integer> subscriptionId) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                       .setDefaultSubscriptionId(recipients, subscriptionId.or(-1));
        return null;
      }
    }.execute();
  }

  @Override
  public void onAttachmentDrawerStateChanged(DrawerState drawerState) {
    if (drawerState == DrawerState.FULL_EXPANDED) {
      getSupportActionBar().hide();
    } else {
      getSupportActionBar().show();
    }

    if (drawerState == DrawerState.COLLAPSED) {
      container.hideAttachedInput(true);
    }
  }

  @Override
  public void onImageCapture(@NonNull final byte[] imageBytes) {
    setMedia(PersistentBlobProvider.getInstance(this)
                                   .create(masterSecret, imageBytes, ContentType.IMAGE_JPEG),
             MediaType.IMAGE);
    quickAttachmentDrawer.hide(false);
  }

  @Override
  public void onCameraFail() {
    Toast.makeText(this, R.string.ConversationActivity_quick_camera_unavailable, Toast.LENGTH_SHORT).show();
    quickAttachmentDrawer.hide(false);
    quickAttachmentToggle.disable();
  }

  @Override
  public void onCameraStart() {}

  @Override
  public void onCameraStop() {}

  @Override
  public void onRecorderStarted() {
    Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    vibrator.vibrate(20);

    audioRecorder.startRecording();
  }

  @Override
  public void onRecorderFinished() {
    Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    vibrator.vibrate(20);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final @NonNull Pair<Uri, Long> result) {
        try {
          boolean    forceSms       = sendButton.isManualSelection() && sendButton.getSelectedTransport().isSms();
          int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
          AudioSlide audioSlide     = new AudioSlide(ConversationActivity.this, result.first, result.second, ContentType.AUDIO_AAC);
          SlideDeck  slideDeck      = new SlideDeck();
          slideDeck.addSlide(audioSlide);

          sendMediaMessage(forceSms, "", slideDeck, subscriptionId).addListener(new AssertedSuccessListener<Void>() {
            @Override
            public void onSuccess(Void nothing) {
              new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                  PersistentBlobProvider.getInstance(ConversationActivity.this).delete(result.first);
                  return null;
                }
              }.execute();
            }
          });
        } catch (InvalidMessageException e) {
          Log.w(TAG, e);
          Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_error_sending_voice_message, Toast.LENGTH_LONG).show();
        }
      }

      @Override
      public void onFailure(ExecutionException e) {
        Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_unable_to_record_audio, Toast.LENGTH_LONG).show();
      }
    });
  }

  @Override
  public void onRecorderCanceled() {
    Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    vibrator.vibrate(50);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final Pair<Uri, Long> result) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            PersistentBlobProvider.getInstance(ConversationActivity.this).delete(result.first);
            return null;
          }
        }.execute();
      }

      @Override
      public void onFailure(ExecutionException e) {}
    });
  }

  @Override
  public void onEmojiToggle() {
    if (container.getCurrentInput() == emojiDrawer) container.showSoftkey(composeText);
    else                                            container.show(composeText, emojiDrawer);
  }

  // Listeners

  private class AttachmentTypeListener implements AttachmentTypeSelector.AttachmentClickedListener {
    @Override
    public void onClick(int type) {
      addAttachment(type);
    }
  }

  private class QuickCameraToggleListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      if (!quickAttachmentDrawer.isShowing()) {
        composeText.clearFocus();
        container.show(composeText, quickAttachmentDrawer);
      } else {
        container.hideAttachedInput(false);
      }
    }
  }

  private class SendButtonListener implements OnClickListener, TextView.OnEditorActionListener {
    @Override
    public void onClick(View v) {
      sendMessage();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        sendButton.performClick();
        return true;
      }
      return false;
    }
  }

  private class AttachButtonListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      handleAddAttachment();
    }
  }

  private class AttachButtonLongClickListener implements View.OnLongClickListener {
    @Override
    public boolean onLongClick(View v) {
      return sendButton.performLongClick();
    }
  }

  private class ComposeKeyPressedListener implements OnKeyListener, OnClickListener, TextWatcher, OnFocusChangeListener {

    int beforeLength;

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (TextSecurePreferences.isEnterSendsEnabled(ConversationActivity.this)) {
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void onClick(View v) {
      container.showSoftkey(composeText);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,int after) {
      beforeLength = composeText.getText().length();
    }

    @Override
    public void afterTextChanged(Editable s) {
      calculateCharactersRemaining();

      if (composeText.getText().length() == 0 || beforeLength == 0) {
        composeText.postDelayed(new Runnable() {
          @Override
          public void run() {
            updateToggleButtonState();
          }
        }, 50);
      }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before,int count) {}

    @Override
    public void onFocusChange(View v, boolean hasFocus) {}
  }

  @Override
  public void setThreadId(long threadId) {
    this.threadId = threadId;
  }

  @Override
  public void onAttachmentChanged() {
    handleSecurityChange(isSecureText, isSecureVoice);
    updateToggleButtonState();
  }

  private class RecipientPreferencesTask extends AsyncTask<Recipients, Void, Pair<Recipients,RecipientsPreferences>> {
    @Override
    protected Pair<Recipients, RecipientsPreferences> doInBackground(Recipients... recipients) {
      if (recipients.length != 1 || recipients[0] == null) {
        throw new AssertionError("task needs exactly one Recipients object");
      }

      Optional<RecipientsPreferences> prefs = DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                                                             .getRecipientsPreferences(recipients[0].getIds());
      return new Pair<>(recipients[0], prefs.orNull());
    }

    @Override
    protected void onPostExecute(@NonNull  Pair<Recipients, RecipientsPreferences> result) {
      if (result.first == recipients) {
        updateInviteReminder(result.second != null && result.second.hasSeenInviteReminder());
        updateDefaultSubscriptionId(result.second != null ? result.second.getDefaultSubscriptionId() : Optional.<Integer>absent());
      }
    }
  }
}
