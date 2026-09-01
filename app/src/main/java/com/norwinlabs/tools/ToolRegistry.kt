package com.norwinlabs.tools

/**
 * The single source of truth for every tool the app ships.
 *
 * Adding a tool used to mean editing four places: the catalogue list, a branch in HomeFragment's
 * id switch, an <action> in nav_graph and the category ordering. Everything except the nav_graph
 * destination now derives from this list, so a new tool is one entry here plus its destination.
 */
object ToolRegistry {

    /** Section order in the "Add Tool" sheet; an unlisted category sorts last. */
    val categoryOrder = listOf(
        "Communication",
        "Maps & Location",
        "Network Tools",
        "Dev Tools",
        "Windhelm",
        "Personal",
        "System",
    )

    val all: List<Tool> = listOf(
        Tool(
            id = 1, name = "Calendar", iconRes = android.R.drawable.ic_menu_today, version = "1.0.2",
            color = 0xFF2E7D32.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1506784365847-bbad939e9335?q=80&w=500&auto=format&fit=crop",
            category = "Personal",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_CalendarFragment),
        ),
        Tool(
            id = 2, name = "Converter", iconRes = android.R.drawable.ic_menu_compass,
            color = 0xFF1565C0.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1574634534894-89d7576c8259?q=80&w=500&auto=format&fit=crop",
            category = "Personal",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_ConverterFragment),
        ),
        Tool(
            id = 3, name = "Notes", iconRes = android.R.drawable.ic_menu_edit,
            color = 0xFFEF6C00.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1517842645767-c639042777db?q=80&w=500&auto=format&fit=crop",
            category = "Personal",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_NotesFragment),
        ),
        Tool(
            id = 4, name = "Settings", iconRes = android.R.drawable.ic_menu_manage, version = "1.0.1",
            color = 0xFF455A64.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1581092160562-40aa08e78837?q=80&w=500&auto=format&fit=crop",
            category = "System",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_SettingsFragment),
        ),
        // No background photo: dedupes what used to be an identical image to Bug Report's card.
        Tool(
            id = 5, name = "About", iconRes = android.R.drawable.ic_menu_info_details,
            color = 0xFF4527A0.toInt(), category = "System",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_AboutFragment),
        ),
        Tool(
            id = 9, name = "Idea Generator", iconRes = R.drawable.ic_lightbulb, version = "1.0.1",
            color = 0xFFF9A825.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1499750310107-5fef28a66643?q=80&w=500&auto=format&fit=crop",
            category = "Dev Tools",
            action = ToolAction.Local(LocalAction.IDEA_GENERATOR),
        ),
        Tool(
            id = 12, name = "Update", iconRes = android.R.drawable.ic_menu_upload, version = "1.0.1",
            color = 0xFFC62828.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1633356122544-f134324a6cee?q=80&w=500&auto=format&fit=crop",
            category = "System",
            action = ToolAction.Local(LocalAction.CHECK_UPDATES),
        ),
        Tool(
            id = 13, name = "Windhelm", iconRes = android.R.drawable.ic_menu_view, version = "1.0.2",
            color = 0xFF283593.toInt(), imageUrl = "https://windhelm.dev/background.png",
            category = "Windhelm",
            action = ToolAction.OpenUrl("https://windhelm.dev"),
        ),
        Tool(
            id = 15, name = "UE5 Guide", iconRes = android.R.drawable.ic_menu_directions,
            color = 0xFF00695C.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1542831371-29b0f74f9713?q=80&w=500&auto=format&fit=crop",
            category = "Windhelm",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_UE5GuideFragment),
        ),
        Tool(
            id = 16, name = "Trello", iconRes = android.R.drawable.ic_menu_agenda, version = "1.0.1",
            color = 0xFF0079BF.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1522071820081-009f0129c71c?q=80&w=500&auto=format&fit=crop",
            category = "Windhelm",
            action = ToolAction.OpenUrl("https://trello.com/b/SVY6LFSZ/windhelm-main-development"),
        ),
        Tool(
            id = 17, name = "SSH Client", iconRes = android.R.drawable.ic_dialog_dialer,
            color = 0xFF37474F.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1629654297299-c8506221ca97?q=80&w=500&auto=format&fit=crop",
            category = "Network Tools",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_SshClientFragment),
        ),
        // No background photo: dedupes what used to be an identical image to Port Scanner's card.
        Tool(
            id = 18, name = "Ping Tool", iconRes = android.R.drawable.ic_menu_revert,
            color = 0xFF0091EA.toInt(), category = "Network Tools",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_PingToolFragment),
        ),
        Tool(
            id = 20, name = "Net Scanner", iconRes = android.R.drawable.ic_menu_share, version = "1.0.2",
            color = 0xFF546E7A.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1544197150-b99a580bb7a8?q=80&w=500&auto=format&fit=crop",
            category = "Network Tools",
            requiresBiometric = true,
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_NetScannerFragment),
        ),
        Tool(
            id = 21, name = "Video Ideas", iconRes = android.R.drawable.ic_menu_slideshow, version = "1.0.3",
            color = 0xFFE91E63.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1492724441997-5dc865305da7?q=80&w=500&auto=format&fit=crop",
            category = "Dev Tools",
            action = ToolAction.Local(LocalAction.VIDEO_IDEAS),
        ),
        Tool(
            id = 22, name = "Dev News", iconRes = android.R.drawable.ic_menu_recent_history, version = "1.0.1",
            color = 0xFF2E7D32.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1485827404703-89b55fcc595e?q=80&w=500&auto=format&fit=crop",
            category = "Dev Tools",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_DevNewsFragment),
        ),
        Tool(
            id = 23, name = "Bug Report", iconRes = android.R.drawable.ic_menu_report_image,
            color = 0xFFC62828.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?q=80&w=500&auto=format&fit=crop",
            category = "System",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_BugReportFragment),
        ),
        Tool(
            id = 24, name = "Budget", iconRes = android.R.drawable.ic_menu_save,
            color = 0xFF4CAF50.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1554224155-6726b3ff858f?q=80&w=500&auto=format&fit=crop",
            category = "Personal",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_BudgetFragment),
        ),
        Tool(
            id = 25, name = "System Dash", iconRes = android.R.drawable.ic_menu_info_details,
            color = 0xFF607D8B.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=500&auto=format&fit=crop",
            category = "System",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_SystemDashboardFragment),
        ),
        Tool(
            id = 26, name = "Port Scanner", iconRes = android.R.drawable.ic_menu_compass,
            color = 0xFF3F51B5.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1558494949-ef010ca73324?q=80&w=500&auto=format&fit=crop",
            category = "Network Tools",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_PortScannerFragment),
        ),
        Tool(
            id = 27, name = "Circle Share", iconRes = android.R.drawable.ic_menu_share,
            color = 0xFF2196F3.toInt(),
            imageUrl = "https://images.unsplash.com/photo-1526628953301-3e589a6a8b74?q=80&w=500&auto=format&fit=crop",
            category = "Maps & Location",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_CircleShareFragment),
        ),
        // No background photo: dedupes what used to be an identical image shared by three cards.
        Tool(
            id = 28, name = "Data Centers", iconRes = android.R.drawable.ic_menu_mapmode,
            color = 0xFF00838F.toInt(), category = "Maps & Location",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_DataCenterMapFragment),
        ),
        Tool(
            id = 29, name = "Flock Cameras", iconRes = android.R.drawable.ic_menu_camera,
            color = 0xFF6A1B9A.toInt(), category = "Maps & Location",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_FlockCameraMapFragment),
        ),
        Tool(
            id = 30, name = "VoIP Calling", iconRes = android.R.drawable.ic_menu_call,
            color = 0xFF00897B.toInt(), category = "Communication",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_VoipCallFragment),
        ),
        Tool(
            id = 31, name = "Hunting Insights", iconRes = android.R.drawable.ic_menu_mylocation,
            color = 0xFF33691E.toInt(), category = "Maps & Location",
            action = ToolAction.Navigate(R.id.action_HomeFragment_to_HuntingInsightsMapFragment),
        ),
    )

    private val byId: Map<Int, Tool> = all.associateBy { it.id }

    fun byId(id: Int): Tool? = byId[id]

    fun byIds(ids: List<Int>): List<Tool> = ids.mapNotNull(::byId)
}
