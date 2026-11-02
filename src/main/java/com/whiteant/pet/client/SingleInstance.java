package com.whiteant.pet.client;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 하나만 실행되게. 로컬 포트를 잡고 있는 쪽이 실행 중인 프로그램.
 * 두 번째로 실행된 쪽은 이 포트로 QUIT를 보내서 기존 걸 끌 수 있음.
 */
public final class SingleInstance {

    private static final int PORT = 47321;
    private static final long FORCE_EXIT_MS = 2000;

    private static ServerSocket server;

    private SingleInstance() {
    }

    /** 내가 첫 번째면 true. 다른 실행에서 종료 요청 오면 onQuitRequest 호출 */
    public static boolean acquire(Runnable onQuitRequest) {
        try {
            server = new ServerSocket(PORT, 1, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            return false;
        }

        Thread listener = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept();
                     BufferedReader in = new BufferedReader(
                             new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                    if ("QUIT".equals(in.readLine())) {
                        SwingUtilities.invokeLater(onQuitRequest);
                        forceExitLater();
                    }
                } catch (IOException ignored) {
                    // 소켓 닫힐 때 여기로 옴
                }
            }
        }, "single-instance");
        listener.setDaemon(true);
        listener.start();
        return true;
    }

    /** 기존 프로그램이 꺼져서 포트가 풀릴 때까지 기다렸다가 잡기 */
    public static boolean acquireWithRetry(Runnable onQuitRequest, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (acquire(onQuitRequest)) {
                return true;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** 실행 중인 프로그램한테 꺼지라고 요청 */
    public static void requestQuit() {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), PORT);
             Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
            out.write("QUIT\n");
            out.flush();
        } catch (IOException ignored) {
        }
    }

    /** 재시작 전에 포트 놓아주기 */
    public static void release() {
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** 화면이 멈춰서 정상 종료가 안 되면 강제로 */
    private static void forceExitLater() {
        Thread killer = new Thread(() -> {
            try {
                Thread.sleep(FORCE_EXIT_MS);
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(0);
        }, "force-exit");
        killer.setDaemon(true);
        killer.start();
    }
}