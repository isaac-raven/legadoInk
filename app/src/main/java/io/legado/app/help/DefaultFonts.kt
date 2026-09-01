package io.legado.app.help

import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.FileUtils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.printOnDebug
import splitties.init.appCtx
import java.io.File

/**
 * 内置阅读字体
 * 首次启动时从assets/fonts释放到应用字体目录(externalFiles/font),
 * 之后在阅读界面的字体选择对话框中即可选用
 */
object DefaultFonts {

    private const val FONT_ASSET_DIR = "fonts"

    fun install() {
        Coroutine.async {
            val fontDir = File(FileUtils.getPath(appCtx.externalFiles, "font"))
            if (!fontDir.exists()) {
                fontDir.mkdirs()
            }
            appCtx.assets.list(FONT_ASSET_DIR)?.forEach { name ->
                val target = File(fontDir, name)
                if (target.exists()) {
                    return@forEach
                }
                runCatching {
                    appCtx.assets.open("$FONT_ASSET_DIR/$name").use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }.onError {
            it.printOnDebug()
        }
    }

}
