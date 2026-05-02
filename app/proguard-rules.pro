# =======================================================
# SECURITY & OBFUSCATION RULES
# =======================================================

# 1. Use the nonsense dictionary for renaming classes and methods
-obfuscationdictionary obfuscation-dictionary.txt
-classobfuscationdictionary obfuscation-dictionary.txt
-packageobfuscationdictionary obfuscation-dictionary.txt

# 2. Repackage all classes into a single flat package named 'o'
# This hides your folder structure (e.g., com.rianixia.ui... becomes o.Il1l1)
-repackageclasses 'o'

# 3. Allow changing access modifiers to open up more optimization/renaming opportunities
-allowaccessmodification

# 4. Remove all metadata that could help reverse engineers
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
# Uncomment the line below to REMOVE line numbers (Makes crash logs hard to read, but safer)
#-keepattributes !SourceFile,!LineNumberTable

# =======================================================
# APPLICATION SPECIFIC RULES
# =======================================================

# Keep the entry point (Launcher Activity) otherwise Android cannot start the app
-keep public class com.rianixia.settings.overlay.MainActivity {
    *;
}

# Keep the IntegrityManager logic working but rename internal methods if possible
-keep class com.rianixia.settings.overlay.security.IntegrityManager {
    public boolean isSafe(android.content.Context);
}

# StringFog needs to access its decryptor
-keep class com.github.megatronking.stringfog.** { *; }
-keep interface com.github.megatronking.stringfog.** { *; }

# Jetpack Compose specific rules (Compose uses reflection heavily)
-keepclassmembers public class * extends androidx.activity.ComponentActivity {
    androidx.lifecycle.Lifecycle getLifecycle();
}

# Keep Haze classes to prevent runtime crashes
-keep class dev.chrisbanes.haze.** { *; }
-keep interface dev.chrisbanes.haze.** { *; }

# Keep Compose/Graphics classes that might be accessed via reflection
-keep class androidx.compose.ui.graphics.** { *; }