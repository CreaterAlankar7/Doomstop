package com.doomstop.app

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Totp {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun newSecret(): String {
        val r = SecureRandom()
        val sb = StringBuilder()
        repeat(16) { sb.append(ALPHABET[r.nextInt(32)]) }
        return sb.toString()
    }

    private fun decode(s: String): ByteArray {
        var buffer = 0
        var bits = 0
        val out = ArrayList<Byte>()
        for (ch in s.uppercase()) {
            val v = ALPHABET.indexOf(ch)
            if (v < 0) continue
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    private fun code(secret: String, counter: Long): String {
        val data = ByteArray(8) { i -> ((counter shr (8 * (7 - i))) and 0xFF).toByte() }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(decode(secret), "HmacSHA1"))
        val h = mac.doFinal(data)
        val o = h[h.size - 1].toInt() and 0x0F
        val bin = ((h[o].toInt() and 0x7F) shl 24) or
            ((h[o + 1].toInt() and 0xFF) shl 16) or
            ((h[o + 2].toInt() and 0xFF) shl 8) or
            (h[o + 3].toInt() and 0xFF)
        return (bin % 1_000_000).toString().padStart(6, '0')
    }

    fun verify(secret: String, input: String): Boolean {
        val step = System.currentTimeMillis() / 1000 / 30
        for (d in -1..1) {
            if (code(secret, step + d) == input.trim()) return true
        }
        return false
    }
}