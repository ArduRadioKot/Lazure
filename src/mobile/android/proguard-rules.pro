# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# LiteRT-LM (JNI)
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.rassvet.essential.litert.** { *; }

# sherpa-onnx: the JNI library reads config fields and creates result objects by name, so nothing may be renamed.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# ONNX Runtime Java API (DTLN, MossFormer2) is JNI-backed as well.
-keep class ai.onnxruntime.** { *; }

# OkHttp (local model catalog downloads)
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
