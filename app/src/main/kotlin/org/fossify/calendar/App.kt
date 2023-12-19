package org.fossify.calendar

import androidx.multidex.MultiDexApplication
import org.fossify.commons.extensions.checkUseEnglish

class App : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
    }
}
