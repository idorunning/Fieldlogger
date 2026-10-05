package com.field.logger.nativeapp;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public final class Api {
  public static final String ORIGIN = "https://fieldlogger.co.uk";

  public static final class Failure extends IOException {
    public final int status;
    public final String code;

    public Failure(int status, String message) {
      this(status, message, "");
    }

    public Failure(int status, String message, String code) {
      super(message);
      this.status = status;
      this.code = code == null ? "" : code;
    }

    public boolean photoLimit() {
      return status == 429 && "photo_limit".equals(code);
    }
  }

  public static final class Response {
    public final byte[] bytes;
    public final String cookie;

    Response(byte[] b, String c) {
      bytes = b;
      cookie = c;
    }

    public JSONObject json() throws JSONException {
      return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    }
  }

  private static final int MAX_RESPONSE = 40 * 1024 * 1024;
  private final okhttp3.OkHttpClient client =
      new okhttp3.OkHttpClient.Builder()
          .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
          .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
          .followRedirects(false)
          .followSslRedirects(false)
          .build();

  public Response request(
      String path, String method, String cookie, byte[] body, String contentType)
      throws IOException {
    if (!path.startsWith("/api/") || path.contains("\r") || path.contains("\n"))
      throw new IOException("Invalid journal request.");
    okhttp3.Request.Builder request =
        new okhttp3.Request.Builder()
            .url(ORIGIN + path)
            .header("Accept", "application/json")
            .header("Origin", ORIGIN)
            .header(
                "User-Agent", "MyTrailLog-Android/" + com.field.logger.BuildConfig.VERSION_NAME);
    if (cookie != null && !cookie.isEmpty()) request.header("Cookie", cookie);
    byte[] payload = body;
    if (payload == null
        && !method.equals("GET")
        && !method.equals("HEAD")
        && !method.equals("DELETE")) payload = new byte[0];
    okhttp3.RequestBody requestBody =
        payload == null
            ? null
            : okhttp3.RequestBody.create(
                okhttp3.MediaType.parse(contentType == null ? "application/json" : contentType),
                payload);
    request.method(method, requestBody);
    String operation = path.split("\\?", 2)[0];
    okhttp3.OkHttpClient selected =
        client
            .newBuilder()
            .readTimeout(
                operation.endsWith("/avatar/photo")
                    ? 180
                    : operation.endsWith("/identify")
                        ? 240
                        : (operation.endsWith("/publish") || operation.contains("/moderation/"))
                            ? 100
                            : 25,
                java.util.concurrent.TimeUnit.SECONDS)
            .build();
    try (okhttp3.Response response = selected.newCall(request.build()).execute()) {
      byte[] bytes =
          read(response.body() == null ? null : response.body().byteStream(), MAX_RESPONSE);
      if (!response.isSuccessful()) {
        String message = "The journal service is temporarily unavailable.";
        String code = "";
        try {
          JSONObject failure = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
          message = failure.optString("error", message);
          code = failure.optString("code", "");
        } catch (JSONException ignored) {
        }
        throw new Failure(response.code(), message, code);
      }
      String cookieValue = "";
      for (String value : response.headers("Set-Cookie")) {
        String candidate = value.split(";", 2)[0].trim();
        if (candidate.startsWith("fieldnotes_session=")) cookieValue = candidate;
      }
      return new Response(bytes, cookieValue);
    }
  }

  public JSONObject json(String path, String method, String cookie, JSONObject body)
      throws Exception {
    return request(
            path,
            method,
            cookie,
            body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8),
            "application/json")
        .json();
  }

  public JSONObject identifyCloser(String id, String cookie) throws Exception {
    UUID.fromString(id);
    return json("/api/observations/" + id + "/identify?closer=1", "POST", cookie, new JSONObject())
        .getJSONObject("identification");
  }

  /** Streams the authenticated free export to the chosen document, regardless of ZIP size. */
  public long exportZip(String cookie, OutputStream destination) throws IOException {
    if (cookie == null || cookie.isEmpty())
      throw new Failure(401, "Sign in to download your cloud journal.");
    okhttp3.Request request =
        new okhttp3.Request.Builder()
            .url(ORIGIN + "/api/export")
            .header("Accept", "application/zip")
            .header("Origin", ORIGIN)
            .header("Cookie", cookie)
            .header("User-Agent", "MyTrailLog-Android/" + com.field.logger.BuildConfig.VERSION_NAME)
            .get()
            .build();
    try (okhttp3.Response response =
        client
            .newBuilder()
            .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .execute()) {
      if (!response.isSuccessful()) {
        byte[] bytes =
            read(response.body() == null ? null : response.body().byteStream(), 64 * 1024);
        String message = "The journal export could not be downloaded. Please try again.";
        try {
          message =
              new JSONObject(new String(bytes, StandardCharsets.UTF_8)).optString("error", message);
        } catch (JSONException ignored) {
        }
        throw new Failure(response.code(), message);
      }
      if (response.body() == null
          || !response.header("Content-Type", "").startsWith("application/zip"))
        throw new IOException("The service did not return a journal ZIP file.");
      long written = 0;
      byte[] buffer = new byte[64 * 1024];
      try (InputStream stream = response.body().byteStream()) {
        int count;
        while ((count = stream.read(buffer)) != -1) {
          destination.write(buffer, 0, count);
          written += count;
        }
      }
      destination.flush();
      return written;
    }
  }

  public void upload(Observation record, String cookie) throws Exception {
    String boundary = "FieldLogger" + UUID.randomUUID().toString().replace("-", "");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(
        ("--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"metadata\"\r\n"
                + "Content-Type: application/json\r\n\r\n"
                + record.data
                + "\r\n--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"photo\"; filename=\"discovery.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    try (InputStream image = new FileInputStream(record.photo)) {
      out.write(read(image, 4 * 1024 * 1024));
    }
    out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    request(
        "/api/observations/" + record.id(),
        "PUT",
        cookie,
        out.toByteArray(),
        "multipart/form-data; boundary=" + boundary);
  }

  public static byte[] read(InputStream stream, int maximum) throws IOException {
    if (stream == null) return new byte[0];
    try (InputStream in = stream;
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = in.read(buffer)) != -1) {
        if (out.size() + count > maximum) throw new IOException("This file is too large.");
        out.write(buffer, 0, count);
      }
      return out.toByteArray();
    }
  }
}
