package com.koalasat.samiz.ui.logs

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.koalasat.samiz.databinding.FragmentLogsBinding
import com.koalasat.samiz.model.Logger

class LogsFragment : Fragment() {
    private var _binding: FragmentLogsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val notificationsViewModel =
            ViewModelProvider(this).get(LogsViewModel::class.java)

        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        Logger.logMessages.observe(viewLifecycleOwner) { logs ->
            displayLogs(logs)
            scrollToBottom()
        }

        return root
    }

    private fun displayLogs(logs: StringBuilder) {
        val spannableStringBuilder = SpannableStringBuilder()

        logs.split("\n").takeLast(50).forEach { log ->
            val logLevel = log.substringBefore(":")
            val coloredLog = SpannableStringBuilder(log)
            val secondColonIndex = log.indexOf(":", log.indexOf(":") + 1)
            when (logLevel) {
                "INFO" -> {
                    coloredLog.setSpan(ForegroundColorSpan(Color.parseColor("#257180")), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    coloredLog.setSpan(
                        ForegroundColorSpan(Color.parseColor("#FFBB86FC")),
                        6,
                        secondColonIndex,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                "ERROR" -> {
                    coloredLog.setSpan(ForegroundColorSpan(Color.RED), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    coloredLog.setSpan(
                        ForegroundColorSpan(Color.parseColor("#FFBB86FC")),
                        7,
                        secondColonIndex,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                else -> {
                    // No specific color for other log levels
                }
            }

            spannableStringBuilder.append(coloredLog)
            spannableStringBuilder.append("\n")
        }

        binding.textLogs.text = spannableStringBuilder
    }

    private fun scrollToBottom() {
        binding.root.post {
            binding.scrollView.fullScroll(View.FOCUS_DOWN) // Scroll to the bottom
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
