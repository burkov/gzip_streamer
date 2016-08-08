package com.github.burkov.nginx.gzip_streamer

import org.asynchttpclient.*
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.AsyncHandler.State.*
import java.util.concurrent.atomic.LongAdder

class DevRandomClientFactory() {
    val connectedClients = LongAdder()
    val totalBytesReceived = LongAdder()

    val client = DefaultAsyncHttpClient(
            DefaultAsyncHttpClientConfig.Builder().setRequestTimeout(-1).setReadTimeout(-1).build())

    fun attachClients(n: Int, port: Int) {
        for (i in 0 until n) {
            client.prepareGet("http://localhost:$port/stream").addHeader("Accept-Encoding", "gzip")
                    .execute(StreamHandler(totalBytesReceived, connectedClients))
        }
    }
}

class StreamHandler(val totalRcvd: LongAdder, val totalConns: LongAdder) : AsyncHandler<Unit> {
    override fun onBodyPartReceived(bodyPart: HttpResponseBodyPart?): State {
        totalRcvd.add(bodyPart!!.length().toLong())
        return CONTINUE
    }

    override fun onStatusReceived(responseStatus: HttpResponseStatus?): State {
        return if (responseStatus!!.statusCode != 200) {
            println("Error: status code = ${responseStatus.statusCode}")
            ABORT
        } else {
            totalConns.increment()
            CONTINUE
        }
    }

    override fun onHeadersReceived(headers: HttpResponseHeaders?): State = CONTINUE
    override fun onCompleted(): Unit = totalConns.decrement()
    override fun onThrowable(t: Throwable?) = t!!.printStackTrace()
}