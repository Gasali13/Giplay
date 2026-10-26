package com.personal.animeplayer.player

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import `is`.xyz.mpv.BaseMPVView

class MPVView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseMPVView(context, attrs) {

    var onInitOptions: (() -> Unit)? = null
    var onPostInitOptions: (() -> Unit)? = null
    var onObserveProperties: (() -> Unit)? = null
    var onSurfaceDestroyedListener: (() -> Unit)? = null

    override fun initOptions() {
        onInitOptions?.invoke()
    }

    override fun postInitOptions() {
        onPostInitOptions?.invoke()
    }

    override fun observeProperties() {
        onObserveProperties?.invoke()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        super.surfaceDestroyed(holder)
        onSurfaceDestroyedListener?.invoke()
    }
}
