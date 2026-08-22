package io.github.androiddesktop

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.widget.HorizontalScrollView

/**
 * niri-style window motion using the shared Flutter-design motion tokens.
 * High-frequency focus/scroll motion is crisp and non-bouncy; physical spring
 * settle is reserved for the low-frequency floating utility affordance.
 */
object NiriWindowMotion {
    fun enterColumn(view: View, delayMs: Long = 0L) {
        AppMotion.cancel(view)
        if (AppMotion.reducedMotion(view.context)) {
            view.alpha = 1f
            view.translationX = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        val offset = dp(view, 18).toFloat()
        view.alpha = 0f
        view.translationX = offset
        view.scaleX = MotionTokens.SurfaceEnterScale
        view.scaleY = MotionTokens.SurfaceEnterScale
        val set = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_X, offset, 0f),
                ObjectAnimator.ofFloat(view, View.SCALE_X, MotionTokens.SurfaceEnterScale, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, MotionTokens.SurfaceEnterScale, 1f)
            )
            duration = MotionTokens.ContentSwitchMs
            startDelay = delayMs
            interpolator = MotionTokens.EaseOut
        }
        AppMotion.start(view, set)
    }

    fun focusColumn(view: View, focused: Boolean) {
        val targetScale = if (focused) 1f else MotionTokens.UnfocusedWindowScale
        val targetAlpha = if (focused) 1f else 0.84f
        val targetTranslationY = if (focused) 0f else dp(view, 4).toFloat()
        if (AppMotion.reducedMotion(view.context)) {
            view.scaleX = targetScale
            view.scaleY = targetScale
            view.alpha = targetAlpha
            view.translationY = targetTranslationY
            view.elevation = if (focused) dp(view, 18).toFloat() else dp(view, 8).toFloat()
            return
        }
        val alreadyAtTarget = kotlin.math.abs(view.scaleX - targetScale) < 0.001f &&
            kotlin.math.abs(view.alpha - targetAlpha) < 0.01f &&
            kotlin.math.abs(view.translationY - targetTranslationY) < 0.5f
        if (!alreadyAtTarget) {
            val set = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, targetScale),
                    ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, targetScale),
                    ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, targetAlpha),
                    ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, targetTranslationY)
                )
                duration = MotionTokens.ContentSwitchMs
                interpolator = MotionTokens.EaseInOut
            }
            AppMotion.start(view, set)
        }
        view.elevation = if (focused) dp(view, 18).toFloat() else dp(view, 8).toFloat()
    }

    fun smoothScrollTo(scrollView: HorizontalScrollView, targetX: Int) {
        val boundedTarget = targetX.coerceAtLeast(0)
        if (AppMotion.reducedMotion(scrollView.context)) {
            scrollView.scrollTo(boundedTarget, 0)
            return
        }
        if (scrollView.scrollX == boundedTarget) return
        val animator = ObjectAnimator.ofInt(scrollView, "scrollX", scrollView.scrollX, boundedTarget).apply {
            duration = MotionTokens.ContentSwitchMs
            interpolator = MotionTokens.EaseInOut
        }
        AppMotion.start(scrollView, animator)
    }

    fun pulse(view: View) = AppMotion.settlePulse(view)

    private fun dp(view: View, value: Int): Int = (value * view.resources.displayMetrics.density).toInt()
}
