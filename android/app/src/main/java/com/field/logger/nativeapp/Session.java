package com.field.logger.nativeapp;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/**
 * Only the account cookie is persisted, encrypted by Android Keystore. Passwords/AI keys are never
 * kept.
 */
public final class Session {
  private final Context context;

  public Session(Context c) {
    context = c.getApplicationContext();
  }

  private javax.crypto.SecretKey key() throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if (ks.containsAlias("fieldlogger-session-v1"))
      return (javax.crypto.SecretKey) ks.getKey("fieldlogger-session-v1", null);
    KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    gen.init(
        new KeyGenParameterSpec.Builder(
                "fieldlogger-session-v1",
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build());
    return gen.generateKey();
  }

  public synchronized JSONObject get() {
    try {
      String value = context.getSharedPreferences("session", 0).getString("sealed", "");
      if (value.isEmpty()) return null;
      String[] parts = value.split(":");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          key(),
          new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
      return new JSONObject(
          new String(
              cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
              java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      return null;
    }
  }

  public synchronized void save(JSONObject user, String cookie) throws Exception {
    JSONObject data = Observation.copy(user);
    Observation.put(data, "cookie", cookie);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key());
    byte[] sealed =
        cipher.doFinal(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    if (!context
        .getSharedPreferences("session", 0)
        .edit()
        .putString(
            "sealed",
            Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                + ":"
                + Base64.encodeToString(sealed, Base64.NO_WRAP))
        .commit()) throw new java.io.IOException("Could not save your sign-in.");
  }

  public String owner() {
    JSONObject u = get();
    return u == null ? "guest" : u.optString("id", "guest");
  }

  public synchronized void clear() {
    context.getSharedPreferences("session", 0).edit().clear().commit();
  }
}
