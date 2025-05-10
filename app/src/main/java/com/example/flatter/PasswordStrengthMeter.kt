package com.example.flatter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText

/**
 * Indicador visual de la fuerza de la contraseña.
 * Muestra una barra de progreso con colores que cambian según la fuerza
 * y un texto descriptivo.
 */
class PasswordStrengthMeter @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Constantes para los niveles de fuerza
    companion object {
        const val STRENGTH_WEAK = 0
        const val STRENGTH_MEDIUM = 1
        const val STRENGTH_STRONG = 2
        const val STRENGTH_VERY_STRONG = 3
    }

    // Colores para cada nivel de fuerza
    private val colorWeak = Color.parseColor("#F44336")       // Rojo
    private val colorMedium = Color.parseColor("#FF9800")     // Naranja
    private val colorStrong = Color.parseColor("#4CAF50")     // Verde
    private val colorVeryStrong = Color.parseColor("#2196F3") // Azul

    // Textos descriptivos para cada nivel
    private val textWeak = "Débil"
    private val textMedium = "Media"
    private val textStrong = "Fuerte"
    private val textVeryStrong = "Muy fuerte"

    // Propiedades de dibujo
    private val paint = Paint()
    private val rectF = RectF()
    private var currentStrength = STRENGTH_WEAK
    private var currentProgress = 0.25f

    // Propiedades de texto asociado (opcional)
    private var strengthTextView: TextView? = null

    init {
        paint.isAntiAlias = true
    }

    /**
     * Establece la fuerza de la contraseña
     * @param strength El nivel de fuerza (0-3)
     */
    fun setStrength(strength: Int) {
        currentStrength = strength
        currentProgress = when (strength) {
            STRENGTH_WEAK -> 0.25f
            STRENGTH_MEDIUM -> 0.5f
            STRENGTH_STRONG -> 0.75f
            STRENGTH_VERY_STRONG -> 1.0f
            else -> 0.25f
        }
        invalidate()
        updateTextView()
    }

    /**
     * Asocia un TextView para mostrar el texto descriptivo
     */
    fun setStrengthTextView(textView: TextView) {
        this.strengthTextView = textView
        updateTextView()
    }

    /**
     * Asocia un campo de contraseña para analizarlo automáticamente
     */
    fun attachToPasswordField(editText: TextInputEditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()
                val strength = calculatePasswordStrength(password)
                setStrength(strength)
            }
        })
    }

    /**
     * Actualiza el texto descriptivo en el TextView asociado
     */
    private fun updateTextView() {
        strengthTextView?.let {
            val text = when (currentStrength) {
                STRENGTH_WEAK -> textWeak
                STRENGTH_MEDIUM -> textMedium
                STRENGTH_STRONG -> textStrong
                STRENGTH_VERY_STRONG -> textVeryStrong
                else -> textWeak
            }
            val color = when (currentStrength) {
                STRENGTH_WEAK -> colorWeak
                STRENGTH_MEDIUM -> colorMedium
                STRENGTH_STRONG -> colorStrong
                STRENGTH_VERY_STRONG -> colorVeryStrong
                else -> colorWeak
            }
            it.text = text
            it.setTextColor(color)
        }
    }

    /**
     * Calcula la fuerza de la contraseña basada en reglas simples
     */
    private fun calculatePasswordStrength(password: String): Int {
        if (password.isEmpty()) return STRENGTH_WEAK

        var score = 0

        // Longitud mínima
        if (password.length >= 8) score++
        if (password.length >= 12) score++

        // Complejidad
        if (password.any { it.isDigit() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        return when {
            score <= 2 -> STRENGTH_WEAK
            score <= 4 -> STRENGTH_MEDIUM
            score <= 6 -> STRENGTH_STRONG
            else -> STRENGTH_VERY_STRONG
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Dibujar fondo
        paint.color = Color.LTGRAY
        rectF.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rectF, height / 2f, height / 2f, paint)

        // Dibujar progreso
        paint.color = when (currentStrength) {
            STRENGTH_WEAK -> colorWeak
            STRENGTH_MEDIUM -> colorMedium
            STRENGTH_STRONG -> colorStrong
            STRENGTH_VERY_STRONG -> colorVeryStrong
            else -> colorWeak
        }

        // Ancho basado en la fuerza
        val progressWidth = width * currentProgress
        rectF.set(0f, 0f, progressWidth, height.toFloat())
        canvas.drawRoundRect(rectF, height / 2f, height / 2f, paint)
    }
}