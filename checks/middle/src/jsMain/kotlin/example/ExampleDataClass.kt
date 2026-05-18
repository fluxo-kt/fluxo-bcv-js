@file:OptIn(ExperimentalJsExport::class)

package example

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
public data class ExampleDataClass(public val value: Int)
