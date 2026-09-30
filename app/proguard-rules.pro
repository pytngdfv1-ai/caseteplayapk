# Mix.Casete – reglas ProGuard
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn com.pierfrancescosoffritti.androidyoutubeplayer.**
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes *Annotation*, InnerClasses, Signature
