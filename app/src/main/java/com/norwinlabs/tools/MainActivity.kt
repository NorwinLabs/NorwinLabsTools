package com.norwinlabs.tools

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationBarView
import com.norwinlabs.tools.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var navigationBar: NavigationBarView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // The three tabs are all roots, so none of them shows an Up arrow. Passing the graph
        // wholesale would have made only the start destination top-level, giving Tools and
        // Settings a back arrow that competes with the tab bar.
        appBarConfiguration = AppBarConfiguration(TOP_LEVEL_DESTINATIONS)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Phones inflate a bottom bar, w600dp+ a navigation rail. Both are NavigationBarViews
        // with the same menu and ids, so only one of these is non-null in any configuration.
        navigationBar = binding.bottomNav ?: binding.navRail
        navigationBar?.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isHome = destination.id == R.id.HomeFragment
            if (isHome) {
                binding.fab.show()
                binding.toolbar.setBackgroundColor(Color.TRANSPARENT)
            } else {
                binding.fab.hide()
            }
            // The bar belongs to the top-level destinations; on a detail screen it would offer
            // to switch tabs out from under whatever the user is doing.
            navigationBar?.visibility =
                if (destination.id in TOP_LEVEL_DESTINATIONS) View.VISIBLE else View.GONE
        }

        handleVoipIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        findNavController(R.id.nav_host_fragment_content_main).handleDeepLink(intent)
        handleVoipIntent(intent)
    }

    /** Opens the VoIP Calling screen when launched from the incoming-call notification. */
    private fun handleVoipIntent(intent: Intent) {
        if (!intent.getBooleanExtra(VoipCallService.EXTRA_OPEN_VOIP, false)) return
        val autoAccept = intent.getBooleanExtra(VoipCallService.EXTRA_AUTO_ACCEPT, false)
        val args = Bundle().apply { putBoolean("autoAccept", autoAccept) }
        findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.VoipCallFragment, args)
        // Consume the extras so rotating the screen or another onNewIntent doesn't re-navigate
        // or re-trigger auto-accept.
        intent.removeExtra(VoipCallService.EXTRA_OPEN_VOIP)
        intent.removeExtra(VoipCallService.EXTRA_AUTO_ACCEPT)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.SearchFragment)
                true
            }
            R.id.action_settings -> {
                findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.SettingsFragment)
                true
            }
            R.id.action_windhelm -> {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://windhelm.dev"))
                startActivity(browserIntent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private companion object {
        val TOP_LEVEL_DESTINATIONS = setOf(
            R.id.HomeFragment,
            R.id.ToolsFragment,
            R.id.SettingsFragment,
        )
    }
}