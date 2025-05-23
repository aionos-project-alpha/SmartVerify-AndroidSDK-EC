package com.aionos.smartverify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.aionos.smartverify.api.ApiService
import com.aionos.smartverify.model.AuthRequest
import com.aionos.smartverify.model.VerifyOtpRequest
import com.aionos.smartverify.model.Workflow
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class SmartVerify private constructor() {
    private val authApiService: ApiService =
        ApiClient.getInstance().create(ApiService::class.java)
    private var basicAuthService: ApiService? = null
    private val publicKeyString = """
        -----BEGIN PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA8I04B6voEYbDr+tnT91p
        rCsdFYxv7bdSRteACfr5Iam8EJXEpQIU6uAsE1uA/Gv1+CjH5olDDXP1P2wnQpXJ
        8R7Ktw5eCWt3TRfCIfW+EnacnsFlt7t0N8dHJWljE6bhgULbulpsS9QqX6ufjuCa
        h4LzavHYF2Do6q/1eQXKU7lyuuTV7BgXe05jRlOLvKAf4/s/oX9QJ60rQ1cpVd0x
        M0O9ZpzAZN9gGf/qD/7tXWbSHhLI+HXpGkCuoWDgNAMi+EtrOc2754MF7UuNDHO8
        fSYfiDmojCx0x4/b1fgS/iGgzDFGDfYYgHxm9rdcjOCTqImASyem18FY/1cDYhjj
        cQIDAQAB
        -----END PUBLIC KEY-----
        """

    companion object {
        private var instance: SmartVerify? = null

        fun getInstance(): SmartVerify {
            if (instance == null) {
                instance = SmartVerify()
            }
            return instance!!
        }
    }

    private fun encryptWithPublicKey(data: String): String {
        try {
            val cleanKey = publicKeyString
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")

            val publicKeyBytes = Base64.getDecoder().decode(cleanKey)
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey: PublicKey = keyFactory.generatePublic(keySpec)

            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            val oaepParams = OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
            )
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams)

            Log.e("DataToEncrypt SDK", data)

            val encryptedData = cipher.doFinal(data.toByteArray())

            Log.e("EncryptedData SDK", encryptedData.toString())
            return Base64.getEncoder().encodeToString(encryptedData)
        } catch (e: Exception) {
            Log.e("EncryptionError SDK", "Error encrypting mobile number: ${e.message}", e)
            return ""
        }
    }

    fun initBasicAuthService(clientId: String, clientSecret: String) {
        basicAuthService = ApiClient
            .getInstanceWithBasicAuth(clientId, clientSecret)
            .create(ApiService::class.java)
    }

    fun getToken(
        clientId: String,
        clientSecret: String,
        callback: ApiCallback
    ) {
        if (basicAuthService == null) {
            initBasicAuthService(clientId, clientSecret)
        }

        val call = basicAuthService?.getToken(clientId)
        call?.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful && response.body() != null) {
                    val responseBody = response.body()!!.string()

                    Log.e("Token ApiResponse SDK", "Response Body: $responseBody")

                    try {
                        val jsonObject = JSONObject(responseBody)
                        val token = jsonObject.getString("access_token")
                        Log.e("Token ApiResponse SDK", "Response Body: $token")

                        callback.onSuccess(token)
                    } catch (e: JSONException) {
                        Log.e("Token JSONParseError SDK", "Failed to parse token: ${e.message}")
                        callback.onError("Failed to parse token")
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    Log.e("ApiError SDK", "Error: ${response.message()}, Body: $errorBody")

                    callback.onError("Error: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("onTokenFailure SDK", "Failure: ${t.message}")
                callback.onError("Token Failure: ${t.message}")
            }
        })
    }

    fun authenticate(
        token: String,
        authRequest: AuthRequest,
        callback: ApiCallback
    ) {
        val encryptedWorkflow = authRequest.workflow.map { workflow ->
            val encryptedMobileNumber = encryptWithPublicKey(workflow.mobileNumberTo)
            workflow.copy(mobileNumberTo = encryptedMobileNumber)
        }
        val encryptedAuthRequest = authRequest.copy(workflow = encryptedWorkflow)
        Log.e("AuthRequest SDK", encryptedAuthRequest.toString())
        val call = authApiService.authenticate("Bearer $token", encryptedAuthRequest)
        Log.e("Auth call request SDK", "Response ${call.request()}")
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                Log.e("Auth onResponse SDK", "Response Code: ${response.code()}")
                Log.e("Auth onResponse SDK", "Response Headers: ${response.headers()}")
                if (response.isSuccessful && response.body() != null) {
                    val responseBody = response.body()!!.string()
                    Log.e("Auth ApiResponse SDK", "Response Body: $responseBody")
                    callback.onSuccess(responseBody)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    Log.e("Auth ApiError SDK", "Error: ${response.message()}, Body: $errorBody")
                    callback.onError("Error: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("Auth onFailure SDK", "Failure: ${t.message}")
                callback.onError("Failure: ${t.message}")
            }
        })
    }

    fun getStatus(
        token: String,
        txnId: String,
        callback: ApiCallback
    ) {
        val call = authApiService.getStatus("Bearer $token", txnId)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                Log.e("Retrofit SDK", "onResponse called")
                Log.e("onResponse SDK", "Response Code: ${response.code()}")
                Log.e("onResponse SDK", "Response Headers: ${response.headers()}")
                if (response.isSuccessful && response.body() != null) {
                    val responseBody = response.body()!!.string()
                    Log.e("ApiResponse SDK", "Response Body: $responseBody")
                    callback.onSuccess(responseBody)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    Log.e("ApiError SDK", "Error: ${response.message()}, Body: $errorBody")
                    callback.onError("Error: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("onFailure SDK", "Failure: ${t.message}")
                callback.onError("Failure: ${t.message}")
            }
        })
    }

    fun verifyOtp(
        token: String,
        txnId: String,
        otp: String,
        callback: ApiCallback
    ) {
        val encryptedOTP = encryptWithPublicKey(otp)
        val request = VerifyOtpRequest(txnId, encryptedOTP)

        val call = authApiService.verifyOtp("Bearer $token", request)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful && response.body() != null) {
                    val responseBody = response.body()!!.string()
                    Log.e("ApiResponse SDK", "Response Body: $responseBody")
                    callback.onSuccess(responseBody)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    Log.e("ApiError SDK", "Error: ${response.message()}, Body: $errorBody")
                    callback.onError("Error: ${response.message()}, Body: $errorBody")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("ApiFailure SDK", "Failure: ${t.message}")
                callback.onError("Failure: ${t.message}")
            }
        })
    }

    interface ApiCallback {
        fun onSuccess(result: String)
        fun onError(error: String)
    }

    fun bindToCellularNetwork(context: Context, onBound: (Network?) -> Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        cm.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cm.bindProcessToNetwork(network)
                onBound(network)
            }

            override fun onUnavailable() {
                onBound(null)
            }
        })
    }

    fun buildAuthRequest(
        number: String,
        isWifi: Boolean,
        isCellular: Boolean,
        cellularNetwork: String
    ): AuthRequest {
        return AuthRequest(
            brand = "IOH",
            workflow = listOf(
                Workflow(channel = "silent_auth", mobileNumberTo = number),
                Workflow(channel = "sms", mobileNumberTo = number),
            ),
            wifiEnabled = isWifi,
            cellularNetworkEnabled = isCellular,
            cellularNetwork = cellularNetwork
        )
    }

    fun getActiveDataSimOperator(context: Context, network: Network? = null): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Permission not granted"
        }

        val checkedNetwork = network ?: cm.activeNetwork ?: return "No active network"
        val capabilities = cm.getNetworkCapabilities(checkedNetwork) ?: return "No network capabilities"
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "Data not on cellular"
        }

        val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        val tmForData = tm.createForSubscriptionId(dataSubId)
        val operatorName = tmForData.networkOperatorName
        val subInfo = sm.getActiveSubscriptionInfo(dataSubId)

        return if (subInfo != null) {
            operatorName.ifEmpty { "Unknown operator" }
        } else {
            "No SIM info found for dataSubId: $dataSubId"
        }
    }

}
