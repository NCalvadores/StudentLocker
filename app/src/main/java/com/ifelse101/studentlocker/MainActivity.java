package com.ifelse101.studentlocker;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) { // Set up Bottom Navigation
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigationView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });
        setupBottomNavigation();
    }

    private void replaceFragment(Fragment fragment) { // Replace the current fragment with the new fragment
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void setupBottomNavigation() {
        // Show HomeFragment by default when the app opens for the first time
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            replaceFragment(new HomeFragment());
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                replaceFragment(new HomeFragment());
                return true;
            } else if (itemId == R.id.nav_files) {
                replaceFragment(new FilesFragment());
                return true;
            } else if (itemId == R.id.nav_folder) {
                replaceFragment(new FolderFragment());
                return true;
            } else if (itemId == R.id.nav_search) {
                replaceFragment(new SearchFragment());
                return true;
            } else if (itemId == R.id.nav_settings) {
                replaceFragment(new SettingsFragment());
                return true;
            }
            return false;
        });
    }
}
