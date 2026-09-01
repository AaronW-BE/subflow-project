# SubFlow Commercial Production Proguard / R8 Rules

# General
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Room Database
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>();
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Domain & Data Models (Gson serialization preservation)
-keep class org.dpdns.alwaysup.subflow.domain.model.** { *; }
-keep class org.dpdns.alwaysup.subflow.data.local.** { *; }
-keep class org.dpdns.alwaysup.subflow.data.remote.** { *; }
-keepclassmembers class org.dpdns.alwaysup.subflow.domain.model.** { <fields>; }
-keepclassmembers class org.dpdns.alwaysup.subflow.data.remote.** { <fields>; }
-keepattributes *Annotation*
-keepclassmembers enum * { *; }

# Retrofit & OkHttp
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Google Play Billing Library
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.api.**

# Google Mobile Ads (AdMob)
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# AndroidX WorkManager
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Credentials & Identity
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**


# Google Identity Services (Sign in with Google via Credential Manager)
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# User Messaging Platform (GDPR/TCF consent)
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# Gson uses reflection over the generic signature of TypeToken subclasses.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep line numbers but strip the original file name from stack traces.
-renamesourcefileattribute SourceFile
