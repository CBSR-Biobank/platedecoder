package org.biobank.platedecoder.service;

import java.util.Base64;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.BoundRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.asynchttpclient.Dsl.*;

public class HttpClient {

    private static final int DEFAULT_CONNECTION_TIMEOUT_IN_MS = 1000;
    private static final int DEFAULT_REQUEST_TIMEOUT_IN_MS = 1000;

    private AsyncHttpClient asyncHttpClient;

    private final String userName;

    private final String password;

    public HttpClient() {
        this.userName = "admin@admin.com";
        this.password = "Areallybadpassword1"; // "Login!@3";

        asyncHttpClient = asyncHttpClient(config()
            .setConnectTimeout(DEFAULT_CONNECTION_TIMEOUT_IN_MS)
            .setRequestTimeout(DEFAULT_REQUEST_TIMEOUT_IN_MS));
    }

    public void close() {
        //asyncHttpClient.close();
    }

    public BoundRequestBuilder prepareGet(String url) {
        return asyncHttpClient.prepareGet(url)
            .addHeader("Authorization", "Basic " + getCredentials());
    }

    public BoundRequestBuilder preparePost(String url) {
        return asyncHttpClient.preparePost(url)
            .addHeader("Authorization", "Basic " + getCredentials());
    }

    private String getCredentials() {
        // use preference to retrieve user and password here
        return Base64.getEncoder()
            .encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
    }


}
