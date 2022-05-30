package org.sorapointa.utils

import io.ktor.utils.io.core.*
import io.ktor.utils.io.streams.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelOutboundInvoker
import kotlinx.coroutines.suspendCancellableCoroutine
import org.sorapointa.proto.SoraPacket
import org.sorapointa.proto.readToSoraPacket

internal suspend fun ChannelFuture.awaitKt(): ChannelFuture {
    suspendCancellableCoroutine<Unit> { cont ->
        cont.invokeOnCancellation {
            channel().close()
        }
        addListener { f ->
            if (f.isSuccess) {
                cont.resumeWith(Result.success(Unit))
            } else {
                cont.resumeWith(Result.failure(f.cause()))
            }
        }
    }
    return this
}

internal fun ByteBuf.toReadPacket(): ByteReadPacket {
    val buf = this
    return buildPacket {
        ByteBufInputStream(buf).withUse { copyTo(outputStream()) }
    }
}

internal fun ByteArray.toReadPacket(): ByteReadPacket =
    buildPacket {
        writeFully(this@toReadPacket)
    }

internal fun ByteBuf.toByteArray(): ByteArray {
    val bytes = ByteArray(this.readableBytes())
    this.readBytes(bytes)
    return bytes
}

@OptIn(SorapointaInternal::class)
internal fun ByteBuf.readToSoraPacket(
    key: ByteArray
): SoraPacket =
    toByteArray().xor(key).toReadPacket().readToSoraPacket()

internal fun ChannelOutboundInvoker.writeAndFlushOrCloseAsync(msg: Any?): ChannelFuture? {
    return writeAndFlush(msg)
        .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)
        .addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
}