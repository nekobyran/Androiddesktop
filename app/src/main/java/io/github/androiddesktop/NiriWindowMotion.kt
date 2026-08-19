package io.github.androiddesktop

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.HorizontalScrollView

object NiriWindowMotion {
    private val focusInterpolator = OvershootInterpolator(0.7f)
    private val scrollInterpolator = DecelerateInterpolator(1.9f)

    fun enterColumn(view: View, delayMs: Long = 0L) {
        view.alpha = 0f
        view.translationX = 42f
        view.scaleX = 0.94f
        view.scaleY = 0.94f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 42f, 0f),
                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.94f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.94f, 1f)
            )
            duration = 280L
            startDelay = delayMs
            interpolator = focusInterpolator
            start()
        }
    }

    fun focusColumn(view: View, focused: Boolean) {
        val targetScale = if (focused) 1.02f else 0.96f
        val targetAlpha = if (focused) 1f else 0.72f
        val targetTranslationY = if (focused) 0f else 14f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, targetScale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, targetScale),
                ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, targetAlpha),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, targetTranslationY)
            )
            duration = 230L
            interpolator = focusInterpolator
            start()
        }
        view.elevation = if (focused) 20f else 8f
    }

    fun smoothScrollTo(scrollView: HorizontalScrollView, targetX: Int) {
        val startX = scrollView.scrollX
        ObjectAnimator.ofInt(scrollView, "scrollX", startX, targetX).apply {
            duration = 320L
            interpolator = scrollInterpolator
            start()
        }
    }

    fun pulse(view: View) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.94f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.94f, 1f)
            )
            duration = 180L
            interpolator = focusInterpolator
            start()
        }
    }
}
