@file:OptIn(ExperimentalJsExport::class)

package example

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
fun addOne(x: Int): Int = x + 1

@JsExport
fun d2f(f: Double): Float = f.toFloat()

@JsExport
fun s2b(s: String): Boolean = s.toBoolean()

@JsExport
@Suppress("EmptyFunctionBlock")
fun dummy() {}

//@JsExport
//data class ExampleDataClass(val value: Int)
