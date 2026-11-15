# FreshCart ProGuard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.database.PropertyName <methods>;
}
-keep class com.hack.data.model.** { *; }
