# Mix.Casete 📼

Reproductor de música estilo **casete retro** para Android: Kotlin + Jetpack Compose (Material 3),
Media3 ExoPlayer + MediaSessionService, YouTube IFrame Player como fuente principal,
NewPipeExtractor como respaldo, archivos locales y previews de iTunes/Deezer.

- `applicationId`: `com.mixcasete.app` · `minSdk 23` · `targetSdk 34`
- El APK de release se compila automáticamente con **GitHub Actions**.

## Icono de la app

El icono es un casete dibujado vectorialmente (`res/drawable/ic_launcher_foreground.xml`)
sobre fondo crema, con versión **adaptive icon** (Android 8+), variante **monocroma**
(Android 13+) y PNGs genéricos `ic_launcher.png` / `ic_launcher_round.png` en todas las
densidades (`mipmap-mdpi … mipmap-xxxhdpi`) para dispositivos antiguos.

## Compilar localmente

```bash
# Requiere JDK 17 y el SDK de Android (o deja que Android Studio lo instale)
./gradlew assembleDebug      # APK de prueba -> app/build/outputs/apk/debug/
./gradlew assembleRelease    # APK de release (ve el paso del keystore abajo)
```

## Generar el keystore de firma (una sola vez)

```bash
keytool -genkeypair -v \
  -keystore mixcasete.jks \
  -alias mixcasete \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass TU_STORE_PASSWORD \
  -keypass TU_KEY_PASSWORD
```

Guarda `mixcasete.jks` en un lugar seguro. **Nunca lo subas al repositorio**
(ya está excluido en `.gitignore`). Para firmar localmente, copia el archivo a
`app/keystore/mixcasete.jks` y exporta las variables `STORE_PASSWORD`, `KEY_ALIAS`
y `KEY_PASSWORD` antes de `./gradlew assembleRelease`.

## Secretos de GitHub Actions

En tu repositorio: **Settings → Secrets and variables → Actions → New repository secret**

| Secreto | Valor |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 mixcasete.jks` (Linux) o `openssl base64 -A -in mixcasete.jks` (macOS) |
| `KEY_ALIAS` | `mixcasete` (el alias usado en keytool) |
| `KEY_PASSWORD` | contraseña de la clave |
| `STORE_PASSWORD` | contraseña del keystore |

Si los secretos no existen, el workflow **genera automáticamente un keystore temporal
de CI** (con `keytool`) y firma el APK con él: la compilación nunca falla por falta de
secretos. Ese APK sirve para instalar y probar, pero no es válido para Play Store ni
para actualizaciones futuras (la clave cambia en cada run). Para una firma permanente,
configura los cuatro secretos de la tabla.

## Workflow de CI

`.github/workflows/build.yml`:

1. `actions/checkout@v4`
2. `actions/setup-java@v4` con **Temurin JDK 17**
3. `gradle/actions/setup-gradle@v4` (caché de Gradle)
4. Decodifica el keystore desde `KEYSTORE_BASE64` (si los secretos están configurados)
5. `./gradlew assembleRelease`
6. `actions/upload-artifact@v4` sube el APK

Se ejecuta en cada `push` y `pull_request` sobre `main`, y también manualmente
(**Actions → Build APK → Run workflow**).

## Descargar el APK

1. Abre la pestaña **Actions** del repositorio y entra en la última ejecución (✓ verde).
2. Baja hasta **Artifacts** y descarga `Mix.Casete-release` (o `Mix.Casete-debug`).
3. Descomprime: contiene `app-release.apk` (firmado si cargaste los secretos).
4. Instala en el teléfono: `adb install app-release.apk` o cópialo y ábrelo
   (activa "instalar apps desconocidas" si es necesario).

## Pruebas manuales recomendadas

- Girar el teléfono durante la reproducción (no debe reiniciarse ni deformarse la piel).
- Cortar el WiFi en plena reproducción (backoff, reintento y cambio de fuente automática).
- Cambiar de fuente (YouTube → NewPipe → local → preview de 30 s con aviso).
- Abrir/cerrar la tapa al pulsar EJECT e insertar otro casete.
- Bloquear la pantalla: notificación y controles de bloqueo siguen funcionando;
  al desconectar auriculares la reproducción se pausa.
- Botón **Compartir**: con cable/CAST el móvil actúa como mando a distancia y la TV
  muestra solo el vídeo a pantalla completa (letterbox negro, sin interfaz).
