package com.github.burkov.nginx.gzip_streamer

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.ChannelFutureListener.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.netty.handler.codec.http.HttpVersion.*
import io.netty.handler.stream.ChunkedStream
import io.netty.handler.stream.ChunkedWriteHandler
import java.io.BufferedInputStream
import java.io.FileInputStream


class DevRandomServerHandler() : SimpleChannelInboundHandler<FullHttpRequest>() {
    private val chunkSize = 4096
    override fun channelRead0(ctx: ChannelHandlerContext?, msg: FullHttpRequest?) {
        if (msg == null || ctx == null) throw IllegalArgumentException("ctx and msg required")
        if (!msg.decoderResult.isSuccess) {
            ctx.writeAndFlush(DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST)).addListener(CLOSE)
            return
        }
        val resp = DefaultHttpResponse(HTTP_1_1, OK)
        resp.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/octet-stream")
        resp.headers().set(HttpHeaders.Names.TRANSFER_ENCODING, "chunked")
        ctx.write(resp)
        ctx.write(ChunkedStream(BufferedInputStream(FileInputStream("/dev/urandom")), chunkSize))
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(CLOSE)
    }
}

class DevRandomServer() {
    private val channelInitializer = object : ChannelInitializer<SocketChannel>() {
        override fun initChannel(ch: SocketChannel?) {
            ch!!.pipeline().run {
                addLast(HttpRequestDecoder())
                addLast(HttpObjectAggregator(65536))
                addLast(HttpResponseEncoder())
                addLast(ChunkedWriteHandler())
                addLast(DevRandomServerHandler())
            }
        }
    }

    fun run(port: Int) {
        val parentGroup = NioEventLoopGroup()
        val workerGroup = NioEventLoopGroup()
        try {
            ServerBootstrap().run {
                group(parentGroup, workerGroup)
                channel(NioServerSocketChannel::class.java)
                childHandler(channelInitializer)
                option(ChannelOption.SO_BACKLOG, 128)
                childOption(ChannelOption.SO_KEEPALIVE, true)
                bind(port).sync().channel().closeFuture().sync()
            }
        } finally {
            workerGroup.shutdownGracefully()
            parentGroup.shutdownGracefully()
        }
    }
}
