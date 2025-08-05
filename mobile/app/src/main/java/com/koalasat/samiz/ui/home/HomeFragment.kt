package com.koalasat.samiz.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.koalasat.samiz.R
import com.koalasat.samiz.Samiz
import com.koalasat.samiz.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val homeViewModel =
            ViewModelProvider(this)[HomeViewModel::class.java]

        binding.btnStart.setOnClickListener {
            if (Samiz.isEnabled.value == true) {
                Samiz.getInstance().stopService()
            } else {
                Samiz.getInstance().startService()
            }
        }

        // Update button text based on current service state
        Samiz.isEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.btnStart.text =
                if (isEnabled == true) {
                    getString(R.string.stop_session)
                } else {
                    getString(R.string.start_session)
                }

            if (isEnabled) {
                if (binding.logoForeground.animation == null) {
                    val rotateAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.rotate_animation)
                    binding.logoForeground.startAnimation(rotateAnimation)
                }
            } else {
                binding.logoForeground.clearAnimation()
            }
        }

        binding.eventReceivedCount.text = getString(R.string.eventReceivedCount, Samiz.receivedEvents.value)
        binding.eventSentCount.text = getString(R.string.eventSentCount, Samiz.sentEvents.value)
        binding.foundDevicesCount.text =
            getString(
                R.string.foundDevicesCount,
                Samiz.foundDevices.value?.size ?: 0,
            )

        Samiz.receivedEvents.observe(viewLifecycleOwner) {
            binding.eventReceivedCount.text = getString(R.string.eventReceivedCount, Samiz.receivedEvents.value)
        }

        Samiz.sentEvents.observe(viewLifecycleOwner) {
            binding.eventSentCount.text = getString(R.string.eventSentCount, Samiz.sentEvents.value)
        }

        Samiz.foundDevices.observe(viewLifecycleOwner) {
            binding.foundDevicesCount.text =
                getString(
                    R.string.foundDevicesCount,
                    Samiz.foundDevices.value?.size ?: 0,
                )
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
