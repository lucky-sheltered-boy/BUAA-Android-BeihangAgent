package com.example.beihangagent.network;

import com.example.beihangagent.model.ChatRequest;
import com.example.beihangagent.model.ChatResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("v1/chat/completions")
    Call<ChatResponse> sendMessage(@Body ChatRequest request);
}
