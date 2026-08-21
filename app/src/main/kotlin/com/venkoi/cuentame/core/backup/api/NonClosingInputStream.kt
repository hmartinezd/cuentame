package com.venkoi.cuentame.core.backup.api

import java.io.FilterInputStream
import java.io.InputStream

/**
 * A wrapper that prevents [ZipInputStream] (or other decorators)
 * from closing the underlying [InputStream].
 */
class NonClosingInputStream(
    inputStream: InputStream
) : FilterInputStream(inputStream) {
    override fun close() {
        // Do not close the underlying stream
    }
}
