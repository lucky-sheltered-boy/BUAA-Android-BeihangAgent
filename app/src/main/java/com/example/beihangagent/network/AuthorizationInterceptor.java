package com.example.beihangagent.network;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthorizationInterceptor implements Interceptor {
    private String apiKey;

    public AuthorizationInterceptor(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder builder = original.newBuilder()
            .header("Content-Type", "application/json")
            .method(original.method(), original.body());

        String token = apiKey != null ? apiKey.trim() : "";
        if (!token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }

        Request request = builder.build();
        return chain.proceed(request);
    }
}
