/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.reportsscheduler.settings

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.opensearch.common.settings.Setting
import org.opensearch.common.settings.Settings

internal class PluginSettingsTests {

    private val maxReportDefinitionsKey = "plugins.reports.max_report_definitions"

    @Suppress("UNCHECKED_CAST")
    private fun maxReportDefinitions(): Setting<Int> =
        PluginSettings.getAllSettings().first { it.key == maxReportDefinitionsKey } as Setting<Int>

    @Test
    fun `test max report definitions setting is registered`() {
        Assertions.assertTrue(PluginSettings.getAllSettings().any { it.key == maxReportDefinitionsKey })
    }

    @Test
    fun `test max report definitions falls back to its default`() {
        Assertions.assertEquals(50, maxReportDefinitions().get(Settings.EMPTY))
    }

    @Test
    fun `test max report definitions has no upper bound`() {
        val settings = Settings.builder().put(maxReportDefinitionsKey, 100000).build()
        Assertions.assertEquals(100000, maxReportDefinitions().get(settings))

        val maxIntSettings = Settings.builder().put(maxReportDefinitionsKey, Int.MAX_VALUE).build()
        Assertions.assertEquals(Int.MAX_VALUE, maxReportDefinitions().get(maxIntSettings))
    }

    @Test
    fun `test max report definitions accepts zero`() {
        val settings = Settings.builder().put(maxReportDefinitionsKey, 0).build()
        Assertions.assertEquals(0, maxReportDefinitions().get(settings))
    }

    @Test
    fun `test max report definitions rejects negative values`() {
        val settings = Settings.builder().put(maxReportDefinitionsKey, -1).build()
        assertThrows<IllegalArgumentException> { maxReportDefinitions().get(settings) }
    }
}
