package com.dere3046.arbinspector

class ArbResult {
    @JvmField
    var major: Int = 0

    @JvmField
    var minor: Int = 0

    @JvmField
    var arb: Int = 0

    @JvmField
    var debugMessages: MutableList<String> = mutableListOf()

    @JvmField
    var error: String? = null

    constructor()
}