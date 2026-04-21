package com.example.civicconnect

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView

private var activeToast: Toast? = null

fun Context.showAppToast(message: String, isError: Boolean = false) {
    val hostContext = this

    Handler(Looper.getMainLooper()).post {
        activeToast?.cancel()

        val toastView = LayoutInflater.from(hostContext)
            .inflate(R.layout.view_app_toast, null, false)

        val card = toastView.findViewById<MaterialCardView>(R.id.toastCard)
        val iconContainer = toastView.findViewById<FrameLayout>(R.id.toastIconContainer)
        val icon = toastView.findViewById<ImageView>(R.id.ivToastIcon)
        val messageView = toastView.findViewById<TextView>(R.id.tvToastMessage)

        if (isError) {
            card.setBackgroundResource(R.drawable.bg_toast_error)
            iconContainer.setBackgroundResource(R.drawable.bg_toast_icon_error)
            icon.setImageResource(R.drawable.ic_toast_error)
        } else {
            card.setBackgroundResource(R.drawable.bg_toast_success)
            iconContainer.setBackgroundResource(R.drawable.bg_toast_icon_success)
            icon.setImageResource(R.drawable.ic_toast_success)
        }

        messageView.text = message

        val bottomOffset = (hostContext.resources.displayMetrics.density * 96).toInt()

        activeToast = Toast(hostContext.applicationContext).apply {
            duration = Toast.LENGTH_SHORT
            view = toastView
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, bottomOffset)
        }

        activeToast?.show()
    }
}

fun Fragment.showAppToast(message: String, isError: Boolean = false) {
    if (!isAdded) return
    requireContext().showAppToast(message, isError)
}

fun AppCompatActivity.showAppToast(message: String, isError: Boolean = false) {
    (this as Context).showAppToast(message, isError)
}
