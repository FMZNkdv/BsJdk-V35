package com.bsjdk;

import com.bsjdk.logic.Device;
import com.bsjdk.logic.Players;
import com.bsjdk.packets.Factory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;

public class Main {

    private final String ip;
    private final int port;

    public Main(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public void start() {
        System.out.println("[*] Preparing server...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.printf("[*] Server is listening on %s:%d%n", ip, port);
            while (true) {
                Socket client = serverSocket.accept();
                String address = client.getInetAddress().getHostAddress();
                System.out.printf("[*] New connection! Ip: %s%n", address);
                new ClientThread(client).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String host = "0.0.0.0";
        int port = 9339;
        new Main(host, port).start();
    }

    private static class ClientThread extends Thread {
        private final Socket client;
        private final Device device;
        private final Players player;
        private Instant lastPacket;

        public ClientThread(Socket client) {
            this.client = client;
            this.device = new Device(client);
            this.player = new Players(device);
            this.lastPacket = Instant.now();
        }

        private byte[] recvall(int length) throws IOException {
            byte[] data = new byte[length];
            int read = 0;
            while (read < length) {
                int r = client.getInputStream().read(data, read, length - read);
                if (r < 0) {
                    throw new IOException("Receive Error");
                }
                read += r;
            }
            return data;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    byte[] header = new byte[7];
                    int h = client.getInputStream().read(header);
                    if (h > 0) {
                        lastPacket = Instant.now();
                        int packetId = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
                        int length = ((header[2] & 0xFF) << 16)
                                   | ((header[3] & 0xFF) << 8)
                                   | (header[4] & 0xFF);
                        byte[] data = recvall(length);
                        System.out.printf("[*] Received packet! Id: %d%n", packetId);
                        Factory.handle(packetId, client, player, data);
                    }
                    if (Instant.now().minusSeconds(10).isAfter(lastPacket)) {
                        String addr = client.getInetAddress().getHostAddress();
                        System.out.printf("[*] Ip: %s disconnected!%n", addr);
                        client.close();
                        break;
                    }
                }
            } catch (Exception e) {
                String addr = client.getInetAddress().getHostAddress();
                System.out.printf("[*] Ip: %s disconnected!%n", addr);
                try { client.close(); } catch (IOException ignored) {}
            }
        }
    }
}
