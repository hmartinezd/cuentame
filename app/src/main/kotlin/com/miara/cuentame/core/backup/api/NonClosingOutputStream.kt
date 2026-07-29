package com.miara.cuentame.core.backup.api

import java.io.FilterOutputStream
import java.io.OutputStream

/**
 * A wrapper that prevents [ZipOutputStream] (or other decorators) 
 * from closing the underlying [OutputStream].
 */
class NonClosingOutputStream(
    outputStream: OutputStream
) : FilterOutputStream(outputStream) {

    override fun close() {
        // Only flush, do not close the underlying stream
        flush()
    }
}
