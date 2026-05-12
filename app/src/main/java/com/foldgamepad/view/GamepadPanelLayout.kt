package com.foldgamepad.view

import android.content.Context
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

/**
 * A FrameLayout that manually routes each touch pointer to whichever child
 * view it lands on, allowing multiple joysticks (or joystick + buttons) to
 * operate simultaneously with different fingers.
 */
class GamepadPanelLayout(context: Context) : FrameLayout(context) {

    private val pointerOwner = SparseArray<View>()

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {

            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx   = ev.actionIndex
                val pid   = ev.getPointerId(idx)
                val x     = ev.getX(idx)
                val y     = ev.getY(idx)
                val child = childAt(x, y) ?: return true
                pointerOwner.put(pid, child)
                child.dispatchTouchEvent(synth(ev, MotionEvent.ACTION_DOWN, x - child.left, y - child.top))
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val pid   = ev.getPointerId(i)
                    val child = pointerOwner.get(pid) ?: continue
                    child.dispatchTouchEvent(synth(ev, MotionEvent.ACTION_MOVE,
                        ev.getX(i) - child.left, ev.getY(i) - child.top))
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val idx   = ev.actionIndex
                val pid   = ev.getPointerId(idx)
                val child = pointerOwner.get(pid)
                if (child != null) {
                    child.dispatchTouchEvent(synth(ev, MotionEvent.ACTION_UP,
                        ev.getX(idx) - child.left, ev.getY(idx) - child.top))
                    pointerOwner.remove(pid)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val action = if (ev.actionMasked == MotionEvent.ACTION_UP)
                    MotionEvent.ACTION_UP else MotionEvent.ACTION_CANCEL
                val pid   = ev.getPointerId(0)
                val child = pointerOwner.get(pid)
                if (child != null) {
                    child.dispatchTouchEvent(synth(ev, action, ev.x - child.left, ev.y - child.top))
                }
                pointerOwner.clear()
            }
        }
        return true
    }

    private fun childAt(x: Float, y: Float): View? {
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            if (child.visibility != VISIBLE) continue
            if (x >= child.left && x <= child.right &&
                y >= child.top  && y <= child.bottom) return child
        }
        return null
    }

    private fun synth(src: MotionEvent, action: Int, lx: Float, ly: Float): MotionEvent =
        MotionEvent.obtain(src.downTime, src.eventTime, action, lx, ly, src.metaState)
}
