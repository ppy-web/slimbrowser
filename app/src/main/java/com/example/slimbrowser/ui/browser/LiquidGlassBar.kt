package com.example.slimbrowser.ui.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.example.slimbrowser.R
import kotlin.math.max

/**
 * A compact glass surface for application controls.
 *
 * API 31+ can blur a one-shot crop of the content behind the surface. Older
 * releases deliberately use a static translucent gradient: they never redraw
 * the WebView on a timer, which keeps scrolling predictable on low-end devices.
 */
class LiquidGlassBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private var cornerRadius = resources.getDimension(R.dimen.glass_corner_radius)
    private val blurRadius = resources.getDimension(R.dimen.glass_blur_radius)
    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(resources.getDimension(R.dimen.glass_outline_width), 1f)
    }

    private var backdropSource: View? = null
    private var snapshot: Bitmap? = null
    private var snapshotDirty = true
    private var backdropSamplingEnabled = true
    private var reducedTransparency = false

    init {
        setWillNotDraw(false)
        clipToOutline = true
        outlineProvider = RoundedOutlineProvider(cornerRadius)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = this@LiquidGlassBar.cornerRadius
            setColor(Color.TRANSPARENT)
        }
        elevation = resources.getDimension(R.dimen.glass_elevation)
    }

    /** The view hierarchy sampled once for API 31+ blur. It must not contain this view. */
    fun setBackdropSource(source: View) {
        if (backdropSource === source) {
            refreshBackdrop()
            return
        }
        backdropSource = source
        discardSnapshot()
        snapshotDirty = true
        invalidate()
    }

    fun setGlassCornerRadiusDp(radius: Float) {
        cornerRadius = radius * density
        outlineProvider = RoundedOutlineProvider(cornerRadius)
        (background as? GradientDrawable)?.cornerRadius = cornerRadius
        invalidateOutline()
        invalidate()
    }

    /**
     * Kept for source compatibility with the original prototype. Passing false
     * disables backdrop sampling; true enables one-shot sampling, never a
     * continuous redraw loop.
     */
    fun setLiveBackdropUpdates(enabled: Boolean) {
        backdropSamplingEnabled = enabled
        if (!enabled) discardSnapshot() else snapshotDirty = true
        invalidate()
    }

    fun setReducedTransparency(enabled: Boolean) {
        if (reducedTransparency == enabled) return
        reducedTransparency = enabled
        invalidate()
    }

    fun refreshBackdrop() {
        snapshotDirty = true
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth || height != oldHeight) {
            discardSnapshot()
            snapshotDirty = true
        }
    }

    override fun onDetachedFromWindow() {
        discardSnapshot()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val drawWidth = width.toFloat()
        val drawHeight = height.toFloat()
        if (drawWidth <= 0f || drawHeight <= 0f) return

        val drewBackdrop = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            canvas.isHardwareAccelerated &&
            backdropSamplingEnabled
        ) {
            updateSnapshotIfNeeded()
            drawBlurredBackdrop(canvas, drawWidth, drawHeight)
        } else {
            false
        }
        drawGlassTint(canvas, drawWidth, drawHeight, backdropVisible = drewBackdrop)
        drawOutline(canvas, drawWidth, drawHeight)
    }

    private fun updateSnapshotIfNeeded() {
        if (!snapshotDirty) return
        snapshotDirty = false
        val source = backdropSource ?: return
        if (width <= 0 || height <= 0 || source.width <= 0 || source.height <= 0) return

        val candidate = createBitmap(width, height)
        val sourceLocation = IntArray(2)
        val barLocation = IntArray(2)
        source.getLocationOnScreen(sourceLocation)
        getLocationOnScreen(barLocation)
        val captured = runCatching {
            Canvas(candidate).withSave {
                drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                translate(
                    (sourceLocation[0] - barLocation[0]).toFloat(),
                    (sourceLocation[1] - barLocation[1]).toFloat(),
                )
                source.draw(this)
            }
        }.isSuccess
        if (captured) {
            discardSnapshot()
            snapshot = candidate
        } else {
            candidate.recycle()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun drawBlurredBackdrop(canvas: Canvas, drawWidth: Float, drawHeight: Float): Boolean {
        val bitmap = snapshot ?: return false
        backdropPaint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val renderNode = RenderNode("SlimBrowserGlassBackdrop").apply {
            setPosition(0, 0, drawWidth.toInt(), drawHeight.toInt())
            val recordingCanvas = beginRecording()
            recordingCanvas.drawRect(0f, 0f, drawWidth, drawHeight, backdropPaint)
            endRecording()
            setRenderEffect(RenderEffect.createBlurEffect(
                blurRadius,
                blurRadius,
                Shader.TileMode.CLAMP,
            ))
        }
        canvas.drawRenderNode(renderNode)
        backdropPaint.shader = null
        return true
    }

    private fun drawGlassTint(
        canvas: Canvas,
        drawWidth: Float,
        drawHeight: Float,
        backdropVisible: Boolean,
    ) {
        val colors = when {
            reducedTransparency -> intArrayOf(
                color(R.color.glass_opaque_start),
                color(R.color.glass_opaque_end),
            )
            backdropVisible -> intArrayOf(
                color(R.color.glass_blurred_start),
                color(R.color.glass_blurred_end),
            )
            else -> intArrayOf(
                color(R.color.glass_fallback_start),
                color(R.color.glass_fallback_end),
            )
        }
        tintPaint.shader = LinearGradient(
            0f,
            0f,
            drawWidth,
            drawHeight,
            colors,
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(0f, 0f, drawWidth, drawHeight, cornerRadius, cornerRadius, tintPaint)
        tintPaint.shader = null
    }

    private fun drawOutline(canvas: Canvas, drawWidth: Float, drawHeight: Float) {
        outlinePaint.color = color(R.color.glass_outline)
        val inset = outlinePaint.strokeWidth / 2f
        canvas.drawRoundRect(
            inset,
            inset,
            drawWidth - inset,
            drawHeight - inset,
            cornerRadius,
            cornerRadius,
            outlinePaint,
        )
    }

    private fun color(resourceId: Int): Int = ContextCompat.getColor(context, resourceId)

    private fun discardSnapshot() {
        snapshot?.takeUnless(Bitmap::isRecycled)?.recycle()
        snapshot = null
    }
}

private class RoundedOutlineProvider(private val radius: Float) : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
}
