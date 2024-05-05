@file:OptIn(ExperimentalJsExport::class)

package example

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
data class ExampleDataClass(val value: Int)
