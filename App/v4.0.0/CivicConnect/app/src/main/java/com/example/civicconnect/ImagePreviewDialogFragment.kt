package com.example.civicconnect

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.example.civicconnect.databinding.DialogImagePreviewBinding

class ImagePreviewDialogFragment : DialogFragment() {

    private var _binding: DialogImagePreviewBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_IMAGE_URL = "image_url"
        private const val TAG = "ImagePreviewDialog"

        fun newInstance(imageUrl: String): ImagePreviewDialogFragment {
            return ImagePreviewDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_URL, imageUrl)
                }
            }
        }

        fun show(host: androidx.fragment.app.Fragment, imageUrl: String) {
            newInstance(imageUrl).show(host.parentFragmentManager, TAG)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogImagePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageUrl = arguments?.getString(ARG_IMAGE_URL).orEmpty()

        Glide.with(this)
            .load(imageUrl)
            .fitCenter()
            .placeholder(R.drawable.ic_placeholder)
            .error(R.drawable.ic_placeholder)
            .into(binding.ivPreviewImage)

        binding.btnClosePreview.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.previewBackdrop.setOnClickListener {
            dismissAllowingStateLoss()
        }

        // Consume taps on the image so the user can inspect it without dismissing accidentally.
        binding.ivPreviewImage.setOnClickListener { }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
