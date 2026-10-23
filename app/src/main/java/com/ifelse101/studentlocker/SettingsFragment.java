package com.ifelse101.studentlocker;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.bumptech.glide.Glide;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;



import java.util.concurrent.atomic.AtomicLong;

/* Since I don't understand sht in this code - Nathan
 * Maglalagay ako ng comments sa mga complicated part - Nathan
 * Yup, puro comments hahahaha - Nathan
 *
 * I've used google api as login and it's already
 * connected to firebase - Nathan
 */

public class SettingsFragment extends Fragment {

    private CredentialManager credentialManager;
    private ShapeableImageView imgAvatar;
    private TextView tvName;
    private TextView tvEmail;
    private MaterialButton btnAuth;

    private boolean isSignedIn = false;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) { // Load the list of settings (from preferences.xml) under the header.
        super.onViewCreated(view, savedInstanceState);

        imgAvatar = view.findViewById(R.id.imgAvatar); // Set up the user's info
        tvName = view.findViewById(R.id.tvName);
        tvEmail = view.findViewById(R.id.tvEmail);
        btnAuth = view.findViewById(R.id.btnAuth);
        credentialManager = CredentialManager.create(requireContext());

        // Set up the settings fragment
        if (savedInstanceState == null) {
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settingsContainer, new SettingsPreferencesFragment())
                    .commit();
        }
        AtomicLong lastClickTime = new AtomicLong();
        final long DEBOUNCE_TIME = 2000; // 2 seconds

        btnAuth.setOnClickListener(v -> { //ignores taps within 2 sec
            if (isSignedIn) {
                signOut();
            } else {
                long currentTime = SystemClock.elapsedRealtime();
                if (currentTime - lastClickTime.get() < DEBOUNCE_TIME) {
                    return;
                }
                lastClickTime.set(currentTime);
                signIn();
            }
        });

        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current != null) {
            showUser(current.getDisplayName(), current.getEmail(), current.getPhotoUrl());
        } else {
            showLoggedOut();
        }

    }


    @Override
    public void onDestroyView() { // Release the views when the screen is closed, so no memory is held.
        super.onDestroyView();
        imgAvatar = null;
        tvName = null;
        tvEmail = null;
        btnAuth = null;
    }

    private void signIn() { //Google sign in
        GetSignInWithGoogleOption option =
                new GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        credentialManager.getCredentialAsync( // Get the credentials from Google
                requireContext(), request, new CancellationSignal(),
                ContextCompat.getMainExecutor(requireContext()),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        // Stop if the user already left this screen while the Google popup was open
                        FragmentActivity activity = getActivity();
                        if (activity == null || !isAdded()) return;

                        activity.runOnUiThread(() -> {
                            if (!isAdded() || getView() == null) return;
                            Credential c = result.getCredential();

                            if (c instanceof CustomCredential
                                    && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                                try {
                                    // Unpack the Google token
                                    GoogleIdTokenCredential g = GoogleIdTokenCredential
                                            .createFrom(((CustomCredential) c).getData());

                                    // Convert the Google token into a Firebase login credential
                                    AuthCredential firebaseCred =
                                            GoogleAuthProvider.getCredential(g.getIdToken(), null);

                                    // Log in to Firebase. This is what makes the login persist after restart.
                                    FirebaseAuth.getInstance().signInWithCredential(firebaseCred)
                                            .addOnCompleteListener(requireActivity(), task -> {
                                                if (!isAdded() || getView() == null) return;
                                                if (task.isSuccessful() && task.getResult() != null
                                                        && task.getResult().getUser() != null) {
                                                    // Success: show name, email and photo in the header
                                                    FirebaseUser u = task.getResult().getUser();
                                                    showUser(u.getDisplayName(), u.getEmail(), u.getPhotoUrl());
                                                    Toast.makeText(requireContext(), "Signed In Successfully", Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Log.e("SignIn", "Firebase sign-in failed", task.getException());
                                                    Toast.makeText(requireContext(),
                                                            "Sign-in failed. Please try again.",
                                                            Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                } catch (Exception e) {
                                    // logcat purposes
                                    Log.e("SignIn", "Failed to parse credential", e);
                                    Toast.makeText(requireContext(),
                                            "Sign-in failed: " + e.getLocalizedMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        FragmentActivity activity = getActivity();
                        if (activity == null || !isAdded()) return;
                        activity.runOnUiThread(() -> {
                            if (!isAdded() || getView() == null) return;
                            Log.e("SignIn", "Get credential failed", e);
                            Toast.makeText(requireContext(), "Sign-in failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void signOut() {
        FirebaseAuth.getInstance().signOut();
        credentialManager.clearCredentialStateAsync(
                new ClearCredentialStateRequest(), new CancellationSignal(),
                ContextCompat.getMainExecutor(requireContext()),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void r) {
                        FragmentActivity activity = getActivity();
                        if (activity == null || !isAdded()) return;
                        activity.runOnUiThread(() -> {
                            if (!isAdded() || getView() == null) return;
                            showLoggedOut();
                        });
                    }

                    @Override
                    public void onError(ClearCredentialException e) {
                        FragmentActivity activity = getActivity();
                        if (activity == null || !isAdded()) return;
                        activity.runOnUiThread(() -> {
                            if (!isAdded() || getView() == null) return;
                            showLoggedOut();
                        });
                    }
                });
    }

    private void showUser(String name, String email, Uri photoUrl) {
        isSignedIn = true;
        if (tvName != null) tvName.setText(name != null ? name : "User");
        if (tvEmail != null) tvEmail.setText(email != null ? email : "");
        if (btnAuth != null) btnAuth.setText("Sign out");
        if (imgAvatar != null) {
            if (photoUrl != null) {
                Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(imgAvatar);
            } else {
                imgAvatar.setImageResource(R.drawable.ic_person);
            }
        }
    }

    private void showLoggedOut() { //default
        isSignedIn = false;
        if (tvName != null) tvName.setText("Not signed in");
        if (tvEmail != null) tvEmail.setText("");
        if (btnAuth != null) btnAuth.setText("Sign in");
        if (imgAvatar != null) imgAvatar.setImageResource(R.drawable.ic_person);
        Toast.makeText(requireContext(), "Signed Out Successfully", Toast.LENGTH_SHORT).show();
    }

    public static class SettingsPreferencesFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            Preference privacy = findPreference("pref_about");
            if (privacy != null) {
                privacy.setOnPreferenceClickListener(p -> {
                    Toast.makeText(requireContext(), "Student Locker v1.0", Toast.LENGTH_SHORT).show();
                    return true;
                });
            }
        }
    }

}
