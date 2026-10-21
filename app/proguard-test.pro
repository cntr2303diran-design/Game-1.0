# APK uji instrumentasi saat diuji terhadap build rilis (R8 dijalankan juga pada APK uji).
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn org.jspecify.annotations.**
-keep class id.diran.lisbon1944.ReleaseSmokeTest { *; }
-keep class androidx.test.** { *; }
-keep class org.junit.** { *; }
-keep class junit.** { *; }
