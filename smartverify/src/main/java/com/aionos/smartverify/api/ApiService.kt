package com.aionos.smartverify.api

import com.aionos.smartverify.frameworks.common.Constants.KEY_AUTHENTICATION
import com.aionos.smartverify.model.AuthRequest
import com.aionos.smartverify.model.VerifyOtpRequest
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query


interface ApiService {

    @FormUrlEncoded
    @POST("jwt")
    fun getToken(
        @Query("apikey") clientId: String,
        @Field("grant_type") grantType: String = "client_credentials"
    ): Call<ResponseBody>

    @POST("authenticate")
    fun authenticate(
        @Header(KEY_AUTHENTICATION) token: String,
        @Body request: AuthRequest
    ): Call<ResponseBody>


    @GET("status/{txnId}")
    fun getStatus(
        @Header(KEY_AUTHENTICATION) token: String,
        @Path("txnId") txnId: String
    ): Call<ResponseBody>

    @POST("verifyOtp")
    fun verifyOtp(
        @Header(KEY_AUTHENTICATION) token: String,
        @Body request: VerifyOtpRequest
    ): Call<ResponseBody>

}