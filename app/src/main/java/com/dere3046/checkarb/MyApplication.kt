package com.dere3046.checkarb

import android.app.Application
import com.topjohnwu.superuser.Shell

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
        Shell.getShell()
    }
}