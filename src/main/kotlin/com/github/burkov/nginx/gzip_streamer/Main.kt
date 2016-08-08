package com.github.burkov.nginx.gzip_streamer

import java.io.File
import java.io.FileReader
import java.io.InputStreamReader

private fun Long.bytes(signed: Boolean = false): String {
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
            4 to "TB"
    )
    return if(signed)
        if (p == 0) "%+7d  b".format(this) else "${"%+7.2f".format(r * sign)} ${map[p]}"
    else
        if (p == 0) "%7d  b".format(this) else "${"%7.2f".format(r * sign)} ${map[p]}"

}

private fun checkOutput(command: String, errorMessage: String): List<String> {
    val process = Runtime.getRuntime().exec(command)
    if (process.waitFor() != 0) throw Exception(errorMessage)
    return InputStreamReader(process.inputStream).readLines()
}

private fun startNginx(nginxPath: String, nginxConfig: String): Pair<Process, List<Int>> {
    fun pollFile(path: String, pollInterval: Long = 300, maxAttempts: Int = 10): File {
        require(maxAttempts > 0 || pollInterval > 0)
        val file = File(path)
        var attempts = 0
        while (attempts++ < maxAttempts) {
            if (file.exists()) return file
            Thread.sleep(pollInterval)
        }
        throw Exception("polling of '$path' failed ($maxAttempts attempts every $pollInterval ms)")
    }

    fun getWorkersPids(parentPid: Int): List<Int> {
        return checkOutput("pgrep -P $parentPid", "failed to fetch children pids of $parentPid")
                .map { it.trim().toInt() }
    }

    val command = "$nginxPath -p . -c $nginxConfig"
    val pb = ProcessBuilder(command.split(" "))
    pb.redirectErrorStream(true)
    pb.redirectOutput(ProcessBuilder.Redirect.to(File("nginx.out.log")))
    try {
        val process = pb.start()
        val pid = FileReader(pollFile("nginx.pid")).readText().trim().toInt()
        val workers = getWorkersPids(pid)
        println("nginx started, pid: $pid, workers: $workers (output redirected to 'nginx.out.log', pidfile: 'nginx.pid')")
        return process to workers
    } catch (e: Exception) {
        println("failed to start nginx with `$command`: ${e.message}")
        System.exit(1)
        throw e // unreachable
    }
}

class StatPrinter(val workersPids: List<Int>) {
    private var lastRss = 0L

    private fun getRssBytes(pids: List<Int>): Long {
        val pidsList = pids.joinToString(",") { it.toString() }
        return checkOutput("ps -p $pidsList -o rss", "failed to get rss of $pidsList").drop(1)
                .map { it.trim().toInt() }.sum().toLong()
    }

    fun print() {
        val currentRss = getRssBytes(workersPids)
        val rssDiff = currentRss - lastRss
        if (lastRss != 0L && rssDiff != 0L) println(" ${rssDiff.bytes(true)}")
        lastRss = currentRss
        print("\rtotal rss: ${currentRss.bytes()}")
    }
}

private fun startServer(server: DevRandomServer): Thread {
    val cf = server.waitBind()
    return Thread {
        try {
            cf.channel().closeFuture().sync()
        } finally {
            println("stream server exited")
            server.shutdown()
        }
    }.apply { start() }
}

fun main(args: Array<String>) {
    println("usage: gzip_streamer.jar [nginx] [config]")
    val nginxPath = (args.firstOrNull() ?: "/usr/sbin/nginx")
    val configPath = (args.drop(1).firstOrNull() ?: "nginx.conf")
    val pollInterval = 3000L

    println("nginx executable  = $nginxPath")
    println("nginx config      = $configPath")
    println("rss poll interval = $pollInterval ms\n")

    val serverWatchdog = startServer(DevRandomServer())

    val (process, workers) = startNginx(nginxPath, configPath)
    Runtime.getRuntime().addShutdownHook(Thread {
        process.destroy()
        process.waitFor()
        process.destroyForcibly() // make sure nginx is stopped
    })
    val sp = StatPrinter(workers)

    println("now you should start client (e.g `curl -H 'Accept-Encoding: gzip' -v http://localhost:8080/stream 1>/dev/null`)")

    try {
        while (process.isAlive && serverWatchdog.isAlive) {
            sp.print()
            Thread.sleep(pollInterval)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        System.exit(1) // stop other threads (if any)
    }
}