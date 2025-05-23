package com.aionos.smartverify

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.aionos.smartverify.databinding.FragmentStatusBinding

class StatusFragment : Fragment() {

    private val binding by lazy {
        FragmentStatusBinding.inflate(layoutInflater)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

}