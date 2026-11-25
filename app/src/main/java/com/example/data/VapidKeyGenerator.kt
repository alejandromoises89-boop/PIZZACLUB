package com.example.data

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

data class VapidKeyBundle(
    val publicKey: String,
    val privateKey: String,
    val subject: String = DEFAULT_VAPID_SUBJECT
) {
    companion object {
        const val DEFAULT_VAPID_SUBJECT = "mailto:alejandromoises89@gmail.com"
    }

    fun toSupabaseSecretsEnv(): String = buildString {
        appendLine("# Subir en Supabase Dashboard -> Edge Functions -> Secrets")
        appendLine("# O via CLI: supabase secrets set --env-file supabase-push-secrets.env")
        appendLine("VAPID_PUBLIC_KEY=$publicKey")
        appendLine("VAPID_PRIVATE_KEY=$privateKey")
        appendLine("VAPID_SUBJECT=$subject")
    }

    fun toWebDbConfigJs(supabaseUrl: String, supabaseAnonKey: String): String {
        val safeUrl = supabaseUrl.trim().ifBlank { "https://YOUR_PROJECT.supabase.co" }
        val safeKey = supabaseAnonKey.trim().ifBlank { "YOUR_SUPABASE_ANON_KEY" }
        return buildString {
            appendLine("// Configuracion de base de datos online (Supabase PostgreSQL REST) — SIN FIREBASE.")
            appendLine("// Reemplaza web-pwa/db-config.js con este archivo o carga su contenido en el GitHub Secret DB_CONFIG_JS.")
            appendLine("window.PIZZA_CLUB_DB_CONFIG = {")
            appendLine("  url: \"${escapeJsString(safeUrl)}\",")
            appendLine("  anonKey: \"${escapeJsString(safeKey)}\",")
            appendLine("  vapidPublicKey: \"${escapeJsString(publicKey)}\"")
            appendLine("};")
        }
    }

    fun toSupabaseGuideText(supabaseUrl: String): String {
        val cleanUrl = supabaseUrl.trim().trimEnd('/').ifBlank { "https://TU_PROYECTO.supabase.co" }
        val projectRef = cleanUrl.removePrefix("https://").substringBefore(".supabase.co")
            .takeIf { it.isNotBlank() && !it.contains("YOUR_PROJECT") } ?: "TU_PROJECT_REF"
        return buildString {
            appendLine("PIZZA CLUB — GUIA RAPIDA DE NOTIFICACIONES PUSH (ANDROID & IPHONE)")
            appendLine("======================================================================")
            appendLine()
            appendLine("1) CLAVES VAPID GENERADAS (P-256 / RFC 8292)")
            appendLine("   VAPID_PUBLIC_KEY  = $publicKey")
            appendLine("   VAPID_PRIVATE_KEY = $privateKey")
            appendLine("   VAPID_SUBJECT     = $subject")
            appendLine()
            appendLine("2) CARGAR EN SUPABASE (EDGE FUNCTIONS -> SECRETS)")
            appendLine("   Opcion A — Panel Web de Supabase:")
            appendLine("   - Entra a https://supabase.com/dashboard/project/$projectRef/functions/secrets")
            appendLine("   - Crea los 3 secrets con los valores de supabase-push-secrets.env:")
            appendLine("     * VAPID_PUBLIC_KEY")
            appendLine("     * VAPID_PRIVATE_KEY")
            appendLine("     * VAPID_SUBJECT")
            appendLine("   - Verifica que tambien existan SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY.")
            appendLine()
            appendLine("   Opcion B — Supabase CLI:")
            appendLine("   - supabase secrets set --env-file supabase-push-secrets.env")
            appendLine("   - supabase functions deploy order-status-push")
            appendLine()
            appendLine("3) CREAR DATABASE WEBHOOK EN SUPABASE")
            appendLine("   - Ve a Database -> Webhooks -> Create a new hook")
            appendLine("   - Tabla: public.order_status")
            appendLine("   - Eventos: INSERT y UPDATE")
            appendLine("   - Type: Supabase Edge Functions -> order-status-push")
            appendLine()
            appendLine("4) ACTIVAR EN WEB PWA (GITHUB PAGES)")
            appendLine("   - Reemplaza web-pwa/db-config.js con el archivo db-config.js descargado")
            appendLine("     (o pega este contenido en GitHub -> Settings -> Secrets -> DB_CONFIG_JS).")
            appendLine("   - Haz Push a GitHub para actualizar tu sitio HTTPS.")
            appendLine()
            appendLine("5) PROBAR EN ANDROID Y IPHONE")
            appendLine("   - Android (Chrome): Abri la tienda, toca 'Activar alertas' y acepta el permiso.")
            appendLine("   - iPhone (iOS 16.4+ Safari): Abri el link en Safari -> Compartir ->")
            appendLine("     'Agregar a pantalla de inicio', abri la app desde el icono y toca 'Activar alertas'.")
        }
    }

    private fun escapeJsString(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")
}

object VapidKeyGenerator {
    fun generate(subject: String = VapidKeyBundle.DEFAULT_VAPID_SUBJECT): VapidKeyBundle {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = generator.generateKeyPair()
        val ecPub = keyPair.public as ECPublicKey
        val ecPriv = keyPair.private as ECPrivateKey

        val x = bigIntToFixedUnsignedBytes(ecPub.w.affineX, 32)
        val y = bigIntToFixedUnsignedBytes(ecPub.w.affineY, 32)
        val rawPublic = ByteArray(65)
        rawPublic[0] = 0x04
        System.arraycopy(x, 0, rawPublic, 1, 32)
        System.arraycopy(y, 0, rawPublic, 33, 32)

        val rawPrivate = bigIntToFixedUnsignedBytes(ecPriv.s, 32)

        return VapidKeyBundle(
            publicKey = encodeBase64UrlNoPadding(rawPublic),
            privateKey = encodeBase64UrlNoPadding(rawPrivate),
            subject = subject.trim().ifBlank { VapidKeyBundle.DEFAULT_VAPID_SUBJECT }
        )
    }

    internal fun bigIntToFixedUnsignedBytes(value: BigInteger, size: Int): ByteArray {
        val src = value.toByteArray()
        val result = ByteArray(size)
        if (src.size == size) {
            System.arraycopy(src, 0, result, 0, size)
        } else if (src.size > size) {
            // Drop leading sign byte(s)
            System.arraycopy(src, src.size - size, result, 0, size)
        } else {
            System.arraycopy(src, 0, result, size - src.size, src.size)
        }
        return result
    }

    internal fun encodeBase64UrlNoPadding(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val out = StringBuilder((bytes.size * 4 + 2) / 3)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val hasB1 = i + 1 < bytes.size
            val hasB2 = i + 2 < bytes.size
            val b1 = if (hasB1) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) bytes[i + 2].toInt() and 0xFF else 0

            out.append(alphabet[b0 ushr 2])
            out.append(alphabet[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            if (hasB1) {
                out.append(alphabet[((b1 and 0x0F) shl 2) or (b2 ushr 6)])
            }
            if (hasB2) {
                out.append(alphabet[b2 and 0x3F])
            }
            i += 3
        }
        return out.toString()
    }
}
