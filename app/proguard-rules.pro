# =======================================================
# SECURITY & OBFUSCATION RULES
# =======================================================
-obfuscationdictionary obfuscation-dictionary.txt
-classobfuscationdictionary obfuscation-dictionary.txt
-packageobfuscationdictionary obfuscation-dictionary.txt
-repackageclasses 'o'
-allowaccessmodification
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
# =======================================================
# APPLICATION SPECIFIC RULES
# =======================================================
-keep public class com.rianixia.settings.overlay.MainActivity {
    *;
}
-keep class com.rianixia.settings.overlay.security.IntegrityManager {
    public boolean isSafe(android.content.Context);
}
-keep class com.github.megatronking.stringfog.** { *; }
-keep interface com.github.megatronking.stringfog.** { *; }
-keepclassmembers public class * extends androidx.activity.ComponentActivity {
    androidx.lifecycle.Lifecycle getLifecycle();
}
-keep class dev.chrisbanes.haze.** { *; }
-keep interface dev.chrisbanes.haze.** { *; }
-keep class androidx.compose.ui.graphics.** { *; }