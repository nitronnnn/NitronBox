-keepattributes Signature,*Annotation*
-keep class com.nitronbox.app.data.remote.dto.** { *; }
-dontwarn org.conscrypt.**
# pdfbox-android optionally references a JPEG2000 decoder that is not bundled.
-dontwarn com.gemalto.jp2.**
