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

    public Failure(int status, String message) {
      super(message);
      this.status = status;
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
            .header("User-Agent", "MyTrailLog-Android/2.3.0");
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
    okhttp3.OkHttpClient selected =
        client
            .newBuilder()
            .readTimeout(
                (path.endsWith("/identify")
                        || path.endsWith("/publish")
                        || path.contains("/moderation/"))
                    ? 100
                    : 25,
                java.util.concurrent.TimeUnit.SECONDS)
            .build();
    try (okhttp3.Response response = selected.newCall(request.build()).execute()) {
      byte[] bytes =
          read(response.body() == null ? null : response.body().byteStream(), MAX_RESPONSE);
      if (!response.isSuccessful()) {
        String message = "The journal service is temporarily unavailable.";
        try {
          message =
              new JSONObject(new String(bytes, StandardCharsets.UTF_8)).optString("error", message);
        } catch (JSONException ignored) {
        }
        throw new Failure(response.code(), message);
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
