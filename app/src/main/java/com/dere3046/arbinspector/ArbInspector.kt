package com.dere3046.arbinspector

class ArbInspector {
    companion object {
        init {
            System.loadLibrary("arb_inspector")
        }
    }

    external fun extract(
        path: String,
        debug: Boolean,
        blockMode: Boolean
    ): ArbResult

    external fun extractWithConfig(
        path: String,
        debug: Boolean,
        blockMode: Boolean,
        config: ArbConfig?
    ): ArbResult
}