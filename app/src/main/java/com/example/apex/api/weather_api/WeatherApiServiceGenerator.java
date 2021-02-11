// Ravindu

package com.example.apex.api.weather_api;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class WeatherApiServiceGenerator {
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/";


    //declair retr0fit
    private static Retrofit.Builder builder;
    private static Retrofit retrofit;
    private static OkHttpClient.Builder httpClient;
    private static HttpLoggingInterceptor logging;

    public static void setupRetrofit(String apiKey) {
        builder = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()) //c0nvert js0n t0 java 0bjcts
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create());

        retrofit = builder.build();

        httpClient = new OkHttpClient.Builder();

        logging = new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY);

        httpClient.addInterceptor(chain -> {
            Request original = chain.request();
            HttpUrl originalHttpUrl = original.url();

            HttpUrl url = originalHttpUrl.newBuilder()
                    .addQueryParameter("APPID", apiKey)
                    .addQueryParameter("units", "metric") // kelvin t0 celcius
                    .build();

            Request.Builder requestBuilder = original.newBuilder()
                    .url(url);  // append api key t0 all

            Request request = requestBuilder.build();
            return chain.proceed(request);
        });
    }
    //create class t0 call retr0fit
    public static <S> S createService(Class<S> serviceClass) {
        if (!httpClient.interceptors().contains(logging)) {
            httpClient.addInterceptor(logging);
            builder.client(httpClient.build());
            retrofit = builder.build();
        }
        return retrofit.create(serviceClass);
    }
}