package network.path.mobilenode.library.utils

import android.content.SharedPreferences
import timber.log.Timber
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("REIFIED_TYPE_PARAMETER_NO_INLINE")
internal fun <reified T : Any> Set<*>.isSetOf(): Boolean =
    T::class.java.isAssignableFrom(this::class.java.componentType)

internal fun <T : Any> prefs(prefs: SharedPreferences, name: String, defaultValue: T): SharedPreferencesDelegate<T> =
    SharedPreferencesDelegate(prefs, name, defaultValue)

internal fun <T : Any> prefsOptional(
    prefs: SharedPreferences,
    name: String,
    clazz: Class<T>,
    ttl: Long = -1L
): SharedPreferencesOptionalDelegate<T> =
    SharedPreferencesOptionalDelegate(prefs, name, clazz, ttl)

internal class SharedPreferencesDelegate<T : Any>(
    private val prefs: SharedPreferences,
    private val name: String,
    private val defaultValue: T
) : ReadWriteProperty<Any, T> {

    @Suppress("UNCHECKED_CAST")
    override operator fun getValue(thisRef: Any, property: KProperty<*>): T = if (!prefs.contains(name)) {
        Timber.v("Shared preference [$name] is missing. Returning default [$defaultValue]")
        defaultValue
    } else {
        val value = when (defaultValue) {
            is Boolean -> prefs.getBoolean(name, defaultValue) as T
            is Int -> prefs.getInt(name, defaultValue) as T
            is Long -> prefs.getLong(name, defaultValue) as T
            is Float -> prefs.getFloat(name, defaultValue) as T
            is String -> prefs.getString(name, defaultValue) as T
            is Set<*> -> when {
                defaultValue.isSetOf<String>() -> prefs.getStringSet(name, defaultValue as Set<String>) as T
                else -> throw RuntimeException("Bundle value $defaultValue has wrong type")
            }
            else -> throw RuntimeException("Bundle value $defaultValue has wrong type")
        }
        Timber.v("Found shared preference [$name] = [$value]")
        value
    }

    @Suppress("UNCHECKED_CAST")
    override operator fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        Timber.v("Saving new shared preference [$name] = [$value]")
        val editor = prefs.edit()
        when (value) {
            is Boolean -> editor.putBoolean(name, value)
            is Int -> editor.putInt(name, value)
            is Long -> editor.putLong(name, value)
            is Float -> editor.putFloat(name, value)
            is String -> editor.putString(name, value)
            is Set<*> -> when {
                value.isSetOf<String>() -> editor.putStringSet(name, value as Set<String>)
                else -> throw RuntimeException("Bundle value $value has wrong type")
            }
            else -> throw RuntimeException("Bundle value $value has wrong type")
        }
        editor.apply()
    }
}

internal class SharedPreferencesOptionalDelegate<T : Any>(
    private val prefs: SharedPreferences,
    private val name: String,
    private val clazz: Class<T>,
    private val ttl: Long = -1L
) : ReadWriteProperty<Any, T?> {

    @Suppress("UNCHECKED_CAST")
    override operator fun getValue(thisRef: Any, property: KProperty<*>): T? = if (!prefs.contains(name)) {
        Timber.v("Shared preference [$name] is missing.")
        null
    } else {
        val saveTime = prefs.getLong(name + "_TIME", -1L)
        val curTime = System.currentTimeMillis()
        if (ttl != -1L && (saveTime < 0 || (curTime - saveTime > ttl))) {
            Timber.v("Shared preference [$name] is too old ($curTime, $saveTime, $ttl)")
            null
        } else {
            val value = when (clazz) {
                Boolean::class.java -> prefs.getBoolean(name, false) as T
                Int::class.java -> prefs.getInt(name, 0) as T
                Long::class.java -> prefs.getLong(name, 0L) as T
                Float::class.java -> prefs.getFloat(name, 0.0f) as T
                String::class.java -> prefs.getString(name, "") as T
                else -> throw RuntimeException("Class $clazz is not supported")
            }
            Timber.v("Found shared preference [$name] = [$value]")
            value
        }
    }

    @Suppress("UNCHECKED_CAST")
    override operator fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
        Timber.v("Saving new shared preference [$name] = [$value]")
        val editor = prefs.edit()
        when (value) {
            null -> editor.remove(name)
            is Boolean -> editor.putBoolean(name, value)
            is Int -> editor.putInt(name, value)
            is Long -> editor.putLong(name, value)
            is Float -> editor.putFloat(name, value)
            is String -> editor.putString(name, value)
            is Set<*> -> when {
                value.isSetOf<String>() -> editor.putStringSet(name, value as Set<String>)
                else -> throw RuntimeException("Bundle value $value has wrong type")
            }
            else -> throw RuntimeException("Bundle value $value has wrong type")
        }
        if (ttl != -1L) {
            editor.putLong(name + "_TIME", System.currentTimeMillis())
        }
        editor.apply()
    }
}
