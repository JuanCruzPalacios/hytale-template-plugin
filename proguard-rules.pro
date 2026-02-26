# Mantener atributos mínimos útiles
-keepattributes *Annotation*,Signature,EnclosingMethod,InnerClasses
-dontwarn **

# Quitar metadata para que el decompilado sea más feo
-renamesourcefileattribute SourceFile
-keepattributes Exceptions

# Mantener SOLO el entrypoint (CAMBIAR por tu main real)
-keep class org.jcp.plugin.ExplosivesPackPlugin { *; }

# Mantener cualquier clase que extienda JavaPlugin (por si tu main cambia)
-keep class ** extends com.hypixel.hytale.server.core.plugin.JavaPlugin { *; }

# Mantener constructores públicos (muchos frameworks instancian por reflection)
-keepclassmembers class * {
  public <init>(...);
}