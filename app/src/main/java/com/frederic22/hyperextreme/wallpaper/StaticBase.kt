package com.frederic22.hyperextreme.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

/**
 * Alive 壁纸的**静态基底**（2026-11-11）。
 *
 * ## 为什么需要这一步
 *
 * Alive 的画面是挂在「壁纸 SurfaceControl」上的。MIUI 的锁屏壁纸默认走
 * `MiuiKeyguardPictorialWallpaper`（景深/画报）那条独立通道，我们在它上面挂的层是 hidden 的
 * —— 日志里帧一帧不少，锁屏上什么都没有；表现就是「锁屏不显示 / 进 AOD 闪一下 / 桌面偶尔可见」。
 *
 * ## 为什么是「一次写两个 flag」
 *
 * 用户实测：在相册里选一张图、**同时设置桌面和锁屏**之后，Alive 就正常显示了。
 * 对应到 API 就是一次 `setBitmap(..., FLAG_SYSTEM or FLAG_LOCK)`。踩过的两个坑：
 * * 分两次写（先 SYSTEM 再 LOCK）：MIUI 只吃第一口，锁屏 id 不变（实测 394 / 395）；
 * * `clear(both)`：只把引擎退成 `ImageWallpaper`，锁屏表面还是 pictorial 那层。
 *
 * 写完之后必须 `killall com.miui.miwallpaper` 重建壁纸层，否则旧层树还在。
 */
internal object StaticBase {

    private const val TAG = "SPATIAL_WP"
    private const val W = 720
    private const val H = 1600

    /** 当前 which 对应的壁纸 id（MIUI 上 `getWallpaperInfo` 恒 null，只能靠 id 比对）。 */
    fun wallpaperId(wm: WallpaperManager, which: Int): Int = runCatching {
        wm.getWallpaperId(which)
    }.getOrDefault(-1)

    /**
     * 把锁屏 + 桌面一起换成 [source]（没有就纯黑），然后重建壁纸层。
     *
     * **必须在 `AliveFlag.push` 之前同步执行** —— 壁纸层没建好就下发属性，
     * 渲染器会挂到旧的（被隐藏的）leash 上。
     */
    fun prepareAliveBase(ctx: Context, source: Bitmap?, reason: String): Boolean {
        val wm = WallpaperManager.getInstance(ctx)
        val before = "system=${wallpaperId(wm, WallpaperManager.FLAG_SYSTEM)} " +
            "lock=${wallpaperId(wm, WallpaperManager.FLAG_LOCK)}"
        val ok = runCatching {
            val src = source ?: Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                .apply { eraseColor(Color.BLACK) }
            val scaled = if (src.width > 1080) {
                val h = (src.height.toLong() * 1080L / src.width).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(src, 1080, h, true)
            } else {
                src
            }
            try {
                wm.setBitmap(
                    scaled,
                    null,
                    true,
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
                )
            } finally {
                if (scaled !== src) scaled.recycle()
                if (src !== source) src.recycle()
            }
            true
        }.getOrElse {
            Log.w(TAG, "写基底失败（$reason）：${it.javaClass.simpleName}: ${it.message}")
            false
        }
        Log.i(
            TAG,
            "写基底（$reason）：$before ⇒ system=${wallpaperId(wm, WallpaperManager.FLAG_SYSTEM)} " +
                "lock=${wallpaperId(wm, WallpaperManager.FLAG_LOCK)} ok=$ok",
        )
        if (!ok) return false
        val r = com.frederic22.hyperextreme.data.RootShell.run("killall com.miui.miwallpaper")
        Log.i(TAG, "重建壁纸层（$reason）：killall ⇒ ${if (r.ok) "ok" else r.output.take(120)}")
        runCatching { Thread.sleep(2000L) }
        return true
    }
}
