# 📚 StudentLocker

Welcome to **StudentLocker**! This is a Java-based Android application built using Android Studio. It is designed to help students organize, store, and manage their academic notes, folders, and documents in one convenient place.

This guide explains how the project is structured, **where you can write code**, **what you can change**, and best practices to keep our codebase clean.

---

## 🚀 Quick Setup Guide

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/ifelse101/StudentLocker.git
   ```
2. **Open in Android Studio**:
   - Open Android Studio -> **File -> Open...** -> select the `StudentLocker` folder.
   - Wait for Gradle to download dependencies and finish indexing.
3. **Run the App**:
   - Connect an Android phone with USB Debugging enabled, or launch an Android Virtual Device (Emulator).
   - Press the **Run** button (`Shift + F10` or the green play icon ▶️).

---

## 📁 Where to Write Code & What You Can Change

Here is a map of the project folders you will work with:

```text
StudentLocker/
└── app/src/main/
    ├── java/com/ifelse101/studentlocker/   <-- Java source code (Activities, UI Logic)
    └── res/                                 <-- App Resources (Layouts, Strings, Colors)
        ├── layout/                          <-- XML Layout files (Screen designs)
        ├── values/                          <-- Strings, Colors, Themes
        └── drawable/                        <-- Icons, Buttons, Background images
```

### 1. 🎨 Screen Designs & UI Layouts
**Location**: `app/src/main/res/layout/`

- **What it is**: XML files that define how your app screens look (buttons, text boxes, cards, lists).
- **What you can change**:
  - `activity_main.xml`: The main screen layout.
  - **Adding new screens**: Right-click `layout/` -> **New -> Layout Resource File** (e.g., `activity_folder_detail.xml`).
- **Tips**:
  - Use `ConstraintLayout` or `LinearLayout` for screen structures.
  - Give every view a clear ID (e.g., `android:id="@+id/btn_add_folder"` or `android:id="@+id/tv_title"`).

---

### 2. ⚡ Java Code & Screen Logic
**Location**: `app/src/main/java/com/ifelse101/studentlocker/`

- **What it is**: Java files that control user actions, button clicks, screen navigation, and app features.
- **What you can change**:
  - `MainActivity.java`: The primary screen controller. You can handle button clicks, open new screens, and update UI elements here.
  - **Adding new screens**: Right-click `com.ifelse101.studentlocker` -> **New -> Activity -> Empty Views Activity**.
- **Example - Finding a view and handling a button click**:
  ```java
  Button btnAdd = findViewById(R.id.btn_add_folder);
  btnAdd.setOnClickListener(v -> {
      // Your code here when user clicks the button
  });
  ```

---

### 3. 📝 Strings, Colors & Branding
**Location**: `app/src/main/res/values/`

- **`strings.xml`**:
  - Store all user-facing text here instead of hardcoding text in XML layouts.
  - Example: `<string name="app_name">Student Locker</string>`
- **`colors.xml`**:
  - Define custom brand colors used across the app (primary colors, background colors, card colors).
  - Example: `<color name="primary_blue">#1976D2</color>`
- **`themes.xml`**:
  - Controls the general styling, action bar visibility, and dark mode appearance.

---

### 4. 🖼️ Icons & Visual Assets
**Location**: `app/src/main/res/drawable/`

- Place custom icons, background shapes, or image assets here.
- Right-click `drawable/` -> **New -> Vector Asset** to pick built-in Material design icons.

---

## 🛠️ Contributor Guidelines for Students

To make sure everyone can work smoothly without breaking each other's code:

1. **Use Git Branches**:
   - Do not push directly to `main`.
   - Create a branch for your feature:
     ```bash
     git checkout -b feature/ui-redesign
     ```
2. **Never Hardcode Strings**:
   - Always put text labels in `res/values/strings.xml` and reference them as `@string/your_text_name`.
3. **Use Meaningful ID Names**:
   - Good: `@+id/btn_save_document`, `@+id/tv_folder_name`
   - Avoid: `@+id/button1`, `@+id/textView2`
4. **Test Before Pushing**:
   - Make sure the project builds without errors before creating a Pull Request.
   - Go to **Build -> Make Project** in Android Studio (`Ctrl + F9` or `Cmd + F9`).

---

## ❓ Troubleshooting Common Errors

- **"Cannot resolve symbol R" or Red Text in XML/Java**:
  - Go to **Build -> Clean Project**, then **Build -> Rebuild Project**.
- **Gradle Sync Failed**:
  - Go to **File -> Sync Project with Gradle Files**.
- **App Crashing on Launch**:
  - Check the **Logcat** tab at the bottom of Android Studio and filter by `Error` to find the exact line causing the issue.

---

Happy Coding! and Goodluck
