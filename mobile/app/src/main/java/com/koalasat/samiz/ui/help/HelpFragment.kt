package com.koalasat.samiz.ui.help

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.koalasat.samiz.databinding.FragmentHelpBinding
import com.koalasat.samiz.util.AppSystem

class HelpFragment : Fragment() {
    private var _binding: FragmentHelpBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this).get(HelpViewModel::class.java)

        _binding = FragmentHelpBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.version.text = getAppVersion()

        return root
    }

    private fun getAppVersion(): String {
        return AppSystem.getAppVersion(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
