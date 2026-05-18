@file:OptIn(ExperimentalJsExport::class)

package example

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
public fun addOne(x: Int): Int = x + 1

@JsExport
public fun d2f(f: Double): Float = f.toFloat()

@JsExport
public fun s2b(s: String): Boolean = s.toBoolean()

@JsExport
@Suppress("EmptyFunctionBlock")
public fun dummy() {}
