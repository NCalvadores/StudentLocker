package com.ifelse101.studentlocker;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.ifelse101.studentlocker.fragments.files.FilesFragment;
import com.ifelse101.studentlocker.fragments.folder.FolderFragment;
import com.ifelse101.studentlocker.fragments.home.HomeFragment;
import com.ifelse101.studentlocker.fragments.search.SearchFragment;
import com.ifelse101.studentlocker.fragments.settings.SettingsFragment;
import com.ifelse101.studentlocker.fragments.settings.ThemeManager;

import java.util.Map;
import java.util.function.Supplier;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ThemeManager.applyFromPreferences(this);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        applyWindowsInsets();
        showHomeFragmentInsets();
        setupBottomNavigation();
    }

    private void applyWindowsInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigationView, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });
    }

    private void showHomeFragmentInsets() {
        boolean isContainerEmpty =
                getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null;

        if (isContainerEmpty) {
            replaceFragment(new HomeFragment());
        }
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment fragment = createFragmentFor(item.getItemId());
            if (fragment != null) {
                replaceFragment(fragment);
                return true;
            }
            return false;
        });
    }

    @Nullable
    private Fragment createFragmentFor(int menuItemId) {
        final Map<Integer, Supplier<Fragment>> fragmentFactories = Map.of(
                R.id.nav_home, HomeFragment::new,
                R.id.nav_files, FilesFragment::new,
                R.id.nav_folder, FolderFragment::new,
                R.id.nav_search, SearchFragment::new,
                R.id.nav_settings, SettingsFragment::new
        );
        Supplier<Fragment> factory = fragmentFactories.get(menuItemId);
        return factory != null ? factory.get() : null;
    }

    private void replaceFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
