package com.github.burkov.nginx.gzip_streamer

import java.io.*
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private fun checkOutput(command: String, errorMessage: String): List<String> {
    val process = Runtime.getRuntime().exec(command)
    if (process.waitFor() != 0) throw Exception(errorMessage)
    return InputStreamReader(process.inputStream).readLines()
}

private fun startNginx(nginxPath: String, nginxConfig: String): List<Int> {
    fun pollFile(path: String, pollInterval: Long = 300, maxAttempts: Int = 10): File {
        require(maxAttempts > 0 || pollInterval > 0)
        val file = File(path)
        var attempts = 0
        while (attempts++ < maxAttempts) {
            if (file.exists()) return file
            Thread.sleep(pollInterval)
        }
        throw Exception("polling of $path failed ($maxAttempts attempts every $pollInterval ms)")
    }

    fun getWorkersPids(parentPid: Int): List<Int> {
        return checkOutput("pgrep -P $parentPid", "failed to fetch children pids of $parentPid")
                .map { it.trim().toInt() }
    }

    try {
        val process = Runtime.getRuntime().exec("$nginxPath -p . -c $nginxConfig")
        val pid = FileReader(pollFile("nginx.pid")).readText().trim().toInt()
        val workersPids = getWorkersPids(pid)
        Thread {
            process.waitFor()
            // unreachable
            listOf(process.inputStream to "nginx.out.log", process.errorStream to "nginx.err.log").forEach {
                Files.copy(it.first, FileSystems.getDefault().getPath(it.second), StandardCopyOption.REPLACE_EXISTING)
            }
            println("nginx stopped unexpectedly, see nginx.{out|err}.log")
            System.exit(1)
        }.start()
        return workersPids
    } catch (e: Exception) {
        println("failed to start nginx")
        throw e
    }
}

class StatPrinter(val workersPids: List<Int>, val clientFactory: DevRandomClientFactory) {
    private var lastRss = 0L

    private fun getRssBytes(pids: List<Int>): Long {
        val pidsList = pids.joinToString(",") { it.toString() }
        return checkOutput("ps -p $pidsList -o rss", "failed to get rss of $pidsList").drop(1)
                .map { it.trim().toInt() }.sum().toLong()
    }

    fun print() {
        val currentRss = getRssBytes(workersPids)
        val rssDiff = currentRss - lastRss
        if (lastRss != 0L && rssDiff != 0L) println(" (${if (rssDiff > 0) "+" else ""}${rssDiff.bytes()})")
        lastRss = currentRss
        val conns = clientFactory.connectedClients.sum()
        val total = clientFactory.totalBytesReceived.sum().bytes()
        print("\r$conns connections, rcvd: $total, rss: ${currentRss.bytes()}")
    }
}

fun main(args: Array<String>) {
    val nginxConfig = (args.firstOrNull() ?: "nginx.conf")
    val nginxPath = (args.drop(1).firstOrNull() ?: "/usr/local/bin/nginx")
    val numberOfClients = (args.drop(2).firstOrNull() ?: "1").toInt()

    val pollInterval = 3000L
    val serverPort = 8081
    val nginxRelayPort = 8080
    val clientFactory = DevRandomClientFactory()

    val workersPids = startNginx(nginxPath, nginxConfig)
    Thread { DevRandomServer().run(serverPort) }.start()
    clientFactory.attachClients(numberOfClients, nginxRelayPort)
    val sp = StatPrinter(workersPids, clientFactory)

    try {
        while (true) {
            sp.print()
            Thread.sleep(pollInterval)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        System.exit(1)
    }

}