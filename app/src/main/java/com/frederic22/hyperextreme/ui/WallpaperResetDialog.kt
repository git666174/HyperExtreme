package com.frederic22.hyperextreme.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 「应用壁纸提示」弹窗（2026-11-11 改写；原名 WallpaperResetDialog 保留以减少改动）。
 *
 * ## 为什么还要弹
 *
 * 应用 Alive 会把锁屏和桌面壁纸一起换成这张照片（作为底层基底）：
 * 只有两份写成同一张静态图，锁屏那条独立壁纸才会消失，解锁动画和 AOD 才能正常显示。
 * 这仍然是「会改掉用户壁纸」的操作，所以保留一次确认，但不再清回系统默认。
 *
 * ## 和原作者 .81 那版的区别
 *
 * 他那一版是 clear(FLAG_SYSTEM or FLAG_LOCK) 回到系统默认再杀壁纸进程；实测在本机
 * 锁屏那份仍是独立壁纸（id 397/396 不相等），Alive 依旧看不见。这里改成单次
 * setBitmap(照片, SYSTEM|LOCK)，见 StaticBase.prepareAliveBase。
 */
@Composable
internal fun WallpaperResetDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        title = "应用壁纸提示",
        summary = "应用 Alive 壁纸后，锁屏与桌面的壁纸都会换成这张照片作为底层基底，这样解锁动画与 AOD 才能正常显示。原有壁纸会被这张照片替换，如确定，请点击继续。",
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        val dismissState = LocalDismissState.current
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButtonLike(
                    text = "取消",
                    onClick = {
                        onDismissRequest()
                        dismissState?.invoke()
                    },
                )
                Spacer(Modifier.width(20.dp))
                TextButtonLike(
                    text = "继续",
                    danger = true,
                    onClick = {
                        onConfirm()
                        dismissState?.invoke()
                    },
                )
            }
        }
    }
}
