package io.github.androiddesktop

import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.widget.ScrollView
import android.widget.TextView

/**
 * Compatibility facade for older call sites.
 *
 * All parameters now come from [MotionTokens]; this file intentionally owns no
 * independent duration, curve, spring, or scale constants.
 */
object UiMotion {
    fun runEntrance(root: View) {
        AppMotion.cancel(root)
        if (AppMotion.reducedMotion(root.context)) {
            root.alpha = 1f
            root.translationY = 0f
            root.scaleX = 1f
            root.scaleY = 1f
            return
        }
        val offset = dp(root, 12).toFloat()
        root.alpha = 0f
        root.translationY = offset
        root.scaleX = MotionTokens.SurfaceEnterScale
        root.scaleY = MotionTokens.SurfaceEnterScale
        val set = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(root, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(root, View.TRANSLATION_Y, offset, 0f),
                ObjectAnimator.ofFloat(root, View.SCALE_X, MotionTokens.SurfaceEnterScale, 1f),
                ObjectAnimator.ofFloat(root, View.SCALE_Y, MotionTokens.SurfaceEnterScale, 1f)
            )
            duration = MotionTokens.ContentSwitchMs
            interpolator = MotionTokens.EaseOut
        }
        AppMotion.start(root, set)
    }

    fun attachPressFeedback(view: View) = AppMotion.installPressFeedback(view)

    fun pulse(view: View) = AppMotion.settlePulse(view)

    fun replaceText(textView: TextView, scrollView: ScrollView?, newText: String, animated: Boolean = true) {
        AppMotion.cancel(textView)
        if (!animated || AppMotion.reducedMotion(textView.context)) {
            textView.alpha = 1f
            textView.translationY = 0f
            textView.text = newText
            scrollView?.post { scrollView.scrollTo(0, 0) }
            return
        }
        val out = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(textView, View.ALPHA, textView.alpha, 0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, textView.translationY, dp(textView, 6).toFloat())
            )
            duration = MotionTokens.StateMs / 2
            interpolator = MotionTokens.EaseInOut
        }
        val enterOffset = -dp(textView, 6).toFloat()
        val enter = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(textView, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, enterOffset, 0f)
            )
            duration = MotionTokens.ContentSwitchMs - (MotionTokens.StateMs / 2)
            interpolator = MotionTokens.EaseOut
        }
        out.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                textView.text = newText
                textView.translationY = enterOffset
                scrollView?.scrollTo(0, 0)
            }
        })
        AppMotion.start(textView, AnimatorSet().apply { playSequentially(out, enter) })
    }

    fun flash(view: View) {
        AppMotion.cancel(view)
        if (AppMotion.reducedMotion(view.context)) {
            view.alpha = 1f
            return
        }
        val animator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.84f, 1f).apply {
            duration = MotionTokens.StateMs
            interpolator = MotionTokens.EaseInOut
        }
        AppMotion.start(view, animator)
    }

    fun animateFloat(
        owner: View,
        from: Float,
        to: Float,
        normalMs: Long = MotionTokens.ContentSwitchMs,
        update: (Float) -> Unit
    ) {
        AppMotion.cancel(owner)
        if (AppMotion.reducedMotion(owner.context)) {
            update(to)
            return
        }
        val animator = ValueAnimator.ofFloat(from, to).apply {
            duration = normalMs
            interpolator = MotionTokens.EaseOut
            addUpdateListener { update(it.animatedValue as Float) }
        }
        AppMotion.start(owner, animator)
    }

    private fun dp(view: View, value: Int): Int = (value * view.resources.displayMetrics.density).toInt()
}
