# Leapy added begin: Runtime contracts common to ROM applications, not blanket implementation keeps.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,AnnotationDefault
-renamesourcefileattribute SourceFile
-keepnames class * extends android.app.Activity
-keepnames class * extends android.app.Service
-keepnames class * extends android.content.BroadcastReceiver
-keepnames class * extends android.content.ContentProvider
-keepnames class * extends android.app.Fragment
-keepnames class * extends androidx.fragment.app.Fragment
-keepnames class * implements android.os.Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keep,allowoptimization,allowobfuscation class * extends com.google.gson.reflect.TypeToken {
    <init>(...);
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-keepclassmembers class * extends com.google.protobuf.nano.MessageNano { <fields>; }
-keep class * implements com.dream.ext.core.IExtCreator { <init>(...); }
-keep interface com.dream.ext.core.IExtCreator { *; }
# Leapy end
