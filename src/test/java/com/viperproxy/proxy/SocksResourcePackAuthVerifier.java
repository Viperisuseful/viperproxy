package com.viperproxy.proxy;

import com.viperproxy.config.ProxyConfig;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class SocksResourcePackAuthVerifier {
    private static final String USERNAME = "resource-pack-user";
    private static final String PASSWORD = "resource-pack-password";

    private SocksResourcePackAuthVerifier() {
    }

    public static void main(String[] args) throws Exception {
        ProxyConfig config = new ProxyConfig();
        config.enabled = true;
        config.host = "127.0.0.1";
        config.username = USERNAME;
        config.password = PASSWORD;
        config.type = ProxyType.SOCKS5;

        Authenticator previousAuthenticator = Authenticator.getDefault();
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();

        try (ServerSocket proxyServer = new ServerSocket(0)) {
            config.port = proxyServer.getLocalPort();
            Authenticator.setDefault(new ViperProxyAuthenticator(() -> config));

            Thread serverThread = Thread.ofPlatform().name("SocksAuthVerifier").start(() -> {
                try {
                    acceptAuthenticatedTunnel(proxyServer);
                } catch (Throwable failure) {
                    serverFailure.set(failure);
                }
            });

            try (Socket socket = new Socket(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(config.host, config.port)))) {
                socket.connect(InetSocketAddress.createUnresolved("resourcepacks.hypixel.net", 443), 3_000);
            }

            serverThread.join(3_000);
            require(!serverThread.isAlive(), "SOCKS5 verifier server did not finish");
            if (serverFailure.get() != null) {
                throw new IllegalStateException("SOCKS5 verifier server failed", serverFailure.get());
            }

            PasswordAuthentication leaked = Authenticator.requestPasswordAuthentication(
                "resourcepacks.hypixel.net",
                InetAddress.getLoopbackAddress(),
                443,
                "HTTPS",
                "origin authentication",
                null
            );
            require(leaked == null, "Proxy credentials were exposed to an origin server request");
        } finally {
            Authenticator.setDefault(previousAuthenticator);
        }

        System.out.println("Verified authenticated JDK SOCKS5 routing for Minecraft resource-pack downloads.");
    }

    private static void acceptAuthenticatedTunnel(ServerSocket proxyServer) throws Exception {
        try (
            Socket client = proxyServer.accept();
            DataInputStream input = new DataInputStream(client.getInputStream());
            DataOutputStream output = new DataOutputStream(client.getOutputStream())
        ) {
            require(input.readUnsignedByte() == 5, "Expected SOCKS5 greeting");
            int methodCount = input.readUnsignedByte();
            byte[] methods = input.readNBytes(methodCount);
            require(contains(methods, 2), "Client did not offer username/password authentication");
            output.write(new byte[] { 5, 2 });
            output.flush();

            require(input.readUnsignedByte() == 1, "Expected RFC 1929 authentication request");
            String username = readString(input);
            String password = readString(input);
            require(USERNAME.equals(username), "Wrong SOCKS5 username supplied by JDK");
            require(PASSWORD.equals(password), "Wrong SOCKS5 password supplied by JDK");
            output.write(new byte[] { 1, 0 });
            output.flush();

            require(input.readUnsignedByte() == 5, "Expected SOCKS5 connect request");
            require(input.readUnsignedByte() == 1, "Expected SOCKS5 CONNECT command");
            input.readUnsignedByte();
            consumeAddress(input, input.readUnsignedByte());
            input.readUnsignedShort();

            output.write(new byte[] { 5, 0, 0, 1, 127, 0, 0, 1, 0, 0 });
            output.flush();
        }
    }

    private static String readString(DataInputStream input) throws Exception {
        return new String(input.readNBytes(input.readUnsignedByte()), StandardCharsets.UTF_8);
    }

    private static void consumeAddress(DataInputStream input, int addressType) throws Exception {
        switch (addressType) {
            case 1 -> input.readNBytes(4);
            case 3 -> input.readNBytes(input.readUnsignedByte());
            case 4 -> input.readNBytes(16);
            default -> throw new IllegalStateException("Unexpected SOCKS5 address type: " + addressType);
        }
    }

    private static boolean contains(byte[] values, int expected) {
        for (byte value : values) {
            if (Byte.toUnsignedInt(value) == expected) {
                return true;
            }
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
