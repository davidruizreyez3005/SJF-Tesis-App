# Firma de la APK

La firma depende de **un solo secreto**: `ANDROID_UPDATE_SEED`. En cada build,
CI deriva de él la llave RSA-3072 y el certificado con
[`scripts/llave_de_firma.py`](scripts/llave_de_firma.py), los usa para firmar
y borra el almacén temporal al terminar. Ninguna llave se guarda en el
repositorio; `.gitignore` bloquea `*.keystore`, `*.jks`, `*.p12`, etc.

Android exige que todas las versiones de una app estén firmadas con el
**mismo certificado**. La derivación es determinista: la misma semilla produce
siempre el mismo certificado, byte por byte, así que mientras conserves la
semilla podrás publicar actualizaciones que se instalan encima.

## La semilla es la llave

Quien conozca `ANDROID_UPDATE_SEED` puede firmar APK que tu teléfono aceptará
como actualizaciones legítimas. Por eso:

- Debe ser **aleatoria**, no una frase: mínimo 32 caracteres (el script
  rechaza las más cortas). Genera una con `openssl rand -hex 32`.
- Guárdala **fuera de GitHub**, con respaldo. Si se pierde, ya no se podrán
  publicar actualizaciones; habrá que desinstalar y reinstalar con una nueva.
- La derivación pasa por scrypt (lenta a propósito) para encarecer ataques de
  fuerza bruta, pero eso no sustituye una semilla aleatoria.

## 1. Cargar el secreto

En GitHub: **Settings › Secrets and variables › Actions › Secrets › New
repository secret** → nombre `ANDROID_UPDATE_SEED`, valor: la semilla.

## 2. Fijar la huella (recomendado)

La corrida firmada muestra en el paso *Verify APK signature* la huella
SHA-256 del certificado. Créala como **variable** (no secreto; la huella es
pública) en **Settings › Secrets and variables › Actions › Variables**:
`SIGNING_CERT_SHA256`. A partir de ahí CI rechaza cualquier APK firmada con
otra llave (p. ej. si alguien cambia la semilla por error).

## 3. Probar

**Actions › Build APK › Run workflow**. *Prepare signing key* debe decir
«Llave de firma derivada de ANDROID_UPDATE_SEED» y la corrida publica la
Release `v<versión>` con la APK firmada.

## Protección contra cambios en la derivación

Cualquier cambio en el script (o en la librería `cryptography`, fijada a una
versión exacta en el workflow) cambiaría el certificado y rompería las
actualizaciones. CI ejecuta `llave_de_firma.py --probar` en cada build: deriva
con una semilla de prueba pública y exige la huella registrada en el script.
Si no coincide, el build se detiene.

## Compilar firmado en tu computadora

```bash
python3 -m venv /tmp/firma && /tmp/firma/bin/pip install cryptography==49.0.0
read -rs ANDROID_UPDATE_SEED && export ANDROID_UPDATE_SEED
export SIGNING_STORE_FILE=/tmp/sjf-release.p12 SIGNING_KEY_ALIAS=sjf-tesis
export SIGNING_STORE_PASSWORD=$(/tmp/firma/bin/python scripts/llave_de_firma.py "$SIGNING_STORE_FILE")
export SIGNING_KEY_PASSWORD=$SIGNING_STORE_PASSWORD
./gradlew assembleRelease
rm -f "$SIGNING_STORE_FILE"
```

Sin esas variables, `assembleRelease` genera la APK sin firmar.
