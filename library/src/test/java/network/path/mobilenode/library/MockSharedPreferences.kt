package network.path.mobilenode.library

import android.content.SharedPreferences

class MockSharedPreferences : SharedPreferences {
    private val booleans = mutableMapOf<String, Boolean?>()
    private val ints = mutableMapOf<String, Int?>()
    private val longs = mutableMapOf<String, Long?>()
    private val floats = mutableMapOf<String, Float?>()
    private val stringSets = mutableMapOf<String, MutableSet<String>?>()
    private val strings = mutableMapOf<String, String?>()

    override fun contains(key: String?) =
        booleans.keys.contains(key) ||
                ints.keys.contains(key) ||
                longs.keys.contains(key) ||
                floats.keys.contains(key) ||
                stringSets.keys.contains(key) ||
                strings.keys.contains(key)

    override fun getBoolean(key: String?, defaultValue: Boolean) = booleans[key] ?: defaultValue

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // TODO: If necessary
    }

    override fun getInt(key: String?, defaultValue: Int) = ints[key] ?: defaultValue

    override fun getAll(): MutableMap<String, *> =
        mutableMapOf<String, Any?>().apply {
            putAll(booleans)
            putAll(ints)
            putAll(longs)
            putAll(floats)
            putAll(stringSets)
            putAll(strings)
        }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun getLong(key: String?, defaultValue: Long) = longs[key] ?: defaultValue

    override fun getFloat(key: String?, defaultValue: Float) = floats[key] ?: defaultValue

    override fun getStringSet(key: String?, defaultValue: MutableSet<String>?) =
        stringSets[key] ?: defaultValue

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // TODO: If necessary
    }

    override fun getString(key: String?, defaultValue: String?) = strings[key] ?: defaultValue

    private inner class Editor : SharedPreferences.Editor {
        override fun clear(): SharedPreferences.Editor {
            booleans.clear()
            ints.clear()
            longs.clear()
            floats.clear()
            stringSets.clear()
            strings.clear()
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            remove(key)
            longs[key] = value
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            remove(key)
            ints[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            booleans.remove(key)
            ints.remove(key)
            longs.remove(key)
            floats.remove(key)
            stringSets.remove(key)
            strings.remove(key)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            booleans[key] = value
            return this
        }

        override fun putStringSet(key: String, value: MutableSet<String>?): SharedPreferences.Editor {
            stringSets[key] = value
            return this
        }

        override fun commit(): Boolean {
            // TODO: If necessary
            return true
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            floats[key] = value
            return this
        }

        override fun apply() {
            // TODO: If necessary
        }

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            strings[key] = value
            return this
        }
    }
}
