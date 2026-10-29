package com.whiteant.pet.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 자동 업데이트.
 * 실행 중엔 jar가 잠겨 있어서, 다운로드까지만 하고 교체/재실행은 앱이 꺼진 뒤 PowerShell 스크립트가 함.
 */
public final class Updater {

    private static final String JAR_NAME = "desktop-pet-client.jar";
    private static final String EXE_NAME = "DesktopPet.exe";

    private interface Progress {
        void update(int percent);
    }

    private Updater() {
    }

    public static String currentVersion() {
        String v = Updater.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    /** 런타임/라이브러리 묶음 버전. 이게 오르면 zip 통째로 받아야 함 */
    private static int currentBundleVersion() {
        try {
            return Integer.parseInt(Updater.class.getPackage().getSpecificationVersion());
        } catch (Exception e) {
            return 0;
        }
    }

    /** 업데이트를 시작해서 앱이 곧 꺼지면 true */
    public static boolean checkAndUpdate(String server) {
        String current = currentVersion();
        Path installDir = installDir();
        if ("dev".equals(current) || installDir == null) {
            return false; // IntelliJ 실행 같은 개발 환경
        }

        JsonNode latest;
        try {
            latest = fetchLatest(server);
        } catch (Exception e) {
            return false; // 서버 안 되면 그냥 실행
        }

        String latestVersion = latest.path("version").asText("");
        if (compare(latestVersion, current) <= 0) {
            return false;
        }

        boolean forced = compare(latest.path("minVersion").asText("0"), current) > 0;
        Object[] options = forced ? new Object[]{"업데이트"} : new Object[]{"업데이트", "나중에"};
        int choice = JOptionPane.showOptionDialog(null,
                "업데이트가 있습니다\n" + current + " → " + latestVersion,
                "데스크톱 펫", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);

        if (choice != 0) {
            if (forced) {
                System.exit(0);
            }
            return false;
        }

        boolean full = latest.path("bundleVersion").asInt(0) > currentBundleVersion();
        return runUpdate(server, latest, installDir, full);
    }

    /** exe로 실행 중일 때 자기 자신 다시 켜기 */
    public static boolean relaunch() {
        Path dir = installDir();
        if (dir == null) {
            return false;
        }
        try {
            new ProcessBuilder(dir.resolve(EXE_NAME).toString()).start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean runUpdate(String server, JsonNode latest, Path installDir, boolean full) {
        String file = latest.path(full ? "zip" : "jar").asText();
        String expectedSha = latest.path(full ? "zipSha256" : "jarSha256").asText();

        JDialog dialog = new JDialog((Frame) null, "업데이트 중", true);
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        JLabel label = new JLabel("다운로드 중…");
        label.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        panel.add(label, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        dialog.setContentPane(panel);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setSize(320, 110);
        dialog.setLocationRelativeTo(null);

        Exception[] error = {null};
        Thread worker = new Thread(() -> {
            try {
                Path tmp = Files.createTempDirectory("desktoppet-update");
                Path downloaded = tmp.resolve(file);
                download(server + "/api/client/files/" + file, downloaded,
                        p -> SwingUtilities.invokeLater(() -> bar.setValue(p)));

                if (!expectedSha.equalsIgnoreCase(sha256(downloaded))) {
                    throw new IOException("받은 파일이 손상됐어");
                }

                SwingUtilities.invokeLater(() -> label.setText("설치 준비 중…"));
                Path script;
                if (full) {
                    Path extracted = tmp.resolve("extracted");
                    unzip(downloaded, extracted);
                    script = writeScript(tmp, extracted.resolve("DesktopPet"), installDir, true);
                } else {
                    script = writeScript(tmp, downloaded, installDir, false);
                }

                new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-WindowStyle", "Hidden", "-File", script.toString()).start();
            } catch (Exception e) {
                error[0] = e;
            }
            SwingUtilities.invokeLater(dialog::dispose);
        }, "updater");
        worker.start();

        dialog.setVisible(true); // 작업 끝날 때까지 여기서 대기

        if (error[0] != null) {
            JOptionPane.showMessageDialog(null,
                    "업데이트 실패: " + error[0].getMessage() + "\n일단 지금 버전으로 실행할게",
                    "데스크톱 펫", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        System.exit(0); // 꺼지면 스크립트가 교체하고 다시 켬
        return true;
    }

    private static Path writeScript(Path tmp, Path source, Path installDir, boolean full) throws IOException {
        long pid = ProcessHandle.current().pid();
        Path exe = installDir.resolve(EXE_NAME);

        String copy = full
                ? "Copy-Item -Path (Join-Path " + q(source) + " '*') -Destination " + q(installDir) + " -Recurse -Force"
                : "Copy-Item -LiteralPath " + q(source) + " -Destination "
                + q(installDir.resolve("app").resolve(JAR_NAME)) + " -Force";

        String ps = String.join("\r\n",
                "while (Get-Process -Id " + pid + " -ErrorAction SilentlyContinue) { Start-Sleep -Milliseconds 300 }",
                "Start-Sleep -Milliseconds 500",
                "try {",
                "  " + copy,
                "} finally {",
                "  Start-Process -FilePath " + q(exe),
                "}",
                "Remove-Item -LiteralPath " + q(tmp) + " -Recurse -Force -ErrorAction SilentlyContinue");

        // 한글 경로 대비 UTF-8 BOM
        Path script = tmp.resolve("update.ps1");
        try (OutputStream out = Files.newOutputStream(script)) {
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            out.write(ps.getBytes(StandardCharsets.UTF_8));
        }
        return script;
    }

    private static String q(Path path) {
        return "'" + path.toString().replace("'", "''") + "'";
    }

    private static Path installDir() {
        try {
            Path jar = Path.of(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!jar.getFileName().toString().equals(JAR_NAME)) {
                return null;
            }
            Path dir = jar.getParent().getParent(); // DesktopPet/app/xxx.jar → DesktopPet
            return Files.exists(dir.resolve(EXE_NAME)) ? dir : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonNode fetchLatest(String server) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(server + "/api/client/latest"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return new ObjectMapper().readTree(response.body());
    }

    private static void download(String url, Path target, Progress progress) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpResponse<InputStream> response = client.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("다운로드 실패 HTTP " + response.statusCode());
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buf = new byte[64 * 1024];
            long read = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                read += n;
                if (total > 0) {
                    progress.update((int) (read * 100 / total));
                }
            }
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static void unzip(Path zip, Path dest) throws IOException {
        Files.createDirectories(dest);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = dest.resolve(entry.getName()).normalize();
                if (!out.startsWith(dest)) {
                    throw new IOException("잘못된 압축 파일");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** "0.4.10" vs "0.4.9" 같은 비교 */
    private static int compare(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int na = i < pa.length ? parse(pa[i]) : 0;
            int nb = i < pb.length ? parse(pb[i]) : 0;
            if (na != nb) {
                return Integer.compare(na, nb);
            }
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}