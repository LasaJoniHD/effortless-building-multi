package nl.requios.effortlessbuilding.test;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** RCON for the isolated test server only. Never uses a user's server or credentials. */
final class ProbeRcon implements AutoCloseable {
    private final Socket socket = new Socket("127.0.0.1", 25577);
    private int sequence;
    ProbeRcon() throws IOException { request(3, "backend-probe-local-only"); }

    String command(String text) throws IOException { return request(2, text); }

    private String request(int type, String text) throws IOException {
        int id = ++sequence;
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer out = ByteBuffer.allocate(body.length + 14).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(body.length + 10).putInt(id).putInt(type).put(body).put((byte) 0).put((byte) 0);
        socket.getOutputStream().write(out.array());
        InputStream input = socket.getInputStream();
        socket.setSoTimeout(5000);
        int size = ByteBuffer.wrap(input.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
        ByteBuffer result = ByteBuffer.wrap(input.readNBytes(size)).order(ByteOrder.LITTLE_ENDIAN);
        if (result.getInt() != id) throw new IOException("RCON rejected request");
        result.getInt();
        byte[] response = new byte[size - 10];
        result.get(response);
        return new String(response, StandardCharsets.UTF_8);
    }

    @Override public void close() throws IOException { socket.close(); }
}
