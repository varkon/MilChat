package ua.org.slovo.securesms.providers;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import ua.org.slovo.securesms.crypto.DecryptingPartInputStream;
import ua.org.slovo.securesms.crypto.EncryptingPartOutputStream;
import ua.org.slovo.securesms.crypto.MasterSecret;
import ua.org.slovo.securesms.recipients.Recipients;
import ua.org.slovo.securesms.util.Util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SingleUseBlobProvider {

  private static final String TAG = SingleUseBlobProvider.class.getSimpleName();

  public  static final String AUTHORITY   = "ua.org.slovo.securesms";
  public  static final String PATH        = "memory/*/#";
  private static final Uri    CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/memory");

  private final Map<Long, byte[]> cache = new HashMap<>();

  private static final SingleUseBlobProvider instance = new SingleUseBlobProvider();

  public static SingleUseBlobProvider getInstance() {
    return instance;
  }

  private SingleUseBlobProvider() {}

  public synchronized Uri createUri(@NonNull byte[] blob) {
    try {
      long id = Math.abs(SecureRandom.getInstance("SHA1PRNG").nextLong());
      cache.put(id, blob);

      Uri uniqueUri = Uri.withAppendedPath(CONTENT_URI, String.valueOf(System.currentTimeMillis()));
      return ContentUris.withAppendedId(uniqueUri, id);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public synchronized @NonNull InputStream getStream(long id) throws IOException {
    byte[] cached = cache.get(id);
    cache.remove(id);

    if (cached != null) return new ByteArrayInputStream(cached);
    else                throw new IOException("ID not found: " + id);

  }

}
