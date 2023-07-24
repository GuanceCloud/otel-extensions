package com.guance.javaagent.http;

import io.opentelemetry.sdk.internal.ThrottlingLogger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OKHTTPClient {
    private static final Logger internalLogger = Logger.getLogger(OKHTTPClient.class.getName());
    private final ThrottlingLogger logger = new ThrottlingLogger(internalLogger);
    private static final String DEFAULT_OPENWAY = "https://openway.guance.com";
    public static final String TRACE_CATEGORY = "/v1/write/tracing";
    public static final String METRIC_CATEGORY = "/v1/write/metric";

    private String url;
    private String token;
    private final OkHttpClient client;

    public OKHTTPClient() {
        this.client = new OkHttpClient();
        this.url = "";
        this.token = "";
    }

    public void setEndpoint(@Nonnull String openway) {
        if (!openway.equals("")) {
            this.url = openway;
        } else {
            this.url = DEFAULT_OPENWAY;
        }
    }

    public void setToken( String token) {
        if (!token.equals("")) {
            this.token = token;
        }
    }

    public void write(String data, Integer pts, String category) {
        if(url.equals("") || token.equals("")){
            logger.log(Level.WARNING, "url is null or token is null, can not upload data to openway");
            return;
        }
        Request request =
                new Request.Builder()
                        .url(url + category + "?token=" + token)
                        .addHeader("X-Points", pts.toString()) // 添加 header
                        .post(RequestBody.create(data.getBytes(StandardCharsets.UTF_8)))
                        .build();
        try {
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                logger.log(Level.WARNING, "code=" + response.code());
            }
            response.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, e.toString());
        }
    }
}
