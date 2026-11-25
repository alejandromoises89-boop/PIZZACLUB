package com.example

import com.example.data.VapidKeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VapidKeyGeneratorTest {

    @Test
    fun generatedVapidKeysMatchRfc8292LengthsAndBase64UrlCharset() {
        val bundle = VapidKeyGenerator.generate()
        // 65-byte uncompressed P-256 key -> 87 Base64URL chars without padding
        assertEquals(87, bundle.publicKey.length)
        // Uncompressed point starts with 0x04 -> first 6 bits are 000001 -> 'B'
        assertTrue(bundle.publicKey.startsWith("B"))
        // 32-byte private scalar -> 43 Base64URL chars without padding
        assertEquals(43, bundle.privateKey.length)

        val base64UrlRegex = Regex("^[A-Za-z0-9_-]+$")
        assertTrue(base64UrlRegex.matches(bundle.publicKey))
        assertTrue(base64UrlRegex.matches(bundle.privateKey))
        assertFalse(bundle.publicKey.contains("="))
        assertFalse(bundle.privateKey.contains("="))
    }

    @Test
    fun consecutiveGenerationsProduceUniqueKeypairs() {
        val generated = (1..10).map { VapidKeyGenerator.generate() }
        assertEquals(10, generated.map { it.publicKey }.toSet().size)
        assertEquals(10, generated.map { it.privateKey }.toSet().size)
    }

    @Test
    fun exportedEnvAndDbConfigContainGeneratedKeys() {
        val bundle = VapidKeyGenerator.generate()
        val env = bundle.toSupabaseSecretsEnv()
        assertTrue(env.contains("VAPID_PUBLIC_KEY=${bundle.publicKey}"))
        assertTrue(env.contains("VAPID_PRIVATE_KEY=${bundle.privateKey}"))
        assertTrue(env.contains("VAPID_SUBJECT=${bundle.subject}"))

        val dbConfig = bundle.toWebDbConfigJs(
            supabaseUrl = "https://abcxyz.supabase.co",
            supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.testkey"
        )
        assertTrue(dbConfig.contains("url: \"https://abcxyz.supabase.co\""))
        assertTrue(dbConfig.contains("vapidPublicKey: \"${bundle.publicKey}\""))
    }
}
