package com.kumhoht.todaysost.engine.composer

/**
 * Python `random.Random(seed)` 과 같은 난수열을 내는 메르센 트위스터(MT19937).
 * 원본 작곡 엔진이 random.Random(3), random.Random(21) 로 연주 흔들림과 멜로디를 정하므로,
 * Python 과 똑같은 악보를 만들려면 난수도 비트 단위로 같아야 한다.
 * (CPython Modules/_randommodule.c, Lib/random.py 기준)
 */
class PyRandom(seed: Long) {
    private val mt = IntArray(N)
    private var mti = N + 1

    init {
        // random.seed(int): 절댓값을 32비트 단위로 쪼갠 배열로 init_by_array
        var a = kotlin.math.abs(seed)
        val key = mutableListOf<Int>()
        do { key += (a and 0xFFFFFFFFL).toInt(); a = a ushr 32 } while (a != 0L)
        initByArray(key.toIntArray())
    }

    private fun initGenrand(s: Int) {
        mt[0] = s
        for (i in 1 until N) mt[i] = 1812433253 * (mt[i - 1] xor (mt[i - 1] ushr 30)) + i
        mti = N
    }

    private fun initByArray(key: IntArray) {
        initGenrand(19650218)
        var i = 1
        var j = 0
        var k = maxOf(N, key.size)
        while (k > 0) {
            mt[i] = (mt[i] xor ((mt[i - 1] xor (mt[i - 1] ushr 30)) * 1664525)) + key[j] + j
            i++; j++
            if (i >= N) { mt[0] = mt[N - 1]; i = 1 }
            if (j >= key.size) j = 0
            k--
        }
        k = N - 1
        while (k > 0) {
            mt[i] = (mt[i] xor ((mt[i - 1] xor (mt[i - 1] ushr 30)) * 1566083941)) - i
            i++
            if (i >= N) { mt[0] = mt[N - 1]; i = 1 }
            k--
        }
        mt[0] = 0x80000000.toInt()
    }

    private fun nextU32(): Int {
        if (mti >= N) {
            for (kk in 0 until N) {
                val y = (mt[kk] and UPPER) or (mt[(kk + 1) % N] and LOWER)
                mt[kk] = mt[(kk + M) % N] xor (y ushr 1) xor (if (y and 1 != 0) MATRIX_A else 0)
            }
            mti = 0
        }
        var y = mt[mti++]
        y = y xor (y ushr 11)
        y = y xor ((y shl 7) and 0x9d2c5680.toInt())
        y = y xor ((y shl 15) and 0xefc60000.toInt())
        y = y xor (y ushr 18)
        return y
    }

    /** random.random(): [0, 1) 53비트 */
    fun random(): Double {
        val a = (nextU32() ushr 5).toLong()
        val b = (nextU32() ushr 6).toLong()
        return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0)
    }

    /** random.uniform(a, b) = a + (b - a) * random() */
    fun uniform(a: Double, b: Double): Double = a + (b - a) * random()

    private fun getrandbits(k: Int): Int = nextU32() ushr (32 - k)

    /** random._randbelow(n) (getrandbits 방식) */
    private fun randbelow(n: Int): Int {
        val k = 32 - Integer.numberOfLeadingZeros(n) // n.bit_length()
        var r = getrandbits(k)
        while (r >= n) r = getrandbits(k)
        return r
    }

    /** random.choice(seq) */
    fun <T> choice(seq: List<T>): T = seq[randbelow(seq.size)]

    private companion object {
        const val N = 624
        const val M = 397
        const val MATRIX_A = 0x9908b0df.toInt()
        const val UPPER = 0x80000000.toInt()
        const val LOWER = 0x7fffffff
    }
}
