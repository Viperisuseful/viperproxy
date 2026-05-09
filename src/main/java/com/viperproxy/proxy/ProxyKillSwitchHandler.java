package com.viperproxy.proxy;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.proxy.ProxyConnectException;
import io.netty.handler.proxy.ProxyConnectionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProxyKillSwitchHandler extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("ViperProxy");

    private final ProxyRuntime runtime;

    public ProxyKillSwitchHandler(ProxyRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof ProxyConnectionEvent) {
            LOGGER.info("Proxy tunnel established: remoteAddress={}", context.channel().remoteAddress());
            this.runtime.markProxyTunnelEstablished();
        }

        super.userEventTriggered(context, event);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
        if (this.runtime.isProxyRequired() && isProxyFailure(cause)) {
            FailureReason failureReason = FailureReason.PROXY_DROPPED;
            String detail = "Proxy tunnel failed: " + safeMessage(cause);

            LOGGER.error(
                "Proxy kill switch fired: reason={}, remoteAddress={}, detail={}",
                failureReason.code(),
                context.channel().remoteAddress(),
                detail,
                cause
            );

            this.runtime.markRuntimeError(failureReason, detail);
            context.close();
            return;
        }

        super.exceptionCaught(context, cause);
    }

    private static boolean isProxyFailure(Throwable cause) {
        if (cause instanceof ProxyConnectException) {
            return true;
        }

        String className = cause.getClass().getName();
        return className.startsWith("io.netty.handler.proxy")
            || className.contains("Proxy")
            || safeMessage(cause).toLowerCase().contains("proxy");
    }

    private static String safeMessage(Throwable cause) {
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
