package com.example.flatter.utils

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.flatter.R

class FlatterToast {

    companion object {

        /**
         * Show a custom toast with Flatter logo
         * @param context The context
         * @param message The message to display
         * @param duration Toast.LENGTH_SHORT or Toast.LENGTH_LONG
         */
        fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
            try {
                val inflater = LayoutInflater.from(context)
                val layout = inflater.inflate(R.layout.custom_toast_layout, null)

                // Set the message text
                val textView = layout.findViewById<TextView>(R.id.tvToastMessage)
                textView.text = message

                // Set the logo (you can customize this if needed)
                val logoView = layout.findViewById<ImageView>(R.id.ivToastLogo)
                logoView.setImageResource(R.drawable.logo_flatter)

                // Create and configure the toast
                val toast = Toast(context)
                toast.duration = duration
                toast.view = layout
                toast.setGravity(Gravity.CENTER, 0, 0)

                toast.show()
            } catch (e: Exception) {
                // Fallback to regular toast if custom toast fails
                Toast.makeText(context, message, duration).show()
            }
        }

        /**
         * Show a short toast with Flatter logo
         */
        fun showShort(context: Context, message: String) {
            showToast(context, message, Toast.LENGTH_SHORT)
        }

        /**
         * Show a long toast with Flatter logo
         */
        fun showLong(context: Context, message: String) {
            showToast(context, message, Toast.LENGTH_LONG)
        }

        /**
         * Show a success toast with Flatter logo and green styling
         */
        fun showSuccess(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
            try {
                val inflater = LayoutInflater.from(context)
                val layout = inflater.inflate(R.layout.custom_toast_success_layout, null)

                val textView = layout.findViewById<TextView>(R.id.tvToastMessage)
                textView.text = message

                val logoView = layout.findViewById<ImageView>(R.id.ivToastLogo)
                logoView.setImageResource(R.drawable.logo_flatter)

                val toast = Toast(context)
                toast.duration = duration
                toast.view = layout
                toast.setGravity(Gravity.CENTER, 0, 0)

                toast.show()
            } catch (e: Exception) {
                Toast.makeText(context, message, duration).show()
            }
        }

        /**
         * Show an error toast with Flatter logo and red styling
         */
        fun showError(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
            try {
                val inflater = LayoutInflater.from(context)
                val layout = inflater.inflate(R.layout.custom_toast_error_layout, null)

                val textView = layout.findViewById<TextView>(R.id.tvToastMessage)
                textView.text = message

                val logoView = layout.findViewById<ImageView>(R.id.ivToastLogo)
                logoView.setImageResource(R.drawable.logo_flatter)

                val toast = Toast(context)
                toast.duration = duration
                toast.view = layout
                toast.setGravity(Gravity.CENTER, 0, 0)

                toast.show()
            } catch (e: Exception) {
                Toast.makeText(context, message, duration).show()
            }
        }
    }
}