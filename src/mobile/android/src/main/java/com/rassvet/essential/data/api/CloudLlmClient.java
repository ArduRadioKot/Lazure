package com.rassvet.essential.data.api;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public final class CloudLlmClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client =
            new OkHttpClient.Builder()
                    .callTimeout(120, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .build();

    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private static String readBody(Response response) throws IOException {
        return response.body() != null ? response.body().string() : "";
    }

    public String openAiCompatibleChat(
            String baseUrl,
            String apiKey,
            String model,
            List<ChatMessageJson> messages,
            float temperature)
            throws IOException {
        try {
            String root = baseUrl.trim();
            while (root.endsWith("/")) {
                root = root.substring(0, root.length() - 1);
            }
            String url;
            if (root.toLowerCase().endsWith("/v1")) {
                url = root + "/chat/completions";
            } else {
                url = root + "/v1/chat/completions";
            }

            JSONArray arr = new JSONArray();
            for (ChatMessageJson m : messages) {
                JSONObject mo = new JSONObject();
                mo.put("role", m.getRole());
                mo.put("content", m.getContent());
                arr.put(mo);
            }
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("messages", arr);
            body.put("temperature", (double) temperature);

            Request req =
                    new Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer " + apiKey)
                            .post(RequestBody.create(body.toString(), JSON))
                            .build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    String err = readBody(resp);
                    throw new EssentialHttpException(resp.code(), err);
                }
                JSONObject json = new JSONObject(readBody(resp));
                JSONArray choices = json.optJSONArray("choices");
                if (choices == null || choices.length() == 0) {
                    throw new IOException("empty choices");
                }
                JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                if (message == null) {
                    throw new IOException("empty message");
                }
                String text = message.optString("content", "").trim();
                if (text.isEmpty()) {
                    throw new IOException("пустой ответ от провайдера");
                }
                return text;
            }
        } catch (JSONException e) {
            throw new IOException("openai parse", e);
        }
    }

    public String geminiGenerate(
            String apiKey, String modelName, List<ChatMessageJson> messages, float temperature)
            throws IOException {
        return geminiGenerate(apiKey, modelName, messages, temperature, java.util.Collections.emptyList());
    }


    public String geminiGenerate(
            String apiKey,
            String modelName,
            List<ChatMessageJson> messages,
            float temperature,
            List<AttachmentPayload> attachments)
            throws IOException {
        try {
            String model = modelName != null ? modelName.trim() : "";
            if (model.isEmpty()) {
                model = GeminiDefaults.MODEL_ID;
            }
            String url =
                    "https://generativelanguage.googleapis.com/v1beta/models/"
                            + model
                            + ":generateContent";

            JSONArray contents = new JSONArray();
            int lastUserIdx = -1;
            for (int i = 0; i < messages.size(); i++) {
                if (!"assistant".equalsIgnoreCase(messages.get(i).getRole())) {
                    lastUserIdx = i;
                }
            }
            for (int i = 0; i < messages.size(); i++) {
                ChatMessageJson m = messages.get(i);
                String role =
                        "assistant".equalsIgnoreCase(m.getRole()) ? "model" : "user";
                JSONArray partsArr = new JSONArray();
                JSONObject textPart = new JSONObject();
                textPart.put("text", m.getContent());
                partsArr.put(textPart);
                if (i == lastUserIdx && attachments != null && !attachments.isEmpty()) {
                    for (AttachmentPayload att : attachments) {
                        JSONObject inline = new JSONObject();
                        inline.put("mime_type", att.mimeType);
                        inline.put("data", att.base64);
                        JSONObject inlinePart = new JSONObject();
                        inlinePart.put("inline_data", inline);
                        partsArr.put(inlinePart);
                    }
                }
                JSONObject content = new JSONObject();
                content.put("role", role);
                content.put("parts", partsArr);
                contents.put(content);
            }
            JSONObject genCfg = new JSONObject();
            genCfg.put("temperature", (double) temperature);
            JSONObject body = new JSONObject();
            body.put("contents", contents);
            body.put("generationConfig", genCfg);

            Request req =
                    new Request.Builder()
                            .url(url)
                            .header("x-goog-api-key", apiKey)
                            .post(RequestBody.create(body.toString(), JSON))
                            .build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    String err = readBody(resp);
                    throw new EssentialHttpException(resp.code(), err);
                }
                JSONObject json = new JSONObject(readBody(resp));
                JSONArray candidates = json.optJSONArray("candidates");
                if (candidates == null || candidates.length() == 0) {
                    throw new IOException("пустой ответ Gemini");
                }
                JSONObject cand = candidates.getJSONObject(0);
                JSONObject candContent = cand.optJSONObject("content");
                if (candContent == null) {
                    throw new IOException("пустой ответ Gemini");
                }
                JSONArray cparts = candContent.optJSONArray("parts");
                if (cparts == null || cparts.length() == 0) {
                    throw new IOException("пустой ответ Gemini");
                }
                String text = cparts.getJSONObject(0).optString("text", "").trim();
                if (text.isEmpty()) {
                    throw new IOException("пустой ответ Gemini");
                }
                return text;
            }
        } catch (JSONException e) {
            throw new IOException("gemini parse", e);
        }
    }
}


