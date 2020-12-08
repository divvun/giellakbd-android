package com.android.inputmethod.ui.personaldictionary

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.android.inputmethod.latin.databinding.ActivityPersonalDictionaryBinding


class PersonalDictionaryActivity : AppCompatActivity() {
    private lateinit var host: NavHostFragment
    private lateinit var binding: ActivityPersonalDictionaryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonalDictionaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        host = binding.fDictionaryNavhost as NavHostFragment

        val appBarConfiguration = AppBarConfiguration.Builder().build()
        setSupportActionBar(binding.tlDictionary)
        NavigationUI.setupActionBarWithNavController(this, host.navController, appBarConfiguration)
    }

    override fun onSupportNavigateUp(): Boolean {
        return if (host.navController.navigateUp()) {
            true
        } else {
            finish()
            true
        }
    }
}

