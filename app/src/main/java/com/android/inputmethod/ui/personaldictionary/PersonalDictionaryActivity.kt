package com.android.inputmethod.ui.personaldictionary

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.Insets
import androidx.navigation.fragment.NavHostFragment
import com.android.inputmethod.latin.databinding.ActivityPersonalDictionaryBinding


class PersonalDictionaryActivity : AppCompatActivity() {
    //    private lateinit var host: NavHostFragment
    private lateinit var binding: ActivityPersonalDictionaryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonalDictionaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle edge-to-edge display for Android 15+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            handleEdgeToEdge()
        }

//        host = binding.fDictionaryNavhost as NavHostFragment

        setSupportActionBar(binding.tlDictionary)
//        NavigationUI.setupActionBarWithNavController(this, host.navController, appBarConfiguration)
    }

    private fun handleEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            binding.ablDictionary.setPadding(
                binding.ablDictionary.paddingLeft,
                systemBars.top,
                binding.ablDictionary.paddingRight,
                binding.ablDictionary.paddingBottom
            )
            
            binding.fDictionaryNavhost.setPadding(
                binding.fDictionaryNavhost.paddingLeft,
                binding.fDictionaryNavhost.paddingTop,
                binding.fDictionaryNavhost.paddingRight,
                systemBars.bottom
            )
            
            insets
        }
    }

//    override fun onSupportNavigateUp(): Boolean {
//        return if (host.navController.navigateUp()) {
//            true
//        } else {
//            finish()
//            true
//        }
//    }
}

