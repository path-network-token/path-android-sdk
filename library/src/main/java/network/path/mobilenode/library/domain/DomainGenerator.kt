package network.path.mobilenode.library.domain

import com.instacart.library.truetime.TrueTimeRx
import timber.log.Timber
import java.net.InetAddress
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors

object DomainGenerator {
    private const val CHECK_MAX_DAYS = 10
    private val SEED = listOf(
            intArrayOf(8, 11, 17, 4, 25, 16, 13, 19, 12, 7, 14, 47)
    )

    private fun generateDomains(): Set<String> {
        val date = try {
            TrueTimeRx.now()
        } catch (e: Exception) {
            Timber.w("TRUE TIME: now() failed: $e")
            Date()
        }
        val cal = Calendar.getInstance()
        cal.timeZone = TimeZone.getTimeZone("UTC")
        cal.time = date

        return SEED.fold(mutableSetOf()) { set, seed ->
            (0 until CHECK_MAX_DAYS).fold(set) { innerSet, _ ->
                val newSet = generate(seed, cal)
                innerSet.addAll(newSet)
                cal.add(Calendar.DAY_OF_YEAR, -1)
                innerSet
            }
            set
        }
    }

    private fun generate(seed: IntArray, cal: Calendar) = (1..24).map {
        var year = cal.get(Calendar.YEAR).toBigInteger()
        var month = (cal.get(Calendar.MONTH) + 1).toBigInteger()
        var day = cal.get(Calendar.DAY_OF_MONTH).toBigInteger()
        var hour = it.toBigInteger()
        val domain = StringBuffer()
        for (i in 1..16) {
            year = ((year xor seed[0].toBigInteger() * year) shr seed[1]) xor (year shl seed[2])
            month = ((month xor seed[3].toBigInteger() * month) shr seed[4]) xor (seed[5].toBigInteger() * month)
            day = ((day xor (day shl seed[6])) shr seed[7]) xor (day shl seed[8])
            hour = ((hour xor seed[9].toBigInteger() * hour) shr seed[10]) xor (seed[11].toBigInteger() * hour)
            val v = (year xor month xor day xor hour) % 25.toBigInteger()
            val char = (v.toInt() + 97).toChar()
            domain.append(char)
        }
        domain.append(".net")
        domain.toString()
    }.toSet()

    fun findDomain(storage: PathStorage): String? {
        val saved = storage.proxyDomain
        if (saved != null) {
            return saved
        }

        val domains = generateDomains()
//        Timber.d("DOMAIN: potential domains [${domains.joinToString(separator = "\n")}]")
        Timber.d("DOMAIN: potential domains count [${domains.size}]")
        val executor = Executors.newCachedThreadPool()
        val result = executor.invokeAll(domains.map {
            Callable { resolve(it) }
        })
        val resolved = result.mapNotNull { it.get() }.firstOrNull()

        Timber.d("DOMAIN: resolved domains [$resolved]")
        if (resolved != null) {
            storage.proxyDomain = resolved
        }
        return resolved
    }

    private fun resolve(domain: String): String? = try {
        InetAddress.getAllByName(domain)
        domain
    } catch (e: Exception) {
        Timber.v("DOMAIN: cannot resolve host [$domain]: $e")
        null
    }
}
