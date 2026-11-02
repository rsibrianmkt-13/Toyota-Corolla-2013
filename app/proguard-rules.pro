# Reglas de R8. El servicio de notificaciones se declara en el Manifest y R8 lo conserva automáticamente.
# MapLibre y androidx.media traen sus propias reglas (consumer rules).

# Release: eliminar los logs de depuración (menos trabajo en el hilo principal).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
