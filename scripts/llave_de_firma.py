#!/usr/bin/env python3
"""
Deriva la llave de firma de la APK a partir de un único secreto,
ANDROID_UPDATE_SEED.

Android solo instala una actualización si viene firmada con exactamente el
mismo certificado que la versión instalada. Este script produce, para una
misma semilla, siempre la misma llave RSA-3072 y el mismo certificado byte por
byte, así que basta con conservar la semilla para seguir publicando
actualizaciones.

  - La semilla pasa por scrypt (costoso a propósito, frena ataques de fuerza
    bruta) y de ahí un generador HMAC-SHA256 produce los primos de la llave.
  - El certificado usa campos fijos (sujeto, número de serie derivado,
    vigencia fija) y una firma RSA PKCS#1 v1.5, que es determinista.

La semilla ES la llave: quien la conozca puede firmar actualizaciones. Debe
ser aleatoria (p. ej. `openssl rand -hex 32`) y guardarse fuera de GitHub.

NO CAMBIAR NADA DE LA DERIVACIÓN: cualquier cambio produce otra llave y la app
ya no se podría actualizar. `--probar` lo vigila en CI.

Uso (CI):
  ANDROID_UPDATE_SEED=… python3 scripts/llave_de_firma.py RUTA.p12
    Escribe el almacén PKCS12 (permisos 600) e imprime su contraseña en la
    salida estándar (CI la enmascara). La huella SHA-256 del certificado va a
    la salida de error.
  python3 scripts/llave_de_firma.py --probar
    Comprueba que la derivación no haya cambiado (semilla de prueba fija).
"""
import datetime
import hashlib
import hmac
import os
import sys

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

VERSION = b"sjf-tesis/android-signing/v1"
BITS = 3072
E = 65537
ALIAS = "sjf-tesis"
LONGITUD_MINIMA = 32

# Huella del certificado que debe salir con la semilla de prueba. Si cambia,
# la derivación cambió y las llaves reales también: no publicar.
SEMILLA_DE_PRUEBA = "semilla de prueba publica - nunca usar para firmar"
HUELLA_DE_PRUEBA = "8635539e8352bbb3b3fecd4da590544ad9ab7d53e6ddb87bdceb1cb6774f5585"


def _maestra(semilla: str) -> bytes:
    return hashlib.scrypt(
        semilla.encode("utf-8"), salt=VERSION, n=2**17, r=8, p=1, maxmem=256 * 1024 * 1024, dklen=64
    )


class _Flujo:
    """Bytes pseudoaleatorios deterministas: HMAC-SHA256(maestra, etiqueta ‖ contador)."""

    def __init__(self, maestra: bytes, etiqueta: bytes):
        self._clave, self._etiqueta, self._contador, self._resto = maestra, etiqueta, 0, b""

    def bytes(self, n: int) -> bytes:
        while len(self._resto) < n:
            bloque = self._etiqueta + self._contador.to_bytes(8, "big")
            self._resto += hmac.new(self._clave, bloque, hashlib.sha256).digest()
            self._contador += 1
        salida, self._resto = self._resto[:n], self._resto[n:]
        return salida


_PRIMOS_CHICOS = [p for p in range(3, 2000) if all(p % d for d in range(2, int(p**0.5) + 1))]


def _es_primo(n: int) -> bool:
    for p in _PRIMOS_CHICOS:
        if n % p == 0:
            return n == p
    d, s = n - 1, 0
    while d % 2 == 0:
        d, s = d // 2, s + 1
    for a in _PRIMOS_CHICOS[:40]:  # Miller-Rabin con 40 bases
        x = pow(a, d, n)
        if x in (1, n - 1):
            continue
        for _ in range(s - 1):
            x = pow(x, 2, n)
            if x == n - 1:
                break
        else:
            return False
    return True


def _primo(flujo: _Flujo, bits: int) -> int:
    while True:
        n = int.from_bytes(flujo.bytes(bits // 8), "big")
        n |= (3 << (bits - 2)) | 1  # dos bits altos: el módulo tiene exactamente 2·bits bits
        if (n - 1) % E != 0 and _es_primo(n):
            return n


def _mcd(a: int, b: int) -> int:
    while b:
        a, b = b, a % b
    return a


def derivar(semilla: str):
    """Devuelve (llave privada, certificado, contraseña del almacén)."""
    if len(semilla) < LONGITUD_MINIMA:
        raise ValueError(f"ANDROID_UPDATE_SEED es demasiado corta (mínimo {LONGITUD_MINIMA} caracteres aleatorios)")
    maestra = _maestra(semilla)
    p = _primo(_Flujo(maestra, b"p"), BITS // 2)
    q = _primo(_Flujo(maestra, b"q"), BITS // 2)
    if p == q:
        raise ValueError("primos iguales")
    lam = (p - 1) * (q - 1) // _mcd(p - 1, q - 1)
    d = pow(E, -1, lam)
    llave = rsa.RSAPrivateNumbers(
        p=p, q=q, d=d,
        dmp1=rsa.rsa_crt_dmp1(d, p), dmq1=rsa.rsa_crt_dmq1(d, q), iqmp=rsa.rsa_crt_iqmp(p, q),
        public_numbers=rsa.RSAPublicNumbers(E, p * q),
    ).private_key()

    nombre = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, "SJF Tesis"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "SJF Tesis"),
        x509.NameAttribute(NameOID.COUNTRY_NAME, "MX"),
    ])
    serie = (int.from_bytes(_Flujo(maestra, b"serie").bytes(16), "big") >> 1) or 1
    certificado = (
        x509.CertificateBuilder()
        .subject_name(nombre)
        .issuer_name(nombre)
        .public_key(llave.public_key())
        .serial_number(serie)
        .not_valid_before(datetime.datetime(2026, 1, 1, tzinfo=datetime.timezone.utc))
        .not_valid_after(datetime.datetime(2126, 1, 1, tzinfo=datetime.timezone.utc))
        .sign(llave, hashes.SHA256())
    )
    contrasena = _Flujo(maestra, b"contrasena").bytes(24).hex()
    return llave, certificado, contrasena


def huella(certificado) -> str:
    return certificado.fingerprint(hashes.SHA256()).hex()


def main() -> int:
    if sys.argv[1:] == ["--probar"]:
        _, c1, _ = derivar(SEMILLA_DE_PRUEBA)
        _, c2, _ = derivar(SEMILLA_DE_PRUEBA)
        if c1.public_bytes(serialization.Encoding.DER) != c2.public_bytes(serialization.Encoding.DER):
            print("✗ La derivación no es determinista", file=sys.stderr)
            return 1
        h = huella(c1)
        if h != HUELLA_DE_PRUEBA:
            print(f"✗ La derivación cambió: {h} ≠ {HUELLA_DE_PRUEBA}", file=sys.stderr)
            return 1
        print("✓ Derivación de la llave sin cambios")
        return 0

    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    llave, certificado, contrasena = derivar(os.environ.get("ANDROID_UPDATE_SEED", ""))
    almacen = pkcs12.serialize_key_and_certificates(
        ALIAS.encode(), llave, certificado, None, serialization.BestAvailableEncryption(contrasena.encode())
    )
    descriptor = os.open(sys.argv[1], os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "wb") as f:
        f.write(almacen)
    print(contrasena)
    print(f"Huella SHA-256 del certificado: {huella(certificado)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
