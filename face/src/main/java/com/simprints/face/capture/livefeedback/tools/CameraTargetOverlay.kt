package com.simprints.face.capture.livefeedback.tools

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import com.simprints.core.tools.extentions.dpToPx

class CameraTargetOverlay(
    context: Context,
    attrs: AttributeSet
) : AppCompatImageView(context, attrs) {
    companion object {
        private val SEMI_TRANSPARENT_OVERLAY = Color.argb(102, 0, 0, 0)
        private val WHITE_OVERLAY = Color.argb(242, 255, 255, 255)
        private const val percentFromTop = 0.3f

        fun rectForPlane(width: Int, height: Int, rectSize: Float): RectF {
            val top = (height * percentFromTop) - (rectSize / 2)
            val bottom = top + rectSize

            val centerWidth = width / 2
            val left = centerWidth - (rectSize / 2)
            val right = left + rectSize

            return RectF(left, top, right, bottom)
        }
    }

    private var drawingFunc: (Canvas.() -> Unit)? = null
        set(value) {
            field = value
            postInvalidate()
        }

    private val rectSize = 240f.dpToPx(context)

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val circleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f.dpToPx(context)
        color = Color.argb(80, 255, 255, 255)
        strokeCap = Paint.Cap.ROUND
    }
    var rectInCanvas = RectF(0f, 0f, 0f, 0f)

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawingFunc?.invoke(canvas)
    }

    fun drawColorNoTarget(color: Int) {
        drawingFunc = {
            drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawColor(color, PorterDuff.Mode.SRC_OVER)
        }
    }

    fun drawSemiTransparentTarget() {
        drawingFunc = {
            drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawColor(SEMI_TRANSPARENT_OVERLAY, PorterDuff.Mode.SRC_OVER)
            drawTarget()
        }
    }

    fun drawWhiteTarget() {
        drawingFunc = {
            drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawColor(WHITE_OVERLAY, PorterDuff.Mode.SRC_OVER)
            drawTarget()
        }
    }

    private fun Canvas.drawTarget() {
        rectInCanvas = rectForPlane(width, height, rectSize)

        drawOval(rectInCanvas, circlePaint)
        drawOval(rectInCanvas, circleBorderPaint)
    }
}
