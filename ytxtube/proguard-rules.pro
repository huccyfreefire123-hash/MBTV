# Consumer rules: what the YTXTube code needs kept when the app is minified.
# (Global optimization flags from the original app were dropped: libraries may not set them.)

-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.annotation.**
-dontwarn jdk.dynalink.**

-keep class org.schabi.newpipe.extractor.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

-dontwarn androidx.media3.**
