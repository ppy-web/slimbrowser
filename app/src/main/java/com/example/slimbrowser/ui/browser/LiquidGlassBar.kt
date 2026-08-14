package com.example.slimbrowser.ui.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import kotlin.math.max

/**
 * A bottom bar that samples the content behind it and turns it into a frosted,
 * refractive surface. The shader path is intentionally API 33+; older Android
 * releases retain the same shape and contrast with a static glass fallback.
 */
class LiquidGlassBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private var cornerRadius = 28f * density
    private val updateIntervalMillis = 90L
    private var liveBackdropUpdates = true
    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, density)
        color = Color.argb(150, 255, 255, 255)
    }
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var backdropSource: View? = null
    private var snapshot: Bitmap? = null
    private var lastSnapshotAt = 0L
    private var glassShader: RuntimeShader? = null

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (liveBackdropUpdates && isShown) {
                invalidate()
                postDelayed(this, updateIntervalMillis)
            }
        }
    }

    init {
        setWillNotDraw(false)
        clipToOutline = true
        outlineProvider = ViewOutlineProviderCompat(cornerRadius)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = this@LiquidGlassBar.cornerRadius
            setColor(Color.TRANSPARENT)
        }
        elevation = 10f * density
    }

    /** The view hierarchy to sample. It must not contain this bar. */
    fun setBackdropSource(source: View) {
        backdropSource = source
        snapshot?.recycle()
        snapshot = null
        lastSnapshotAt = 0L
        invalidate()
    }

    fun setGlassCornerRadiusDp(radius: Float) {
        cornerRadius = radius * density
        outlineProvider = ViewOutlineProviderCompat(cornerRadius)
        (background as? GradientDrawable)?.cornerRadius = cornerRadius
        invalidateOutline()
        invalidate()
    }

    fun setLiveBackdropUpdates(enabled: Boolean) {
        liveBackdropUpdates = enabled
        removeCallbacks(refreshRunnable)
        if (enabled && isAttachedToWindow) post(refreshRunnable)
    }

    fun refreshBackdrop() {
        lastSnapshotAt = 0L
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(refreshRunnable)
        if (liveBackdropUpdates) post(refreshRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refreshRunnable)
        snapshot?.recycle()
        snapshot = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        removeCallbacks(refreshRunnable)
        if (visibility == VISIBLE && liveBackdropUpdates) post(refreshRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        updateSnapshotIfDue()
        val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canvas.isHardwareAccelerated) {
            createRefractionShader(width, height)
        } else {
            null
        }
        if (shader != null) {
            backdropPaint.shader = shader
            canvas.drawRoundRect(0f, 0f, width, height, cornerRadius, cornerRadius, backdropPaint)
            backdropPaint.shader = null
        } else {
            drawFallback(canvas, width, height)
        }
        canvas.drawRoundRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, cornerRadius, cornerRadius, outlinePaint)
    }

    private fun updateSnapshotIfDue() {
        if (System.currentTimeMillis() - lastSnapshotAt < updateIntervalMillis) return
        val source = backdropSource ?: return
        if (width <= 0 || height <= 0 || source.width <= 0 || source.height <= 0) return

        val bitmap = snapshot?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { snapshot = it }
        val sourceLocation = IntArray(2)
        val barLocation = IntArray(2)
        source.getLocationOnScreen(sourceLocation)
        getLocationOnScreen(barLocation)

        Canvas(bitmap).apply {
            drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            save()
            translate(
                (sourceLocation[0] - barLocation[0]).toFloat(),
                (sourceLocation[1] - barLocation[1]).toFloat(),
            )
            source.draw(this)
            restore()
        }
        lastSnapshotAt = System.currentTimeMillis()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun createRefractionShader(width: Float, height: Float): RuntimeShader? {
        val bitmap = snapshot ?: return null
        return runCatching {
            val runtimeShader = glassShader ?: RuntimeShader(LIQUID_GLASS_SHADER).also { glassShader = it }
            runtimeShader.setFloatUniform("resolution", width, height)
            runtimeShader.setInputShader(
                "backdrop",
                BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
            )
            runtimeShader
        }.onFailure {
            Log.w(TAG, "Liquid glass shader unavailable; using the glass fallback.", it)
        }.getOrNull()
    }

    private fun drawFallback(canvas: Canvas, width: Float, height: Float) {
        snapshot?.let { bitmap ->
            backdropPaint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(0f, 0f, width, height, cornerRadius, cornerRadius, backdropPaint)
            backdropPaint.shader = null
        }
        fallbackPaint.shader = LinearGradient(
            0f,
            0f,
            width,
            height,
            intArrayOf(
                Color.argb(114, 244, 249, 255),
                Color.argb(82, 174, 207, 238),
                Color.argb(106, 255, 255, 255),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(0f, 0f, width, height, cornerRadius, cornerRadius, fallbackPaint)
        fallbackPaint.shader = null
    }

    private companion object {
        const val TAG = "LiquidGlassBar"
        // The source is a live crop of the content below the bar. A radial lens,
        // three nearby samples and split RGB channels create refraction, frost and
        // a restrained chromatic edge without animating the user's controls.
        const val LIQUID_GLASS_SHADER = """
            uniform shader backdrop;
            uniform float2 resolution;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / resolution;
                float2 centered = uv - float2(0.5, 0.5);
                centered.x *= resolution.x / resolution.y;
                float distance = length(centered);
                float lens = 1.0 - smoothstep(0.05, 0.78, distance);
                float ripple = sin(uv.y * 22.0 + uv.x * 7.0) * 1.7 * lens;
                float2 samplePoint = fragCoord + centered * (15.0 * lens * lens) + float2(ripple, -ripple * 0.35);

                half4 base = backdrop.eval(samplePoint);
                half4 left = backdrop.eval(samplePoint + float2(-3.2, 1.2));
                half4 right = backdrop.eval(samplePoint + float2(3.2, -1.2));
                half3 frosted = base.rgb * 0.56 + left.rgb * 0.22 + right.rgb * 0.22;
                half3 refracted = half3(right.r, frosted.g, left.b);
                float sheen = 0.13 + 0.11 * (1.0 - uv.y) + 0.05 * lens;
                half3 glass = mix(refracted, half3(1.0), sheen);
                return half4(glass, 0.88);
            }
        """
    }
}

/** Avoids a drawable resource while keeping clipping consistent on all API levels. */
private class ViewOutlineProviderCompat(private val radius: Float) : android.view.ViewOutlineProvider() {
    override fun getOutline(view: View, outline: android.graphics.Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
}
