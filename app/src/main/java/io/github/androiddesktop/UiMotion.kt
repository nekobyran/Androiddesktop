package io.github.androiddesktop

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ScrollView
import android.widget.TextView

object UiMotion {
    private val decelerate = DecelerateInterpolator()
    private val overshoot = OvershootInterpolator(1.15f)
    private val smooth = AccelerateDecelerateInterpolator()

    fun runEntrance(root: View) {
        root.alpha = 0f
        root.translationY = 32f
        root.scaleX = 0.98f
        root.scaleY = 0.98f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(root, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(root, View.TRANSLATION_Y, 32f, 0f),
                ObjectAnimator.ofFloat(root, View.SCALE_X, 0.98f, 1f),
                ObjectAnimator.ofFloat(root, View.SCALE_Y, 0.98f, 1f)
            )
            duration = 420L
            interpolator = decelerate
            start()
        }
    }

    fun attachPressFeedback(view: View) {
        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> animateScale(touched, 0.965f, 90L)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animateScale(touched, 1f, 170L)
            }
            false
        }
    }

    fun pulse(view: View) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.025f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.025f, 1f),
                ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha.coerceAtLeast(0.72f), 1f)
            )
            duration = 360L
            interpolator = overshoot
            start()
        }
    }

    fun replaceText(textView: TextView, scrollView: ScrollView?, newText: String, animated: Boolean = true) {
        if (!animated) {
            textView.text = newText
            scrollView?.post { scrollView.scrollTo(0, 0) }
            return
        }
        textView.animate().cancel()
        textView.animate()
            .alpha(0f)
            .translationY(10f)
            .setDuration(110L)
            .setInterpolator(smooth)
            .withEndAction {
                textView.text = newText
                textView.translationY = -8f
                scrollView?.scrollTo(0, 0)
                textView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180L)
                    .setInterpolator(decelerate)
                    .start()
            }
            .start()
    }

    fun flash(view: View) {
        ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.72f, 1f).apply {
            duration = 260L
            interpolator = smooth
            start()
        }
    }

    fun animateFloat(from: Float, to: Float, duration: Long, update: (Float) -> Unit) {
        ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            interpolator = decelerate
            addUpdateListener { update(it.animatedValue as Float) }
            start()
        }
    }

    private fun animateScale(view: View, target: Float, duration: Long) {
        view.animate().cancel()
        view.animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(duration)
            .setInterpolator(decelerate)
            .start()
    }
}
