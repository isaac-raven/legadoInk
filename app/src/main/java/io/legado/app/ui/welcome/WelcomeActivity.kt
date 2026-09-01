package io.legado.app.ui.welcome

import android.content.Intent
import android.os.Build
import android.os.Bundle
import io.legado.app.base.BaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.databinding.ActivityWelcomeBinding
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 启动入口, 无开屏画面
 * 直接进入主界面, 避免开屏文字在墨水屏上留下残影
 */
open class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {

    override val binding by viewBinding(ActivityWelcomeBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        if (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0) {
            // 避免从桌面启动程序后，会重新实例化入口类的activity
            finish()
        } else {
            startMainActivity()
        }
    }

    override fun upBackgroundImage() {
        //无开屏画面, 不加载开屏背景图
    }

    private fun startMainActivity() {
        startActivity<MainActivity>()
        if (getPrefBoolean(PreferKey.defaultToRead) && appDb.bookDao.lastReadBook != null) {
            startActivity<ReadBookActivity>()
        }
        finish()
        //取消窗口切换过渡动画, 避免墨水屏在过渡时全屏闪黑
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

}

class Launcher1 : WelcomeActivity()
class Launcher2 : WelcomeActivity()
class Launcher3 : WelcomeActivity()
class Launcher4 : WelcomeActivity()
class Launcher5 : WelcomeActivity()
class Launcher6 : WelcomeActivity()
class Launcher7 : WelcomeActivity()
