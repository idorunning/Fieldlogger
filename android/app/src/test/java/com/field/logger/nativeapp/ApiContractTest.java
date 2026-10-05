package com.field.logger.nativeapp;

import static org.junit.Assert.*;

import java.io.*;
import java.time.Instant;
import java.util.UUID;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class ApiContractTest {
  @Test
  public void nativeRequestsCanSignInUploadReadAndDeleteATestAccount() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("FIELDLOGGER_NATIVE_LIVE_TESTS")));
    Api api = new Api();
    JSONObject account = new JSONObject();
    String password = "FieldLogger-native-QA-only-2026!";
    Observation.put(account, "email", "native-" + UUID.randomUUID() + "@example.test");
    Observation.put(account, "password", password);
    Observation.put(account, "name", "Native integration test");
    Api.Response signup =
        api.request(
            "/api/auth/register",
            "POST",
            null,
            account.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "application/json");
    String cookie = signup.cookie;
    assertTrue(cookie.startsWith("fieldnotes_session="));
    String owner = signup.json().getJSONObject("user").getString("id");
    try {
      assertEquals(
          owner,
          api.json("/api/auth/me", "GET", cookie, null).getJSONObject("user").getString("id"));
      File photo = File.createTempFile("native-contract-", ".jpg");
      try (InputStream in = getClass().getResourceAsStream("/robin.jpg");
          OutputStream out = new FileOutputStream(photo)) {
        out.write(Api.read(in, 4 * 1024 * 1024));
      }
      Observation record =
          new Observation(
              Observation.fresh(UUID.randomUUID().toString(), Instant.now()),
              owner,
              photo,
              true,
              "");
      Observation.put(record.data, "name", "Native test robin");
      Observation.put(record.data, "confirmed", true);
      Observation.put(record.data, "category", "birds");
      api.upload(record, cookie);
      JSONArray rows =
          api.json("/api/observations", "GET", cookie, null).getJSONArray("observations");
      assertEquals(1, rows.length());
      assertEquals(record.id(), rows.getJSONObject(0).getString("id"));
      assertTrue(
          api.request("/api/observations/" + record.id() + "/photo", "GET", cookie, null, null)
                  .bytes
                  .length
              > 10);
      try {
        api.request("/api/observations/" + record.id() + "/photo", "GET", null, null, null);
        fail("An unauthenticated photo request succeeded");
      } catch (Api.Failure e) {
        assertEquals(401, e.status);
      }
      Observation.put(record.data, "archived", true);
      Observation.put(record.data, "revision", 2);
      api.upload(record, cookie);
      assertTrue(
          api.json("/api/observations", "GET", cookie, null)
              .getJSONArray("observations")
              .getJSONObject(0)
              .getBoolean("archived"));
      record.data.remove("archived");
      Observation.put(record.data, "revision", 3);
      api.upload(record, cookie);
      assertTrue(
          api.json("/api/observations", "GET", cookie, null)
              .getJSONArray("observations")
              .getJSONObject(0)
              .getBoolean("archived"));
      Observation.put(record.data, "archived", false);
      Observation.put(record.data, "revision", 4);
      api.upload(record, cookie);
      assertFalse(
          api.json("/api/observations", "GET", cookie, null)
              .getJSONArray("observations")
              .getJSONObject(0)
              .getBoolean("archived"));
      assertFalse(api.json("/api/settings/openai-key", "GET", cookie, null).optBoolean("hasKey"));
      JSONObject status = api.json("/api/status", "GET", cookie, null);
      assertTrue("Identification must work for a new account", status.optBoolean("identification"));
      assertTrue(api.json("/api/settings/openai-key", "GET", cookie, null).optBoolean("serverKey"));
      assertFalse(api.json("/api/settings/openai-key", "GET", cookie, null).optBoolean("canSave"));
      photo.delete();
    } finally {
      JSONObject confirm = new JSONObject();
      Observation.put(confirm, "password", password);
      api.json("/api/account", "DELETE", cookie, confirm);
    }
    assertTrue(api.json("/api/auth/me", "GET", cookie, null).isNull("user"));
  }
}
