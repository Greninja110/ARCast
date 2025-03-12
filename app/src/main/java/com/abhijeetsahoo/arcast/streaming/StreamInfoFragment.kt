package com.abhijeetsahoo.arcast.streaming

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.arcamerastreamer.MainViewModel
import com.example.arcamerastreamer.databinding.FragmentStreamInfoBinding

class StreamInfoFragment : Fragment() {

    private var _binding: FragmentStreamInfoBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStreamInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Will be implemented in Steps 4-6
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}