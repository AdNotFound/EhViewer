/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.ui

import android.os.Bundle
import android.content.Intent
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.snackbar.Snackbar
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.ui.fragment.EhFragment
import com.hippo.ehviewer.ui.fragment.SettingsFragment
import com.hippo.ehviewer.ui.scene.BaseScene

class SettingsActivity : EhActivity() {
    val isTwoPaneLayout: Boolean
        get() = findViewById<View?>(R.id.fragment_detail) != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            if (Settings.layoutEnabled && Settings.layoutSettingsTwoPane) {
                R.layout.activity_preference_large
            } else {
                R.layout.activity_preference
            },
        )
        setSupportActionBar(findViewById(R.id.toolbar))
        val bar = supportActionBar
        bar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            val transaction = supportFragmentManager
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_MATCH_ACTIVITY_OPEN)
            if (isTwoPaneLayout) {
                transaction
                    .replace(R.id.fragment_list, SettingsFragment())
                    .replace(R.id.fragment_detail, EhFragment())
            } else {
                transaction.replace(R.id.fragment, SettingsFragment())
            }
            transaction.commitAllowingStateLoss()
        }
    }

    fun showTip(@StringRes id: Int, length: Int) {
        showTip(getString(id), length)
    }

    fun showTip(message: CharSequence?, length: Int) {
        Snackbar.make(
            findViewById(R.id.snackbar),
            message!!,
            if (length == BaseScene.LENGTH_LONG) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT,
        ).show()
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_RECREATE_MAIN_ACTIVITY = "recreate_main_activity"

        fun recreateMainActivityResult() = Intent().putExtra(EXTRA_RECREATE_MAIN_ACTIVITY, true)
    }
}
