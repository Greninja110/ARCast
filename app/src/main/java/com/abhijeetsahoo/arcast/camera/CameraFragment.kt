package com.example.arcamerastreamer.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.arcamerastreamer.MainViewModel
import com.example.arcamerastreamer.databinding.FragmentCameraBinding

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup UI and observe ViewModel
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        // UI setup will be implemented in Step 2
    }

    private fun observeViewModel() {
        // Observe LiveData from ViewModel
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}