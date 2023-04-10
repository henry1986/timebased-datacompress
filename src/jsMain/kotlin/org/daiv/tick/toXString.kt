package org.daiv.tick

import kotlin.js.Date

fun Date.toXString() = "${getMinutes()}:${getSeconds()}.${getMilliseconds()}"
actual fun Long.toXString():String = Date(this).toXString()
