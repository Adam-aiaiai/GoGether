package com.gogether.app.core.data

object AppContainer {
    val repository: GogetherRepository by lazy { FakeGogetherRepository() }
}
