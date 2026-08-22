package io.github.androiddesktop

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Shared motion system derived from flutter-app-design.
 *
 * Daily UI stays crisp: press 120ms, popover 200ms, content switching 220ms,
 * modal 240ms. Enter/exit uses ease-out; movement between visible states uses
 * ease-in-out. Normal UI deliberately avoids bounce/overshoot.
 */
object MotionTokens {
    const val PressScale = 0.97f
    const val SurfaceEnterScale = 0.96f
    const val PopoverEnterScale = 0.96f
    const val UnfocusedWindowScale = 0.985f

    const val PressMs = 120L
    const val StateMs = 160L
    const val PopoverMs = 200L
    const val ContentSwitchMs = 220L
    const val ModalMs = 240L
    const val ExitMs = 180L

    val EaseOut: TimeInterpolator = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    val EaseInOut: TimeInterpolator = PathInterpolator(0.77f, 0f, 0.175f, 1f)
    val Drawer: TimeInterpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)

    /** Flutter-design boundary settle token: mass=1, stiffness=420, damping=30. */
    val BoundarySpring: TimeInterpolator = DampedSpringInterpolator(
        mass = 1f,
        stiffness = 420f,
        damping = 30f
    )
}

object AppMotion {
    private val running = WeakHashMap<View, Animator>()

    fun reducedMotion(context: Context): Boolean {
        val platformDisabled = android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return platformDisabled || scale == 0f
    }

    fun duration(context: Context, normalMs: Long): Long = if (reducedMotion(context)) 0L else normalMs

    fun installPressFeedback(view: View) {
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> animateScale(target, MotionTokens.PressScale, MotionTokens.PressMs)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animateScale(target, 1f, MotionTokens.PressMs)
            }
            false
        }
    }

    fun showPopover(view: View, fromBottomStart: Boolean = true) {
        cancel(view)
        if (reducedMotion(view.context)) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationY = 0f
            return
        }
        view.visibility = View.VISIBLE
        if (fromBottomStart) {
            view.pivotX = 0f
            view.pivotY = view.height.toFloat().coerceAtLeast(1f)
        }
        view.alpha = 0f
        view.scaleX = MotionTokens.PopoverEnterScale
        view.scaleY = MotionTokens.PopoverEnterScale
        view.translationY = dp(view, 8).toFloat()
        val set = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_X, MotionTokens.PopoverEnterScale, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, MotionTokens.PopoverEnterScale, 1f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, 0f)
            )
            duration = MotionTokens.PopoverMs
            interpolator = MotionTokens.EaseOut
        }
        start(view, set)
    }

    fun hidePopover(view: View, onHidden: () -> Unit) {
        cancel(view)
        if (reducedMotion(view.context)) {
            view.alpha = 0f
            view.visibility = View.GONE
            onHidden()
            return
        }
        val set = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f),
                ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, MotionTokens.PopoverEnterScale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, MotionTokens.PopoverEnterScale),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, dp(view, 6).toFloat())
            )
            duration = MotionTokens.ExitMs
            interpolator = MotionTokens.EaseOut
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    onHidden()
                }
            })
        }
        start(view, set)
    }

    fun showLaunchpad(view: View) {
        cancel(view)
        if (reducedMotion(view.context)) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationY = 0f
            return
        }
        view.visibility = View.VISIBLE
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        view.alpha = 0f
        view.scaleX = 0.92f
        view.scaleY = 0.92f
        view.translationY = dp(view, 14).toFloat()
        val set = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.92f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.92f, 1f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, 0f)
            )
            duration = 260L
            interpolator = MotionTokens.EaseOut
        }
        start(view, set)
    }

    fun hideLaunchpad(view: View, onHidden: () -> Unit) {
        cancel(view)
        if (reducedMotion(view.context)) {
            view.alpha = 0f
            view.visibility = View.GONE
            onHidden()
            return
        }
        val set = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f),
                ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, 0.95f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, 0.95f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, dp(view, 10).toFloat())
            )
            duration = 190L
            interpolator = MotionTokens.EaseOut
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    onHidden()
                }
            })
        }
        start(view, set)
    }

    fun showModal(view: View) {
        cancel(view)
        if (reducedMotion(view.context)) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.visibility = View.VISIBLE
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        view.alpha = 0f
        view.scaleX = MotionTokens.SurfaceEnterScale
        view.scaleY = MotionTokens.SurfaceEnterScale
        val set = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_X, MotionTokens.SurfaceEnterScale, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, MotionTokens.SurfaceEnterScale, 1f)
            )
            duration = MotionTokens.ModalMs
            interpolator = MotionTokens.EaseOut
        }
        start(view, set)
    }

    fun hideModal(view: View, onHidden: () -> Unit) {
        cancel(view)
        if (reducedMotion(view.context)) {
            view.alpha = 0f
            view.visibility = View.GONE
            onHidden()
            return
        }
        val set = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f),
                ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, MotionTokens.SurfaceEnterScale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, MotionTokens.SurfaceEnterScale)
            )
            duration = MotionTokens.ExitMs
            interpolator = MotionTokens.EaseOut
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    onHidden()
                }
            })
        }
        start(view, set)
    }

    fun settlePulse(view: View) {
        cancel(view)
        if (reducedMotion(view.context)) {
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        val downX = ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, MotionTokens.PressScale)
        val downY = ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, MotionTokens.PressScale)
        val down = AnimatorSet().apply {
            playTogether(downX, downY)
            duration = MotionTokens.PressMs
            interpolator = MotionTokens.EaseOut
        }
        val up = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, MotionTokens.PressScale, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, MotionTokens.PressScale, 1f)
            )
            duration = 260L
            interpolator = MotionTokens.BoundarySpring
        }
        val sequence = AnimatorSet().apply { playSequentially(down, up) }
        start(view, sequence)
    }

    fun cancel(view: View) {
        running.remove(view)?.cancel()
        view.animate().cancel()
    }

    fun start(view: View, animator: Animator) {
        cancel(view)
        running[view] = animator
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (running[view] === animation) running.remove(view)
            }
            override fun onAnimationCancel(animation: Animator) {
                if (running[view] === animation) running.remove(view)
            }
        })
        animator.start()
    }

    private fun animateScale(view: View, target: Float, normalMs: Long) {
        if (reducedMotion(view.context)) {
            view.scaleX = target
            view.scaleY = target
            return
        }
        view.animate().cancel()
        view.animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(normalMs)
            .setInterpolator(MotionTokens.EaseOut)
            .withLayer()
            .start()
    }

    private fun dp(view: View, value: Int): Int = (value * view.resources.displayMetrics.density).toInt()
}

private class DampedSpringInterpolator(
    mass: Float,
    stiffness: Float,
    damping: Float
) : Interpolator {
    private val m = max(0.001f, mass)
    private val k = max(0.001f, stiffness)
    private val c = max(0f, damping)
    private val omega0 = sqrt(k / m)
    private val zeta = c / (2f * sqrt(k * m))
    private val omegaD = omega0 * sqrt(max(0.0001f, 1f - zeta * zeta))

    override fun getInterpolation(input: Float): Float {
        val t = input.coerceIn(0f, 1f) * 0.34f
        if (zeta >= 1f) {
            val value = 1f - exp((-omega0 * t).toDouble()).toFloat()
            return value.coerceIn(0f, 1f)
        }
        val envelope = exp((-zeta * omega0 * t).toDouble()).toFloat()
        val value = 1f - envelope * cos((omegaD * t).toDouble()).toFloat()
        // Preserve a subtle physical settle while preventing decorative bounce.
        return value.coerceIn(0f, 1.025f)
    }
}
