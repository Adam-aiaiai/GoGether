package com.gogether.app

import android.app.Application
import com.gogether.app.core.data.FakeGogetherRepository
import com.gogether.app.core.data.GogetherRepository

class GogetherApplication : Application() {
    val repository: GogetherRepository by lazy { FakeGogetherRepository() }
}
