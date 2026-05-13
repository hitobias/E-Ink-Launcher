# Project-specific ProGuard rules.

# Preserve resource IDs accessed by name.
-keep public class cn.modificator.launcher.R$* {
    public static final int *;
}

-keeppackagenames doNotKeepAThing
-renamesourcefileattribute SourceFile
-keepattributes LineNumberTable,SourceFile,*Annotation*

-repackageclasses ''
-optimizationpasses 5
