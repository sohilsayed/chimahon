# JNI in `libhoshidicts_jni.so` looks up these classes and constructors by exact name/signature.

# Keep all chimahon classes for JNI - include constructors explicitly
-keep class chimahon.** { <init>(...); *; }
-keepclassmembers class chimahon.** { <init>(...); *; }

# Keep generic signatures for data classes (needed for proper reflection)
-keepattributes Signature
-keepattributes *Annotation*

# Keep @Keep annotation itself
-keep class androidx.annotation.Keep

# word_audio_jni — JNI binding
-keep class chimahon.audio.WordAudioDatabase { <init>(...); *; }
-keep class chimahon.audio.WordAudioDatabase$LocalEntry { <init>(...); <fields>; }
