package com.github.burkov.nginx.gzip_streamer

import java.io.BufferedReader
import java.io.InputStreamReader


private var start: Pair<Long, Long> = 0L to System.currentTimeMillis()
private var delayStartSecs = 5
fun avg(value: Long): Long {
    if (delayStartSecs > 0) {
        delayStartSecs--
        return 0
    } else {
        if (start.first == 0L) start = value to start.second
        return Math.round((value - start.first) * 1000 / (System.currentTimeMillis() - start.second).toDouble())
    }
}


val user = if (System.getProperty("os.name") == "Mac OS X") "abu" else "www-data"

fun getRssBytes(name: String): Long {
    val pr = ProcessBuilder("ps", "-o", "rss", "-u", name)
    val stream = BufferedReader(InputStreamReader(pr.start().apply { waitFor() }.inputStream))
    return try {
        stream.readLines().drop(1).map { it.trim().toLong() }.sum() * 1000
    } finally {
        stream.close()
    }
}

fun elapsedSeconds(): Long = (System.currentTimeMillis() - start.second) / 1000

fun Long.bytes(): String {
    val sign = if (this < 0) -1 else 1
    var r = Math.abs(this.toDouble())
    var p = 0
    while (r >= 1000) {
        r /= 1000
        p++
    }
    val map = mapOf(
            1 to "kB",
            2 to "MB",
            3 to "GB",
            4 to "TB",
            5 to "PB",
            6 to "EB"
    )
    return if (p == 0) "%7d  b".format(this) else "${"%7.2f".format(r * sign)} ${map[p]}"
}