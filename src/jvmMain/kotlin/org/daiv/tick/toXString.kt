package org.daiv.tick

import java.text.SimpleDateFormat
import java.util.*

actual fun Long.toXString():String{
    return SimpleDateFormat("mm:ss:SSS").format(Date(this))
}

