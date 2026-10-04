# Firma de la APK

La llave de firma **nunca** se guarda en el repositorio. CI la recibe de los
secretos de GitHub, la escribe en un archivo temporal del runner y lo borra al
terminar. `.gitignore` bloquea cualquier archivo de llave (`*.keystore`,
`*.jks`, `*.p12`, `keystore.properties`…).

Android exige que todas las versiones de una app estén firmadas con la **misma
llave**: si cambia, una actualización no se puede instalar encima de la
anterior. Por eso la llave se crea una sola vez y se conserva.

## 1. Crear la llave (una sola vez, en tu computadora)

Requiere Java (`keytool` viene incluido):

```bash
keytool -genkeypair -v \
  -keystore sjf-tesis-release.jks \
  -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias sjf-tesis
```

`keytool` pedirá una contraseña y tus datos (nombre, organización, ciudad…).
Con almacenes PKCS12 la contraseña de la llave es la misma que la del almacén.

**Guarda el archivo `.jks` y la contraseña en un lugar seguro fuera de GitHub**
(por ejemplo, un gestor de contraseñas con respaldo). Si se pierden, ya no se
podrán publicar actualizaciones de la app. No lo copies dentro de esta carpeta
del proyecto.

## 2. Cargarla como secretos del repositorio

En GitHub: **Settings › Secrets and variables › Actions › Secrets › New
repository secret**. Crea estos cuatro:

| Secreto | Valor |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | El archivo `.jks` en Base64 (ver abajo) |
| `SIGNING_STORE_PASSWORD` | La contraseña que elegiste |
| `SIGNING_KEY_PASSWORD` | La misma contraseña |
| `SIGNING_KEY_ALIAS` | `sjf-tesis` (o el alias que usaste) |

Para obtener el Base64 del archivo:

```bash
base64 -w0 sjf-tesis-release.jks    # Linux
base64 -i sjf-tesis-release.jks     # macOS
```

## 3. Fijar la huella de la llave (recomendado)

Obtén la huella SHA-256 del certificado:

```bash
keytool -list -v -keystore sjf-tesis-release.jks -alias sjf-tesis | grep SHA256
```

En **Settings › Secrets and variables › Actions › Variables › New repository
variable** crea `SIGNING_CERT_SHA256` con ese valor (con o sin `:`). A partir
de ahí, CI rechaza cualquier APK firmada con otra llave. La huella es pública
por naturaleza: no es un secreto.

## 4. Probar

**Actions › Build APK › Run workflow**. El paso *Prepare signing key* debe
decir «Llave de firma tomada de los secretos del repositorio» y *Verify APK
signature*, «Firma verificada».

## Compilar firmado en tu computadora

Exporta las variables antes de compilar (sin guardarlas en el proyecto):

```bash
export SIGNING_STORE_FILE=/ruta/fuera/del/proyecto/sjf-tesis-release.jks
export SIGNING_STORE_PASSWORD=…  SIGNING_KEY_PASSWORD=…  SIGNING_KEY_ALIAS=sjf-tesis
gradle assembleRelease
```

Sin esas variables, `assembleRelease` genera la APK sin firmar.
