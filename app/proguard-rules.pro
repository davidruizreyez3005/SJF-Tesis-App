# Reglas de R8 para la versión de release.
#
# La app no usa reflexión ni serialización automática (el JSON se lee y se
# escribe a mano con org.json), y OkHttp y AndroidX traen sus propias reglas.
# Por eso basta con reducir: no se renombran clases ni métodos, así los
# errores que registra la app (NetLog) siguen siendo legibles.
-dontobfuscate

# Números de línea en los errores.
-keepattributes SourceFile,LineNumberTable
