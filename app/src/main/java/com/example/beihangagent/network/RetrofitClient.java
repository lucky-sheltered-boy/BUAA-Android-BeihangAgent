package com.example.beihangagent.network;

import com.example.beihangagent.BuildConfig;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

        private static final String BASE_URL = BuildConfig.API_BASE_URL != null && !BuildConfig.API_BASE_URL.isEmpty()
            ? BuildConfig.API_BASE_URL
            : "https://api.openai.com/";
        private static final String API_KEY = BuildConfig.AI_API_KEY != null ? BuildConfig.AI_API_KEY : "";
    private static RetrofitClient instance;
    private Retrofit retrofit;
    private ApiService apiService;

    private RetrofitClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(new AuthorizationInterceptor(API_KEY))
                .connectTimeout(60, TimeUnit.SECONDS) // Increase timeout to 60 seconds
                .readTimeout(120, TimeUnit.SECONDS) // Increase read timeout to 120 seconds
                .writeTimeout(60, TimeUnit.SECONDS) // Increase write timeout to 60 seconds
                .build();

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
        
        apiService = retrofit.create(ApiService.class);
        
        // Log configuration for debugging
        System.out.println("RetrofitClient initialized:");
        System.out.println("BASE_URL: " + BASE_URL);
        System.out.println("API_KEY: " + (API_KEY.isEmpty() ? "Empty" : "Set (length: " + API_KEY.length() + ")"));
    }

    public static synchronized RetrofitClient getInstance() {
        if (instance == null) {
            instance = new RetrofitClient();
        }
        return instance;
    }

    public ApiService getApiService() {
        return apiService;
    }
}
