-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

-repackageclasses
-allowaccessmodification
-overloadaggressively

-keepattributes JavascriptInterface,Annotation
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.hippo.ehviewer.util.SniBypassInterface {
    public <methods>;
}
