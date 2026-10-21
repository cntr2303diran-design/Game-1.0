# kotlinx.serialization — pertahankan serializer yang dihasilkan plugin.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class id.diran.lisbon1944.**$$serializer { *; }
-keepclassmembers class id.diran.lisbon1944.** { *** Companion; }
-keepclasseswithmembers class id.diran.lisbon1944.** { kotlinx.serialization.KSerializer serializer(...); }

# File konten JSON dibaca dari resource Java milik modul :engine.
-keepdirectories content

# Kelas yang di-serialisasi (simpanan, arsip, konfigurasi bot) dan enum yang dibaca lewat valueOf.
-keep class id.diran.lisbon1944.engine.model.** { *; }
-keep class id.diran.lisbon1944.ui.ArchiveEntry { *; }
-keep class id.diran.lisbon1944.ui.ArchivePlayer { *; }
-keepclassmembers enum id.diran.lisbon1944.** { *; }

# Pustaka JVM yang dikompilasi untuk Java 17 (string concat dinamis).
-dontwarn java.lang.invoke.StringConcatFactory

# ViewModel dibuat lewat refleksi oleh SavedStateViewModelFactory (Application, SavedStateHandle).
-keep class id.diran.lisbon1944.ui.AppViewModel { <init>(...); }
