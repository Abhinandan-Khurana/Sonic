package app.runout.account

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * BIP-340 Schnorr signatures over secp256k1, the signature scheme Nostr uses. Plain Kotlin and
 * java.math, no native code. Checked against the official BIP-340 test vectors.
 */
object Schnorr {
    private val P = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
    val N: BigInteger = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    private val GX = BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16)
    private val GY = BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)
    private val TWO = BigInteger.valueOf(2)
    private val THREE = BigInteger.valueOf(3)
    private val FOUR = BigInteger.valueOf(4)
    private val SEVEN = BigInteger.valueOf(7)
    private val EIGHT = BigInteger.valueOf(8)
    private val SQRT_EXP = P.add(BigInteger.ONE).divide(FOUR)

    data class Point(val x: BigInteger, val y: BigInteger)

    private val G = Point(GX, GY)

    // Jacobian coordinates: (X, Y, Z) stands for (X/Z², Y/Z³). Z = 0 is the point at infinity.
    private class J(val x: BigInteger, val y: BigInteger, val z: BigInteger) {
        val infinite: Boolean get() = z.signum() == 0
    }

    private val INF = J(BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

    private fun dbl(p: J): J {
        if (p.infinite || p.y.signum() == 0) return INF
        val ysq = p.y.multiply(p.y).mod(P)
        val s = FOUR.multiply(p.x).multiply(ysq).mod(P)
        val m = THREE.multiply(p.x).multiply(p.x).mod(P)
        val nx = m.multiply(m).subtract(TWO.multiply(s)).mod(P)
        val ny = m.multiply(s.subtract(nx)).subtract(EIGHT.multiply(ysq).multiply(ysq)).mod(P)
        val nz = TWO.multiply(p.y).multiply(p.z).mod(P)
        return J(nx, ny, nz)
    }

    private fun add(p: J, q: J): J {
        if (p.infinite) return q
        if (q.infinite) return p
        val z1z1 = p.z.multiply(p.z).mod(P)
        val z2z2 = q.z.multiply(q.z).mod(P)
        val u1 = p.x.multiply(z2z2).mod(P)
        val u2 = q.x.multiply(z1z1).mod(P)
        val s1 = p.y.multiply(q.z).multiply(z2z2).mod(P)
        val s2 = q.y.multiply(p.z).multiply(z1z1).mod(P)
        if (u1 == u2) return if (s1 == s2) dbl(p) else INF
        val h = u2.subtract(u1).mod(P)
        val r = s2.subtract(s1).mod(P)
        val h2 = h.multiply(h).mod(P)
        val h3 = h.multiply(h2).mod(P)
        val u1h2 = u1.multiply(h2).mod(P)
        val nx = r.multiply(r).subtract(h3).subtract(TWO.multiply(u1h2)).mod(P)
        val ny = r.multiply(u1h2.subtract(nx)).subtract(s1.multiply(h3)).mod(P)
        val nz = h.multiply(p.z).multiply(q.z).mod(P)
        return J(nx, ny, nz)
    }

    private fun jac(p: Point) = J(p.x, p.y, BigInteger.ONE)

    private fun affine(p: J): Point? {
        if (p.infinite) return null
        val zi = p.z.modInverse(P)
        val zi2 = zi.multiply(zi).mod(P)
        return Point(p.x.multiply(zi2).mod(P), p.y.multiply(zi2).multiply(zi).mod(P))
    }

    private fun mulJ(k: BigInteger, p: Point): J {
        var r = INF
        val base = jac(p)
        for (i in k.bitLength() - 1 downTo 0) {
            r = dbl(r)
            if (k.testBit(i)) r = add(r, base)
        }
        return r
    }

    fun mul(k: BigInteger, p: Point = G): Point? = affine(mulJ(k, p))

    private fun liftX(x: BigInteger): Point? {
        if (x >= P) return null
        val c = x.modPow(THREE, P).add(SEVEN).mod(P)
        val y = c.modPow(SQRT_EXP, P)
        if (y.modPow(TWO, P) != c) return null
        return Point(x, if (y.testBit(0)) P.subtract(y) else y)
    }

    private fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        parts.forEach { md.update(it) }
        return md.digest()
    }

    private fun taggedHash(tag: String, vararg parts: ByteArray): ByteArray {
        val t = sha256(tag.toByteArray())
        return sha256(t, t, *parts)
    }

    fun bytes32(x: BigInteger): ByteArray {
        val b = x.toByteArray()
        return when {
            b.size == 32 -> b
            b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
            else -> ByteArray(32 - b.size) + b
        }
    }

    private fun int(b: ByteArray) = BigInteger(1, b)

    /** The x-only public key for a 32-byte secret key. */
    fun publicKey(secret: ByteArray): ByteArray {
        val d = int(secret)
        require(d.signum() > 0 && d < N) { "secret key out of range" }
        return bytes32(mul(d)!!.x)
    }

    /** A fresh random secret key. */
    fun newSecretKey(random: SecureRandom = SecureRandom()): ByteArray {
        while (true) {
            val b = ByteArray(32).also(random::nextBytes)
            val d = int(b)
            if (d.signum() > 0 && d < N) return b
        }
    }

    fun sign(message: ByteArray, secret: ByteArray, auxRand: ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)): ByteArray {
        val d0 = int(secret)
        require(d0.signum() > 0 && d0 < N) { "secret key out of range" }
        val pt = mul(d0)!!
        val d = if (pt.y.testBit(0)) N.subtract(d0) else d0
        val t = bytes32(d).let { db -> val h = taggedHash("BIP0340/aux", auxRand); ByteArray(32) { (db[it].toInt() xor h[it].toInt()).toByte() } }
        val px = bytes32(pt.x)
        val k0 = int(taggedHash("BIP0340/nonce", t, px, message)).mod(N)
        require(k0.signum() != 0) { "bad nonce" }
        val r = mul(k0)!!
        val k = if (r.y.testBit(0)) N.subtract(k0) else k0
        val rx = bytes32(r.x)
        val e = int(taggedHash("BIP0340/challenge", rx, px, message)).mod(N)
        val sig = rx + bytes32(k.add(e.multiply(d)).mod(N))
        check(verify(message, px, sig)) { "signature failed to verify" }
        return sig
    }

    fun verify(message: ByteArray, publicKey: ByteArray, signature: ByteArray): Boolean = runCatching {
        if (publicKey.size != 32 || signature.size != 64) return false
        val pt = liftX(int(publicKey)) ?: return false
        val r = int(signature.copyOfRange(0, 32))
        val s = int(signature.copyOfRange(32, 64))
        if (r >= P || s >= N) return false
        val e = int(taggedHash("BIP0340/challenge", signature.copyOfRange(0, 32), publicKey, message)).mod(N)
        val sum = add(mulJ(s, G), mulJ(N.subtract(e).mod(N), pt))
        val rr = affine(sum) ?: return false
        !rr.y.testBit(0) && rr.x == r
    }.getOrDefault(false)
}

fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

fun String.unhex(): ByteArray {
    require(length % 2 == 0) { "odd hex length" }
    return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

/** Bech32 (BIP-173), used by Nostr for npub/nsec keys people can copy and paste. */
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GEN = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0 until 5) if ((top ushr i) and 1 == 1) chk = chk xor GEN[i]
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray =
        IntArray(hrp.length) { hrp[it].code ushr 5 } + intArrayOf(0) + IntArray(hrp.length) { hrp[it].code and 31 }

    private fun convert(data: ByteArray, from: Int, to: Int, pad: Boolean): ByteArray? {
        var acc = 0
        var bits = 0
        val out = ArrayList<Byte>()
        val maxv = (1 shl to) - 1
        for (b in data) {
            val v = b.toInt() and 0xff
            if (v ushr from != 0) return null
            acc = (acc shl from) or v
            bits += from
            while (bits >= to) {
                bits -= to
                out += ((acc ushr bits) and maxv).toByte()
            }
        }
        if (pad) {
            if (bits > 0) out += ((acc shl (to - bits)) and maxv).toByte()
        } else if (bits >= from || ((acc shl (to - bits)) and maxv) != 0) {
            return null
        }
        return out.toByteArray()
    }

    fun encode(hrp: String, data: ByteArray): String {
        val five = convert(data, 8, 5, true)!!.map { it.toInt() }.toIntArray()
        val values = hrpExpand(hrp) + five + IntArray(6)
        val mod = polymod(values) xor 1
        val checksum = IntArray(6) { (mod ushr (5 * (5 - it))) and 31 }
        return hrp + "1" + (five + checksum).joinToString("") { CHARSET[it].toString() }
    }

    /** Returns (hrp, bytes), or null when the string isn't valid bech32. */
    fun decode(text: String): Pair<String, ByteArray>? {
        val s = text.trim().lowercase()
        val pos = s.lastIndexOf('1')
        if (pos < 1 || pos + 7 > s.length) return null
        val hrp = s.substring(0, pos)
        val data = IntArray(s.length - pos - 1)
        for (i in data.indices) {
            val v = CHARSET.indexOf(s[pos + 1 + i])
            if (v < 0) return null
            data[i] = v
        }
        if (polymod(hrpExpand(hrp) + data) != 1) return null
        val bytes = convert(ByteArray(data.size - 6) { data[it].toByte() }, 5, 8, false) ?: return null
        return hrp to bytes
    }
}
