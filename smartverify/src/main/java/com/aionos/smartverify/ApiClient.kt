package com.aionos.smartverify

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private var retrofit: Retrofit? = null
    private var authRetrofit: Retrofit? = null

    fun getInstance(): Retrofit {
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL+"id-auth/api/v1/id-auth/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }

    fun getInstanceWithBasicAuth(userId: String, password: String): Retrofit {
        val authInterceptor = Interceptor { chain ->
            val credential = Credentials.basic(userId, password)
            val newRequest = chain.request().newBuilder()
                .addHeader("Authorization", credential)
                .build()
            chain.proceed(newRequest)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        authRetrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL+"/oauth2-cc/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return authRetrofit!!
    }

}