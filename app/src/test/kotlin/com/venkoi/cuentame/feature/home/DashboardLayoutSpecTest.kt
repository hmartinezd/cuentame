package com.venkoi.cuentame.feature.home

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DashboardLayoutSpecTest {

    @Test
    fun `600dp available content uses narrow two-column policy`() {
        val spec = dashboardLayoutSpec(600.dp)

        assertThat(spec.mode).isEqualTo(DashboardLayoutMode.NARROW)
        assertThat(spec.kpiColumns).isEqualTo(2)
        assertThat(spec.quickActionColumns).isEqualTo(2)
        assertThat(spec.useTwoColumnDetails).isFalse()
    }

    @Test
    fun `800dp available content uses medium policy`() {
        val spec = dashboardLayoutSpec(800.dp)

        assertThat(spec.mode).isEqualTo(DashboardLayoutMode.MEDIUM)
        assertThat(spec.kpiColumns).isEqualTo(2)
        assertThat(spec.quickActionColumns).isEqualTo(3)
        assertThat(spec.useTwoColumnDetails).isTrue()
    }

    @Test
    fun `1050dp available content uses wide four-column policy`() {
        val spec = dashboardLayoutSpec(1050.dp)

        assertThat(spec.mode).isEqualTo(DashboardLayoutMode.WIDE)
        assertThat(spec.kpiColumns).isEqualTo(4)
        assertThat(spec.quickActionColumns).isEqualTo(4)
        assertThat(spec.useTwoColumnDetails).isTrue()
    }
}
