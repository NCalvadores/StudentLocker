package com.ifelse101.studentlocker;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;

public class StudentLockerApp extends Application {
    private static final String TAG = "StudentLockerApp";

    @Override
    public void onCreate() {
        super.onCreate();
        initFirebase();
    }

    private void initFirebase() {
        try {
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized.");
        } catch (Exception e) {
            Log.w(TAG, "Firebase initialization skipped or failed. Ensure google-services.json is added to app/", e);
        }
    }
}
