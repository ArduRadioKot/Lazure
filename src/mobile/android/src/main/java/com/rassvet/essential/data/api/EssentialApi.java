package com.rassvet.essential.data.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;

public final class EssentialApi {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType OCTET = MediaType.parse("application/octet-stream");

    private final String baseUrl;
    private final OkHttpClient client;

    public EssentialApi(String baseUrl) {
        this.baseUrl = ApiBaseUrls.normalize(baseUrl);
        this.client =
                new OkHttpClient.Builder()
                        .callTimeout(25, TimeUnit.SECONDS)
                        .connectTimeout(12, TimeUnit.SECONDS)
                        .readTimeout(25, TimeUnit.SECONDS)
                        .writeTimeout(25, TimeUnit.SECONDS)
                        .build();
    }

    private String url(String path) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = path.startsWith("/") ? path : "/" + path;
        return trimmed + p;
    }

    private static String readBody(Response response) throws IOException {
        ResponseBody b = response.body();
        return b != null ? b.string() : "";
    }

    private static void throwIfBad(Response response, int code) throws IOException {
        String body = readBody(response);
        response.close();
        throw new EssentialHttpException(code, body);
    }

    public static String normalizeAuthToken(String token) {
        if (token == null) return "";
        String t = token.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            t = t.substring(7).trim();
        }
        return t;
    }

    private static String bearer(String token) {
        String auth = normalizeAuthToken(token);
        if (auth.isEmpty()) {
            throw new IllegalArgumentException("missing auth token");
        }
        return "Bearer " + auth;
    }

    private static String parseAccessToken(JSONObject json) throws org.json.JSONException {
        if (json.has("accessToken") && !json.isNull("accessToken")) {
            return json.getString("accessToken");
        }
        if (json.has("access_token") && !json.isNull("access_token")) {
            return json.getString("access_token");
        }
        throw new org.json.JSONException("missing accessToken");
    }

    public TokenResponse login(String loginOrEmail, String password) throws IOException {
        JSONObject o = new JSONObject();
        try {
            o.put("login", loginOrEmail);
            o.put("password", password);
        } catch (org.json.JSONException e) {
            throw new RuntimeException(e);
        }
        Request.Builder reqBuilder =
                new Request.Builder()
                        .url(url("/api/auth/login"))
                        .post(RequestBody.create(o.toString(), JSON));
        DeviceAuthHeaders.apply(reqBuilder);
        try (Response resp = client.newCall(reqBuilder.build()).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
            JSONObject json = new JSONObject(readBody(resp));
            return new TokenResponse(parseAccessToken(json));
        } catch (org.json.JSONException e) {
            throw new IOException("login parse", e);
        }
    }

    public void registerSendCode(String email, String password, String login) throws IOException {
        JSONObject o = new JSONObject();
        try {
            o.put("email", email);
            o.put("password", password);
            o.put("login", login);
        } catch (org.json.JSONException e) {
            throw new RuntimeException(e);
        }
        Request req =
                new Request.Builder()
                        .url(url("/api/auth/register/send-code"))
                        .post(RequestBody.create(o.toString(), JSON))
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
        }
    }

    public PasswordResetSendCodeResponse passwordResetSendCode(String loginOrEmail) throws IOException {
        JSONObject o = new JSONObject();
        try {
            o.put("loginOrEmail", loginOrEmail);
        } catch (org.json.JSONException e) {
            throw new RuntimeException(e);
        }
        Request req =
                new Request.Builder()
                        .url(url("/api/auth/password-reset/send-code"))
                        .post(RequestBody.create(o.toString(), JSON))
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
            JSONObject json = new JSONObject(readBody(resp));
            String email =
                    json.has("email") && !json.isNull("email") ? json.getString("email") : null;
            return new PasswordResetSendCodeResponse(email);
        } catch (org.json.JSONException e) {
            throw new IOException("passwordResetSendCode parse", e);
        }
    }

    public TokenResponse passwordResetConfirm(String email, String code, String password)
            throws IOException {
        JSONObject o = new JSONObject();
        try {
            o.put("email", email);
            o.put("code", code);
            o.put("password", password);
        } catch (org.json.JSONException e) {
            throw new RuntimeException(e);
        }
        Request.Builder reqBuilder =
                new Request.Builder()
                        .url(url("/api/auth/password-reset/confirm"))
                        .post(RequestBody.create(o.toString(), JSON));
        DeviceAuthHeaders.apply(reqBuilder);
        try (Response resp = client.newCall(reqBuilder.build()).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
            JSONObject json = new JSONObject(readBody(resp));
            return new TokenResponse(parseAccessToken(json));
        } catch (org.json.JSONException e) {
            throw new IOException("passwordResetConfirm parse", e);
        }
    }

    public TokenResponse registerConfirm(String email, String code) throws IOException {
        JSONObject o = new JSONObject();
        try {
            o.put("email", email);
            o.put("code", code);
        } catch (org.json.JSONException e) {
            throw new RuntimeException(e);
        }
        Request.Builder reqBuilder =
                new Request.Builder()
                        .url(url("/api/auth/register/confirm"))
                        .post(RequestBody.create(o.toString(), JSON));
        DeviceAuthHeaders.apply(reqBuilder);
        try (Response resp = client.newCall(reqBuilder.build()).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
            JSONObject json = new JSONObject(readBody(resp));
            return new TokenResponse(parseAccessToken(json));
        } catch (org.json.JSONException e) {
            throw new IOException("registerConfirm parse", e);
        }
    }

    public MeAccountResponse meAccount(String token) throws IOException {
        try {
            return meAccountFromPath(token, "/api/me/account");
        } catch (EssentialHttpException e) {
            if (e.getStatusCode() == 404) {
                return meAccountFromPath(token, "/api/me/quota");
            }
            throw e;
        }
    }

    private MeAccountResponse meAccountFromPath(String token, String path) throws IOException {
        Request req =
                new Request.Builder()
                        .url(url(path))
                        .header("Authorization", bearer(token))
                        .get()
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
            JSONObject json = new JSONObject(readBody(resp));
            long tokensQuota = 0L;
            if (json.has("tokensQuota") && !json.isNull("tokensQuota")) {
                tokensQuota = json.getLong("tokensQuota");
            } else if (json.has("tokens_quota") && !json.isNull("tokens_quota")) {
                tokensQuota = json.getLong("tokens_quota");
            }
            long tokensUsed = json.optLong("tokensUsed", json.optLong("tokens_used", 0L));
            String subscriptionStatus =
                    json.optString(
                            "subscriptionStatus",
                            json.optString("subscription_status", "free"));
            Long subscriptionExpiresAtEpochMs = null;
            if (json.has("subscriptionExpiresAtEpochMs") && !json.isNull("subscriptionExpiresAtEpochMs")) {
                subscriptionExpiresAtEpochMs = json.getLong("subscriptionExpiresAtEpochMs");
            } else if (json.has("subscription_expires_at_epoch_ms")
                    && !json.isNull("subscription_expires_at_epoch_ms")) {
                subscriptionExpiresAtEpochMs = json.getLong("subscription_expires_at_epoch_ms");
            }
            int subscriptionPriceRub =
                    json.optInt("subscriptionPriceRub", json.optInt("subscription_price_rub", 299));
            int subscriptionPeriodDays =
                    json.optInt("subscriptionPeriodDays", json.optInt("subscription_period_days", 31));
            long freeTokensQuota =
                    json.optLong("freeTokensQuota", json.optLong("free_tokens_quota", 0L));
            long proTokensQuota =
                    json.optLong("proTokensQuota", json.optLong("pro_tokens_quota", 0L));
            return new MeAccountResponse(
                    subscriptionStatus,
                    tokensUsed,
                    tokensQuota,
                    freeTokensQuota,
                    proTokensQuota,
                    json.optLong("createdAtEpochMs", json.optLong("created_at_epoch_ms", 0L)),
                    subscriptionExpiresAtEpochMs,
                    subscriptionPriceRub,
                    subscriptionPeriodDays);
        } catch (org.json.JSONException e) {
            throw new IOException("meAccount parse", e);
        }
    }

    public SubscriptionCheckoutResponse createSubscriptionCheckout(String token) throws IOException {
        Request req =
                new Request.Builder()
                        .url(url("/api/billing/subscription/checkout"))
                        .header("Authorization", bearer(token))
                        .post(RequestBody.create("{}", JSON))
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
            JSONObject json = new JSONObject(readBody(resp));
            return new SubscriptionCheckoutResponse(
                    json.getString("paymentId"),
                    json.optString("transactionId", ""),
                    json.getString("paymentUrl"),
                    json.optInt("amountRub", json.optInt("amount_rub", 299)),
                    json.optInt("periodDays", json.optInt("period_days", 31)));
        } catch (org.json.JSONException e) {
            throw new IOException("createSubscriptionCheckout parse", e);
        }
    }

    public java.util.List<SubscriptionPaymentItem> listSubscriptionPayments(String token) throws IOException {
        Request req =
                new Request.Builder()
                        .url(url("/api/billing/subscription/payments?limit=50"))
                        .header("Authorization", bearer(token))
                        .get()
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
            JSONArray arr = new JSONArray(readBody(resp));
            java.util.ArrayList<SubscriptionPaymentItem> out = new java.util.ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject json = arr.getJSONObject(i);
                Long confirmedAt = null;
                if (json.has("confirmedAtEpochMs") && !json.isNull("confirmedAtEpochMs")) {
                    confirmedAt = json.getLong("confirmedAtEpochMs");
                }
                out.add(
                        new SubscriptionPaymentItem(
                                json.getString("id"),
                                json.optLong("amountRub", json.optLong("amount_rub", 0L)),
                                json.optInt("periodDays", json.optInt("period_days", 0)),
                                json.optString("status", "pending"),
                                json.optLong("createdAtEpochMs", json.optLong("created_at_epoch_ms", 0L)),
                                confirmedAt));
            }
            return out;
        } catch (org.json.JSONException e) {
            throw new IOException("listSubscriptionPayments parse", e);
        }
    }

    public void reportAiUsage(String token, int tokensIn, int tokensOut) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("tokensIn", tokensIn);
            body.put("tokensOut", tokensOut);
        } catch (org.json.JSONException e) {
            throw new RuntimeException(e);
        }
        Request req =
                new Request.Builder()
                        .url(url("/api/ai/report-usage"))
                        .header("Authorization", bearer(token))
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
        }
    }

    public AiCompleteResponse aiComplete(String token, List<ChatMessageJson> messages)
            throws IOException {
        return aiComplete(token, messages, java.util.Collections.emptyList());
    }

    public AiCompleteResponse aiComplete(
            String token, List<ChatMessageJson> messages, List<AttachmentPayload> attachments)
            throws IOException {
        JSONArray arr = new JSONArray();
        try {
            for (ChatMessageJson m : messages) {
                JSONObject jm = new JSONObject();
                jm.put("role", m.getRole());
                jm.put("content", m.getContent());
                arr.put(jm);
            }
        } catch (org.json.JSONException e) {
            throw new RuntimeException(e);
        }
        JSONObject body = new JSONObject();
        try {
            body.put("messages", arr);
            if (attachments != null && !attachments.isEmpty()) {
                JSONArray attArr = new JSONArray();
                for (AttachmentPayload att : attachments) {
                    JSONObject ja = new JSONObject();
                    ja.put("mimeType", att.mimeType);
                    ja.put("base64", att.base64);
                    ja.put("displayName", att.displayName);
                    attArr.put(ja);
                }
                body.put("attachments", attArr);
            }
        } catch (org.json.JSONException e) {
            throw new RuntimeException(e);
        }

        Request req =
                new Request.Builder()
                        .url(url("/api/ai/complete"))
                        .header("Authorization", bearer(token))
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
            JSONObject json = new JSONObject(readBody(resp));
            return new AiCompleteResponse(
                    json.getString("text"),
                    json.optInt("tokensIn", 0),
                    json.optInt("tokensOut", 0));
        } catch (org.json.JSONException e) {
            throw new IOException("ai parse", e);
        }
    }

    public List<VaultSnapshotItemJson> listSnapshots(String token) throws IOException {
        Request req =
                new Request.Builder()
                        .url(url("/api/vault/snapshots"))
                        .header("Authorization", bearer(token))
                        .get()
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
            JSONArray json = new JSONArray(readBody(resp));
            List<VaultSnapshotItemJson> out = new ArrayList<>(json.length());
            for (int i = 0; i < json.length(); i++) {
                JSONObject o = json.getJSONObject(i);
                out.add(
                        new VaultSnapshotItemJson(
                                o.getLong("id"),
                                o.getLong("version"),
                                o.getLong("sizeBytes"),
                                o.getLong("createdAtEpochMs")));
            }
            return out;
        } catch (org.json.JSONException e) {
            throw new IOException("snapshots parse", e);
        }
    }

    public VaultSnapshotItemJson uploadSnapshot(String token, byte[] ciphertext)
            throws IOException {
        Request req =
                new Request.Builder()
                        .url(url("/api/vault/snapshot"))
                        .header("Authorization", bearer(token))
                        .put(RequestBody.create(ciphertext, OCTET))
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
            JSONObject json = new JSONObject(readBody(resp));
            return new VaultSnapshotItemJson(
                    json.getLong("id"),
                    json.getLong("version"),
                    json.getLong("sizeBytes"),
                    json.getLong("createdAtEpochMs"));
        } catch (org.json.JSONException e) {
            throw new IOException("upload parse", e);
        }
    }

    public byte[] downloadSnapshot(String token, long id) throws IOException {
        Request req =
                new Request.Builder()
                        .url(url("/api/vault/snapshot/" + id))
                        .header("Authorization", bearer(token))
                        .get()
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (resp.code() != 200 || !resp.isSuccessful()) throwIfBad(resp, resp.code());
            ResponseBody b = resp.body();
            if (b == null) throw new IOException("empty body");
            return b.bytes();
        }
    }

    public List<AuthSessionItem> listAuthSessions(String token) throws IOException {
        Request req =
                new Request.Builder()
                        .url(url("/api/me/sessions"))
                        .header("Authorization", bearer(token))
                        .get()
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
            JSONArray arr = new JSONArray(readBody(resp));
            List<AuthSessionItem> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject json = arr.getJSONObject(i);
                out.add(
                        new AuthSessionItem(
                                json.getString("id"),
                                json.optString("deviceLabel", json.optString("device_label", "")),
                                json.optString(
                                        "devicePlatform", json.optString("device_platform", "")),
                                json.optString("appVersion", json.optString("app_version", "")),
                                json.optString("ipAddress", json.optString("ip_address", "")),
                                json.optLong(
                                        "createdAtEpochMs", json.optLong("created_at_epoch_ms", 0L)),
                                json.optLong(
                                        "lastSeenAtEpochMs",
                                        json.optLong("last_seen_at_epoch_ms", 0L)),
                                json.optBoolean("current", false)));
            }
            return out;
        } catch (org.json.JSONException e) {
            throw new IOException("listAuthSessions parse", e);
        }
    }

    public void revokeAuthSession(String token, String sessionId) throws IOException {
        Request req =
                new Request.Builder()
                        .url(url("/api/me/sessions/" + sessionId))
                        .header("Authorization", bearer(token))
                        .delete()
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
        }
    }

    public List<ActivityDayJson> fetchActivityHeatmap(String token) throws IOException {
        Request req =
                new Request.Builder()
                        .url(url("/api/me/activity"))
                        .header("Authorization", bearer(token))
                        .get()
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
            JSONObject root = new JSONObject(readBody(resp));
            JSONArray days = root.optJSONArray("days");
            if (days == null) return java.util.Collections.emptyList();
            List<ActivityDayJson> out = new ArrayList<>(days.length());
            for (int i = 0; i < days.length(); i++) {
                JSONObject json = days.getJSONObject(i);
                out.add(
                        new ActivityDayJson(
                                json.optString("date", ""),
                                json.optInt("prompts", 0),
                                json.optInt("notes", 0),
                                json.optInt("total", 0)));
            }
            return out;
        } catch (org.json.JSONException e) {
            throw new IOException("activityHeatmap parse", e);
        }
    }

    public void incrementActivity(String token, int prompts, int notes, String date)
            throws IOException {
        JSONObject body;
        try {
            body =
                    new JSONObject()
                            .put("prompts", prompts)
                            .put("notes", notes)
                            .put("date", date);
        } catch (org.json.JSONException e) {
            throw new IOException("activityIncrement body", e);
        }
        Request req =
                new Request.Builder()
                        .url(url("/api/me/activity/increment"))
                        .header("Authorization", bearer(token))
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throwIfBad(resp, resp.code());
        }
    }

    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}


