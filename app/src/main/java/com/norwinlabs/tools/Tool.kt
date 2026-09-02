package com.norwinlabs.tools

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes

/**
 * Which entitlement a tool needs. Everything ships as [FREE] today; the field exists so gating a
 * feature later is a one-line change in [ToolRegistry] rather than a change to call sites.
 */
enum class ToolTier { FREE, PRO }

/** What tapping a tool card does. */
sealed interface ToolAction {
    /**
     * Navigate to a destination in nav_graph.
     *
     * A destination id rather than an action id: actions are scoped to the screen that declares
     * them, so an action id would only ever work from Home. Destinations are global, which is
     * what lets any screen open any tool.
     */
    data class Navigate(@IdRes val destinationId: Int) : ToolAction

    /** Hand off to the browser. */
    data class OpenUrl(val url: String) : ToolAction

    /** Handled in place by the host screen - a dialog or an inline check. */
    data class Local(val id: LocalAction) : ToolAction

    /** Declared but not built yet. */
    data object ComingSoon : ToolAction
}

/** In-place actions the Home screen handles itself rather than navigating. */
enum class LocalAction { IDEA_GENERATOR, VIDEO_IDEAS, CHECK_UPDATES }

data class Tool(
    val id: Int,
    val name: String,
    @DrawableRes val iconRes: Int,
    val version: String = "1.0.0",
    @ColorInt val color: Int = 0xFF6200EE.toInt(),
    val category: String = "Other",
    val tier: ToolTier = ToolTier.FREE,
    /** Prompts for biometric auth before [action] runs, when the user has enabled it. */
    val requiresBiometric: Boolean = false,
    val action: ToolAction = ToolAction.ComingSoon,
)
