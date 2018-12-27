package network.path.mobilenode.library.utils

import okhttp3.Response
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

fun Socket.readText(maxSize: Int): String {
    ByteArrayOutputStream(maxSize).use {
        getInputStream().copyTo(it)
        return String(it.toByteArray())
    }
}

fun Socket.writeText(payload: String) {
    this.getOutputStream().bufferedWriter().apply {
        write(payload)
        flush()
    }
}

fun Response.getBody(): ResponseBody {
    val body = body()
    if (!isSuccessful) {
        throw IOException("Unsuccessful response code: ${code()}, body: $body")
    }
    if (body == null) {
        throw IOException("Response body is null")
    }
    return body
}

fun isPortInUse(port: Int) = try {
    ServerSocket(port).close()
    false
} catch (e: IOException) {
    true
}
