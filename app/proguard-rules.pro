# Release builds do not minify. Kept for a later build that does.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions, *Annotation*
