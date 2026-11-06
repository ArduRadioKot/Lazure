# OkHttp / JSON (EssentialApi, CloudLlmClient)
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-keep class com.rassvet.essential.data.api.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# LiteRT-LM
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.rassvet.essential.litert.** { *; }

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
