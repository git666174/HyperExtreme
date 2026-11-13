package com.frederic22.hyperextreme.hook

import android.os.Handler
import android.os.Looper

/**
 * 独立的「音乐屏」观察者（2026-11-11 定稿）。
 *
 * 之前把探测塞在 Alive 渲染器的轮询里，渲染器一停（换壁纸、开关切、AOD 重建）探测就停，
 * `Coexist.mcMusicScreen` 会停在旧值上 —— 真机表现就是「退出音乐屏后让路不恢复」。
 * 这里改成**与渲染器无关**的常驻轮询：只遍历视图树，写 Coexist，不碰 alpha。
 */
internal object MusicWatch {

    private var started = false
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var lastLine = ""
    private var lastSeenAt = 0L

    private var tries = 0

    fun start() {
        if (started) return
        val app = runCatching {
            val at = Class.forName("android.app.ActivityThread")
            at.getMethod("currentApplication").invoke(null) as? android.app.Application
        }.getOrNull()
        if (app == null) {
            // init 阶段 Application 往往还没建好（2026-11-11：v123 就是在这里静默返回，
            // MUSIC_WATCH 一条日志都没有）。这里每 1s 重试，最多 30 次。
            tries++
            if (tries == 1) HookRuntime.log("MUSIC_WATCH 等 Application（最多 30s）")
            if (tries <= 30) handler.postDelayed({ start() }, 1000L)
            else HookRuntime.log("MUSIC_WATCH 放弃：拿不到 Application")
            return
        }
        started = true
        val task = object : Runnable {
            override fun run() {
                runCatching { scan(app) }
                handler.postDelayed(this, 800L)
            }
        }
        handler.postDelayed(task, 800L)
        HookRuntime.log("MUSIC_WATCH 启动（800ms 轮询）")
    }

    private fun scan(ctx: android.content.Context) {
        // 灭屏 / AOD 期间冻结：用户语义是「在哪里就显示什么」
        val interactive = runCatching {
            (ctx.getSystemService(android.content.Context.POWER_SERVICE)
                as? android.os.PowerManager)?.isInteractive == true
        }.getOrDefault(false)
        if (!interactive) return
        val found = ArrayList<String>(8)
        var visible = false
        runCatching {
            val cl = Class.forName("android.view.WindowManagerGlobal", false, javaClass.classLoader)
            val inst = cl.getMethod("getInstance").invoke(null)
            val f = cl.getDeclaredField("mViews").apply { isAccessible = true }
            val views = f.get(inst) as? List<*> ?: return
            fun walk(v: android.view.View, depth: Int) {
                if (depth > 8 || found.size >= 12) return
                val n = v.javaClass.name
                if (n.contains("music", true) || n.contains("lyric", true) || n.contains("cover", true)) {
                    // 回到 v117 那个「一见到 MusicBgView 就算在音乐屏」的判据（2026-11-11）：
                    // 那几版正是靠这个宽松判据让 MC 正常显示的。窗口/可见性判断引入后
                    // 反倒把状态判成 false（isShown 恒 false、windowVisibility 抖动）。
                    if (v.visibility == android.view.View.VISIBLE && v.alpha > 0.05f) visible = true
                    found.add(n.substringAfterLast('.') + "(vis=" + v.visibility + ",a=" + v.alpha + ")")
                }
                if (v is android.view.ViewGroup) {
                    for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
                }
            }
            for (v in views) (v as? android.view.View)?.let { walk(it, 0) }
        }
        val line = found.joinToString(" | ")
        if (line != lastLine) {
            lastLine = line
            HookRuntime.log("MUSIC_WATCH 探测：" + if (line.isEmpty()) "（无）" else line)
        }
        // 迟滞（2026-11-11 23:36 实测）：MC 的窗口在歌词刷新/动画时 win 会 true/false 反复跳，
        // 每次跳都切 alpha + 压/放前景层 ⇒ 歌词一闪就没了。这里加 2500ms 迟滞：
        // 只要 2.5s 内见过一次可见，就维持『音乐屏在』。
        val now = android.os.SystemClock.elapsedRealtime()
        if (visible) lastSeenAt = now
        val effective = visible || (lastSeenAt != 0L && now - lastSeenAt < 2500L)
        if (effective != Coexist.mcMusicScreen) {
            Coexist.mcMusicScreen = effective
            HookRuntime.log("MUSIC_WATCH 音乐屏" + (if (effective) "出现" else "消失"))
            Coexist.onChanged?.invoke()
        }
    }
}
