# -------------------------------------------------
# Safe: no shrink, no optimize (para no romper runtime)
# -------------------------------------------------
-dontshrink
-dontoptimize

# ✅ NO pongas -dontpreverify (lo necesitamos en Java 25)
# ProGuard va a regenerar los StackMapFrames correctos.

# (Opcional) fuerza target al mismo bytecode
-target 25

# Mantener atributos importantes
-keepattributes *Annotation*,Signature,EnclosingMethod,InnerClasses,Exceptions

# (Opcional) sacar nombres de source para que moleste más al decompilar
-renamesourcefileattribute SourceFile

-dontwarn **

# -------------------------------------------------
# Entrypoint del plugin
# -------------------------------------------------
-keep class ** extends com.hypixel.hytale.server.core.plugin.JavaPlugin { *; }

# -------------------------------------------------
# NO ofuscar interacciones (como pediste)
# -------------------------------------------------
-keep class org.jcp.plugin.barrelcrate.interaction.** { *; }
-keep class org.jcp.plugin.interaction.** { *; }

# -------------------------------------------------
# Para no romper persistencia / codecs: mantener nombres de clases
# (pero permite renombrar miembros)
# -------------------------------------------------
-keepnames class org.jcp.plugin.**

# Enums (codec + Enum.valueOf)
-keep enum org.jcp.plugin.** { *; }
-keepclassmembers enum org.jcp.plugin.** { *; }

# Mantener fields CODEC
-keepclassmembers class org.jcp.plugin.** {
    public static ** CODEC;
    public <init>(...);
}