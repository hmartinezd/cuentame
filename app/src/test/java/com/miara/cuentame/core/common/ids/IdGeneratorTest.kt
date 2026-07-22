package com.miara.cuentame.core.common.ids

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IdGeneratorTest {
    private val generator = UuidIdGenerator()

    @Test
    fun `newId returns non-empty string`() {
        assertThat(generator.newId()).isNotEmpty()
    }

    @Test
    fun `newId returns unique strings`() {
        val id1 = generator.newId()
        val id2 = generator.newId()
        assertThat(id1).isNotEqualTo(id2)
    }
}
