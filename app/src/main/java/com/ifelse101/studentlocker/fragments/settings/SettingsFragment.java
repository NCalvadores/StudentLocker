package com.ifelse101.studentlocker.fragments.settings;

import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.StringRes;
import androidx.credentials.exceptions.GetCredentialCancellationException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.fragment.app.Fragment;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.bumptech.glide.Glide;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import com.ifelse101.studentlocker.R;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SignIn";
    private CredentialManager credentialManager;
    private ShapeableImageView imgAvatar;
    private TextView tvName;
    private TextView tvEmail;
    private MaterialButton btnAuth;
    private boolean isSignedIn = false;
    private boolean isSigningIn = false;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imgAvatar = view.findViewById(R.id.imgAvatar);
        tvName = view.findViewById(R.id.tvName);
        tvEmail = view.findViewById(R.id.tvEmail);
        btnAuth = view.findViewById(R.id.btnAuth);
        credentialManager = CredentialManager.create(requireContext());

        loadPreferenceList();
        btnAuth.setOnClickListener(v -> onAuthButtonClicked());
        restoreSession();
    }

    @Override
    public void onDestroyView() { // release the views so no memory is held
        super.onDestroyView();
        imgAvatar = null;
        tvName = null;
        tvEmail = null;
        btnAuth = null;
    }

    /** True while the fragment's view exists, so it is safe to touch the UI. */
    private boolean isUiAlive() {
        return isAdded() && getView() != null;
    }

    /** Disables the button while the Google popup / Firebase login is running. */
    private void setSigningIn(boolean signingIn) {
        isSigningIn = signingIn;
        if (btnAuth != null) {
            btnAuth.setEnabled(!signingIn);
        }
    }
    /** Puts the list from preferences.xml under the profile header (only once). */
    private void loadPreferenceList() {
        if (getChildFragmentManager().findFragmentById(R.id.settingsContainer) == null) {
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settingsContainer, new SettingsPreferencesFragment())
                    .commit();
        }
    }

    /** Firebase remembers the user, so refill the header after a restart. */
    private void restoreSession() {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current != null) {
            showUser(current.getDisplayName(), current.getEmail(), current.getPhotoUrl());
        } else {
            showLoggedOut();
        }
    }

    private void onAuthButtonClicked() {
        if (isSignedIn) {
            signOut();
        } else if (!isSigningIn) {
            signIn();
        }
    }


    /** Step 1: ask Google which account to use (popup). */
    private void signIn() {
        setSigningIn(true);

        GetSignInWithGoogleOption option =
                new GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        credentialManager.getCredentialAsync(
                requireContext(), request, new CancellationSignal(),
                ContextCompat.getMainExecutor(requireContext()),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        if (!isUiAlive()) return;
                        handleCredential(result.getCredential());
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (!isUiAlive()) return;
                        setSigningIn(false);

                        if (e instanceof GetCredentialCancellationException) {
                            Log.w(TAG, "Sign-in cancelled", e);
                            return;
                        }
                        showError(R.string.sign_in_failed, e);
                    }
                });
    }

    /** Step 2: unpack the Google token. */
    private void handleCredential(Credential credential) {
        boolean isGoogleToken = credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType());
        if (!isGoogleToken) {
            setSigningIn(false);
            return;
        }

        try {
            GoogleIdTokenCredential google =
                    GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
            signInToFirebase(google.getIdToken());
        } catch (Exception e) {
            setSigningIn(false);
            showError(R.string.sign_in_failed, e);
        }
    }

    /** Step 3: log in to Firebase. This is what makes the login persist after restart. */
    private void signInToFirebase(String idToken) {
        AuthCredential firebaseCred = GoogleAuthProvider.getCredential(idToken, null);

        FirebaseAuth.getInstance().signInWithCredential(firebaseCred)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (!isUiAlive()) return;

                    FirebaseUser user = (task.isSuccessful() && task.getResult() != null)
                            ? task.getResult().getUser()
                            : null;

                    setSigningIn(false);

                    if (user == null) {
                        showError(R.string.sign_in_failed, task.getException());
                        return;
                    }

                    showUser(user.getDisplayName(), user.getEmail(), user.getPhotoUrl());
                    toast(R.string.signed_in_success);
                });
    }


    private void signOut() {
        FirebaseAuth.getInstance().signOut();
        showLoggedOut();
        toast(R.string.signed_out_success);

        // Also tell Credential Manager to forget the Google account
        credentialManager.clearCredentialStateAsync(
                new ClearCredentialStateRequest(), new CancellationSignal(),
                ContextCompat.getMainExecutor(requireContext()),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) { }

                    @Override
                    public void onError(@NonNull ClearCredentialException e) {
                        Log.w(TAG, "Could not clear credential state", e);
                    }
                });
    }


    private void showUser(@Nullable String name, @Nullable String email, @Nullable Uri photoUrl) {
        isSignedIn = true;
        tvName.setText(name != null ? name : getString(R.string.default_user_name));
        tvEmail.setText(email != null ? email : "");
        btnAuth.setText(R.string.sign_out);

        Glide.with(this)
                .load(photoUrl)
                .disallowHardwareConfig() // avoids a rendering crash on some devices
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .fallback(R.drawable.ic_person)
                .into(imgAvatar);
    }

    private void showLoggedOut() {
        isSignedIn = false;
        tvName.setText(getString(R.string.not_signed_in));
        tvEmail.setText("");
        btnAuth.setText(R.string.sign_in);
        imgAvatar.setImageResource(R.drawable.ic_person);
    }

    /** Technical details go to Logcat only. The user sees a friendly message. */
    private void showError(@StringRes int messageRes, @Nullable Throwable cause) {
        Log.e(TAG, getString(messageRes), cause);
        toast(messageRes);
    }

    private void toast(@StringRes int messageRes) {
        if (isAdded()) {
            Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show();
        }
    }


    public static class SettingsPreferencesFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            Preference about = findPreference("pref_about");
            if (about != null) {
                about.setOnPreferenceClickListener(p -> {
                    Toast.makeText(requireContext(), "Student Locker v1.0", Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            ListPreference themeMode = findPreference("theme_mode");
            if (themeMode != null) {
                themeMode.setOnPreferenceChangeListener((preference, newValue) -> {
                    ThemeManager.applyTheme(String.valueOf(newValue));
                    return true;
                });

                // Default to the saved value if the user has previously selected a theme.
                String savedTheme = themeMode.getValue();
                if (savedTheme == null || savedTheme.isEmpty()) {
                    themeMode.setValue(ThemeManager.THEME_MODE_SYSTEM);
                }
            }
        }
    }


}
