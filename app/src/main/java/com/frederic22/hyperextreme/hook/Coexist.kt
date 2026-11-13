package com.frederic22.hyperextreme.hook

/**
 * 两个模块之间的**单向**状态共享（同一进程内，SystemUI）。
 *
 * HyperMusicCover 的音乐屏（封面 / 歌词）和我们的前景层处理抢同一块地方：
 * 它把歌词画进 `keyguard_foreground_layer`，我们没景深主体时把这一层整层压 INVISIBLE
 * ⇒ 表现就是「MC 壁纸正常、歌词不显示」（2026-11-11 真机）。
 *
 * 这里只共享一个布尔：音乐屏是否正在显示。Alive 渲染器每 500ms 探测完就写进来，
 * `SpatialForegroundHider` 读到就跳过压层（并把层恢复 VISIBLE）。
 */
internal object Coexist {

    /** 音乐屏（封面 / 歌词）正在显示。 */
    @Volatile
    var mcMusicScreen: Boolean = false

    /** 状态变化时的回调（前景层需要立刻重算一次，不能等下一次 hook 触发）。 */
    @Volatile
    var onChanged: (() -> Unit)? = null
}
