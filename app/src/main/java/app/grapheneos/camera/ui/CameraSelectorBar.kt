package app.grapheneos.camera.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.Camera
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import kotlin.math.abs

class CameraSelectorBar : LinearLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    companion object {
        private val STOPS = floatArrayOf(0.5f, 1f, 2f, 5f, 10f)
        private const val MATCH_TOLERANCE = 0.05f
        private const val ZOOM_RANGE_EPSILON = 0.01f
        private const val ULTRAWIDE_THRESHOLD = 0.95f
        private const val MAX_INCLUSION_TOLERANCE = 0.05f
        private const val FRACTIONAL_FORMAT = "%.1fx"
        private const val WHOLE_FORMAT = "%.0fx"
    }

    private lateinit var mainActivity: MainActivity

    private var stops: FloatArray = floatArrayOf()
    private val pills = mutableListOf<TextView>()

    private var cachedMin = Float.NaN
    private var cachedMax = Float.NaN
    private var lastFormattedRatio = Float.NaN
    private var lastFormattedString = ""

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun setMainActivity(activity: MainActivity) {
        mainActivity = activity
    }

    fun update(camera: Camera) {
        val state = camera.cameraInfo.zoomState.value ?: return
        val min = state.minZoomRatio
        val max = state.maxZoomRatio

        if (min != cachedMin || max != cachedMax) {
            cachedMin = min
            cachedMax = max
            stops = computeStops(min, max)
            rebuildPills()
            visibility = if (stops.size <= 1 || (max - min) < ZOOM_RANGE_EPSILON) GONE else VISIBLE
        }

        updateActivePill(state.zoomRatio)
    }

    private fun computeStops(min: Float, max: Float): FloatArray {
        val hasUltrawide = min < ULTRAWIDE_THRESHOLD
        return STOPS.filter { stop ->
            when {
                stop < 1f -> hasUltrawide
                stop == 1f -> min <= 1f && 1f <= max
                else -> stop <= max + MAX_INCLUSION_TOLERANCE
            }
        }.toFloatArray()
    }

    private fun updateActivePill(current: Float) {
        if (stops.isEmpty() || pills.isEmpty()) return

        var nearestIdx = 0
        var nearestDiff = Float.MAX_VALUE
        for ((i, stop) in stops.withIndex()) {
            val d = abs(current - stop)
            if (d < nearestDiff) {
                nearestDiff = d
                nearestIdx = i
            }
        }

        for ((i, pill) in pills.withIndex()) {
            val isActive = i == nearestIdx
            if (pill.isSelected != isActive) pill.isSelected = isActive
            val newText = if (isActive && nearestDiff > MATCH_TOLERANCE) {
                formatLiveRatio(current)
            } else {
                formatStopLabel(stops[i])
            }
            if (pill.text.toString() != newText) pill.text = newText
        }
    }

    private fun formatLiveRatio(current: Float): String {
        if (current != lastFormattedRatio) {
            lastFormattedRatio = current
            lastFormattedString = String.format(FRACTIONAL_FORMAT, current)
        }
        return lastFormattedString
    }

    private fun rebuildPills() {
        removeAllViews()
        pills.clear()

        val density = resources.displayMetrics.density
        val sizePx = (36 * density).toInt()
        val marginPx = (6 * density).toInt()
        val textColors: ColorStateList = resources.getColorStateList(
            R.color.camera_selector_pill_text, context.theme
        )

        for (stop in stops) {
            val pill = TextView(context).apply {
                val lp = LayoutParams(sizePx, sizePx)
                lp.marginStart = marginPx
                lp.marginEnd = marginPx
                layoutParams = lp
                gravity = Gravity.CENTER
                isSingleLine = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(textColors)
                setBackgroundResource(R.drawable.camera_selector_pill_bg)
                text = formatStopLabel(stop)
                contentDescription = context.getString(
                    R.string.zoom_select_ratio, formatStopLabel(stop)
                )
                setOnClickListener { mainActivity.cameraControl.setZoomRatio(stop) }
            }
            addView(pill)
            pills.add(pill)
        }
    }

    private fun formatStopLabel(stop: Float): String =
        if (stop < 1f) String.format(FRACTIONAL_FORMAT, stop)
        else String.format(WHOLE_FORMAT, stop)
}
