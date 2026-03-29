package com.dere3046.arbinspector

object ArbInspector {
    init {
        System.loadLibrary("arb_inspector")
    }

    @JvmStatic
    external fun getVersion(): String

    @JvmStatic
    external fun extract(path: String, debug: Boolean, blockMode: Boolean): ArbResult

    @JvmStatic
    external fun extractWithMode(path: String, fullMode: Boolean, debug: Boolean): ArbResult
}