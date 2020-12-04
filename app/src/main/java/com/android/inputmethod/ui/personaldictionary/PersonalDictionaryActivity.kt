package com.android.inputmethod.ui.personaldictionary

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.android.inputmethod.latin.R
import kotlinx.android.synthetic.main.activity_personal_dictionary.*


class PersonalDictionaryActivity : AppCompatActivity() {
    private lateinit var host: NavHostFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_dictionary)
        host = f_dictionary_navhost as NavHostFragment

        setSupportActionBar(tl_dictionary)
        supportActionBar?.setHomeButtonEnabled(true)
        NavigationUI.setupActionBarWithNavController(this, host.navController)

    }

    override fun onSupportNavigateUp() = host.navController.navigateUp()
}

