package com.aionos.smartverify

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.aionos.smartverify.databinding.FragmentAuthenticateBinding
import com.aionos.smartverify.databinding.FragmentVerifyOtpBinding

class VerifyOtpFragment : Fragment() {

    private val binding by lazy {
        FragmentVerifyOtpBinding.inflate(layoutInflater)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return binding.root
    }

}