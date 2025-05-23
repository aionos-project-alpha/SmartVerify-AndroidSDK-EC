package com.aionos.smartverify

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.aionos.smartverify.databinding.FragmentAuthenticateBinding
import com.aionos.smartverify.model.AuthRequest
import com.aionos.smartverify.model.Workflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.Boolean.FALSE

class AuthenticateFragment : Fragment() {

    private val binding by lazy {
        FragmentAuthenticateBinding.inflate(layoutInflater)
    }

    private var token: String = ""
    val sdk = SmartVerify.getInstance()
    var isCellular: Boolean = false
    var isWifi: Boolean = false
    private var boundNetwork: Network? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        getToken()
        binding.btnApiCall.setOnClickListener {
            networkcheck()
        }
    }

    private fun networkcheck() {
        isWifi = NetworkUtils.isWifiConnected(requireContext())
        isCellular = NetworkUtils.isCellularConnected(requireContext())
        Log.e("NetworkCheck", "Wifi: $isWifi, Cellular: $isCellular")

        if (isWifi) {
            Log.e("NetworkCheck", "Wi-Fi is connected")
            Toast.makeText(requireContext(), "Wi-Fi is connected", Toast.LENGTH_SHORT).show()
            AlertDialog.Builder(requireContext())
                .setTitle("Switch to Mobile Data")
                .setMessage("Wi-Fi is connected. Would you like to switch to mobile data?")
                .setPositiveButton("Yes") { _, _ ->
                    preferMobileDataForApp()
                }
                .setNegativeButton("No", null)
                .show()
        } else if (isCellular) {
            Log.e("NetworkCheck", "Cellular is connected")
            Toast.makeText(requireContext(), "Cellular is connected", Toast.LENGTH_SHORT).show()
            authenticate()
        } else {
            Log.e("NetworkCheck", "No network connection")
            Toast.makeText(requireContext(), "No network connection", Toast.LENGTH_SHORT).show()
        }
    }

    private fun preferMobileDataForApp() {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                connectivityManager.bindProcessToNetwork(network)
                boundNetwork = network // Save for future unbinding if needed
                Log.d("NetworkCheck", "App bound to cellular data")

                Toast.makeText(requireContext(), "Using mobile data for this session", Toast.LENGTH_SHORT).show()

                authenticate()
            }

            override fun onUnavailable() {
                super.onUnavailable()
                Log.e("NetworkCheck", "Cellular network unavailable")
            }
        })
    }

    private fun getToken() {
        sdk.getToken(
            "apRYwbyYh6ZRbjAsOGPYiGDwWRoTXUe0w8i10slLxsW8WuFf",
            "a54FKl22FLCN1Y9su3A1DaUO9WrBqkCh62e1OkbvSkiLsyLubuvkJSIhcb5u5HNz",
            object : SmartVerify.ApiCallback {
                override fun onSuccess(result: String) {
                    Log.e("Token fetched Successful: ", result)
                    token = result
                    PreferenceHelper.saveToken(requireContext(), token)
                }

                override fun onError(error: String) {
                    Log.e("Authentication Failed: ", error)
                }
            })
    }

    /*private fun authenticate() {
        // Log current network transport type
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        val activeNetwork = connectivityManager.activeNetwork
//        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        val capabilities = connectivityManager.getNetworkCapabilities(boundNetwork)
        val isUsingMobile = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isUsingWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        Log.d("Authenticate", "Bound to Cellular: $isUsingMobile, Bound to Wi-Fi: $isUsingWifi")

//        val isUsingMobile = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
//        val isUsingWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        Log.d("Authenticate", "Using Mobile Data: $isUsingMobile, Using Wi-Fi: $isUsingWifi")

        val authRequest = AuthRequest(
            brand = "IOH",
            workflow = listOf(
                Workflow(channel = "silent_auth", mobileNumberTo = binding.number.text.toString()),
                Workflow(channel = "sms", mobileNumberTo = binding.number.text.toString()),
            ),
            wifiEnabled = false,
            cellularNetworkEnabled = true,
            cellularNetwork = "TSEL"
        )

        Log.e("Authentication Request", authRequest.toString())

        sdk.authenticate(
            PreferenceHelper.getToken(requireContext())!!,
            authRequest,
            object : SmartVerify.ApiCallback {
                override fun onSuccess(result: String) {
                    Log.e("Authentication Successful", result)
                    val jsonResponse = JSONObject(result)
                    val txnId = jsonResponse.getString("txnId")

                    if (jsonResponse.has("redirectionUrl")) {
                        val redirectionUrl = jsonResponse.getString("redirectionUrl")

                        binding.webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                CoroutineScope(Dispatchers.Main).launch {
                                    sdk.getStatus(token, txnId, object : SmartVerify.ApiCallback {
                                        override fun onSuccess(result: String) {
                                            Log.e("Status Fetch Successful:", result)
                                            val statusResponse = JSONObject(result)
                                            val status = statusResponse.getString("status")
                                            val message = statusResponse.getString("message")

                                            when {
                                                status == "true" -> {
                                                    Toast.makeText(requireContext(), "Authenticated", Toast.LENGTH_SHORT).show()
                                                }
                                                message == "OTP_SENT" -> {
                                                    Toast.makeText(requireContext(), "OTP Sent", Toast.LENGTH_SHORT).show()
                                                }
                                                status == "false" && !message.isNullOrEmpty() -> {
                                                    Toast.makeText(requireContext(), "Authentication Failed", Toast.LENGTH_SHORT).show()
                                                    Log.e("Auth:", "Status is false")
                                                }
                                            }
                                        }

                                        override fun onError(error: String) {
                                            Log.e("Auth error:", error)
                                        }
                                    })
                                }
                            }
                        }

                        binding.webView.loadUrl(redirectionUrl)
                    } else {
                        Toast.makeText(requireContext(), "Authentication failed", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onError(error: String) {
                    Log.e("Auth error:", error)
                }
            }
        )
    }*/

    private fun authenticate() {
        val authRequest = AuthRequest(
            brand = "IOH",
            workflow = listOf(
                Workflow(channel = "silent_auth", mobileNumberTo = binding.number.text.toString()),
                Workflow(channel = "sms", mobileNumberTo = binding.number.text.toString()),
            ),
            wifiEnabled = false,
            cellularNetworkEnabled = true,
            cellularNetwork = "TSEL"
        )

        Log.e("Authentication Request", authRequest.toString())

        sdk.authenticate(
            PreferenceHelper.getToken(requireContext())!!,
            authRequest,
            object : SmartVerify.ApiCallback {
                override fun onSuccess(result: String) {
                    Log.e("Authentication Successful", result)
                    val jsonResponse = JSONObject(result)
                    val txnId = jsonResponse.getString("txnId")

                    if (jsonResponse.has("redirectionUrl")) {
                        val redirectionUrl = jsonResponse.getString("redirectionUrl")

                        binding.webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                CoroutineScope(Dispatchers.Main).launch {
                                    sdk.getStatus(token, txnId, object : SmartVerify.ApiCallback {
                                        override fun onSuccess(result: String) {
                                            Log.e("Status Fetch Successful:", result)
                                            val statusResponse = JSONObject(result)
                                            val status = statusResponse.getString("status")
                                            val message = statusResponse.getString("message")

                                            when {
                                                status == "true" -> {
                                                    Toast.makeText(requireContext(), "Authenticated", Toast.LENGTH_SHORT).show()
                                                }
                                                message == "OTP_SENT" -> {
                                                    Toast.makeText(requireContext(), "OTP Sent", Toast.LENGTH_SHORT).show()
                                                }
                                                status == "false" && !message.isNullOrEmpty() -> {
                                                    Toast.makeText(requireContext(), "Authentication Failed", Toast.LENGTH_SHORT).show()
                                                    Log.e("Auth:", "Status is false")
                                                }
                                            }
                                        }

                                        override fun onError(error: String) {
                                            Log.e("Auth error:", error)
                                        }
                                    })
                                }
                            }
                        }

                        binding.webView.loadUrl(redirectionUrl)
                    } else {
                        Toast.makeText(requireContext(), "Authentication failed", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onError(error: String) {
                    Log.e("Auth error:", error)
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Optional: Unbind from mobile network when the fragment is destroyed
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.bindProcessToNetwork(null)
        boundNetwork = null
    }
}


