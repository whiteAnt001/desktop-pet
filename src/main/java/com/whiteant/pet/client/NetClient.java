package com.whiteant.pet.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import javax.swing.SwingUtilities;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RequiredArgsConstructor
public class NetClient implements WebSocket.Listener {

    private static final long PING_INTERVAL_SEC = 30;

    private final Consumer<JsonNode> onMessage;
    private final Consumer<String> onClosed;

    private final ObjectMapper mapper = new ObjectMapper();
    private final StringBuilder buffer = new StringBuilder();
    private final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-ping");
        t.setDaemon(true);
        return t;
    });

    private volatile WebSocket ws;
    private volatile boolean closing;
    private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

    public CompletableFuture<Void> connect(String url) {
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(url), this)
                .thenAccept(socket -> {
                    ws = socket;
                    // 아무 메시지 없어도 Nginx/중간 장비가 연결 안 끊게
                    pinger.scheduleAtFixedRate(this::ping, PING_INTERVAL_SEC, PING_INTERVAL_SEC, TimeUnit.SECONDS);
                });
    }

    public synchronized void send(Object payload) {
        String text;
        try {
            text = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        // java WebSocket은 이전 send가 끝나기 전에 또 보내면 예외라서 체인으로 순서 보장
        sendChain = sendChain.thenCompose(v -> ws.sendText(text, true));
    }

    private synchronized void ping() {
        if (ws == null || closing) {
            return;
        }
        sendChain = sendChain.thenCompose(v -> ws.sendPing(ByteBuffer.allocate(0)));
    }

    public void close() {
        closing = true;
        pinger.shutdownNow();
        if (ws == null) {
            return;
        }
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(1, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        if (last) {
            String text = buffer.toString();
            buffer.setLength(0);
            try {
                JsonNode node = mapper.readTree(text);
                SwingUtilities.invokeLater(() -> onMessage.accept(node));
            } catch (JsonProcessingException ignored) {
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        notifyClosed("서버 연결이 끊겼어");
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        notifyClosed("연결 오류: " + error.getMessage());
    }

    private void notifyClosed(String reason) {
        if (!closing) {
            SwingUtilities.invokeLater(() -> onClosed.accept(reason));
        }
    }
}