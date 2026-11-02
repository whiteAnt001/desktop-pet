package com.whiteant.pet.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PetClientApp {

    private static final int MAX_NAME_LENGTH = 10;
    private static final int MAX_CHAT_LENGTH = 50;
    private static final int MAX_CHAT_LOG = 20;
    private static final long AWAY_MS = 5 * 60 * 1000L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final Map<String, String> ERRORS = Map.of(
            "INVALID_CODE", "초대 코드가 이상해 (영문/숫자 6자리)",
            "ROOM_NOT_FOUND", "없는 초대 코드야",
            "ROOM_FULL", "방이 꽉 찼어 (최대 10명)",
            "INVALID_PROFILE", "닉네임은 1~10자로 해줘"
    );

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, PetWindow> pets = new HashMap<>();
    private final Map<String, GiftWindow> gifts = new HashMap<>();
    private final Deque<String> chatLog = new ArrayDeque<>();
    private final Inventory inventory = new Inventory();

    private Settings settings;
    private NetClient net;
    private String roomCode;
    private String selfId;
    private boolean createdRoom;
    private ChatInputDialog chatInput;
    private TrayIcon trayIcon;

    private Timer idleTimer;
    private Point lastMouse;
    private long lastMouseMovedAt;
    private boolean away;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PetClientApp().start());
    }

    private void start() {
        if (!SingleInstance.acquire(this::quit) && !handleAlreadyRunning()) {
            System.exit(0);
            return;
        }

        settings = Settings.load();

        if (Updater.checkAndUpdate(settings.getServer())) {
            return; // 업데이트 후 자동 재시작
        }

        if (!settings.canAutoJoin() && !showSetup()) {
            quit();
        }
        connect();
    }

    /** 이미 켜져 있을 때. 다시 시작이면 true (기존 거 끄고 이어서 실행) */
    private boolean handleAlreadyRunning() {
        Object[] options = {"다시 시작", "끄기", "취소"};
        int choice = JOptionPane.showOptionDialog(null,
                "데스크톱 펫이 이미 실행 중이야.\n개미가 안 보이거나 안 눌리면 다시 시작해봐.",
                "데스크톱 펫", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice != 0 && choice != 1) {
            return false;
        }

        SingleInstance.requestQuit();
        boolean acquired = SingleInstance.acquireWithRetry(this::quit, 5000);

        if (choice == 1) {
            if (!acquired) {
                JOptionPane.showMessageDialog(null,
                        "기존 프로그램을 못 껐어.\nWin+R → taskkill /IM DesktopPet.exe /F",
                        "데스크톱 펫", JOptionPane.WARNING_MESSAGE);
            }
            return false; // 끄기만 하고 이것도 종료
        }
        return acquired;
    }

    // ===== 설정 / 접속 =====

    /** 확인 누르면 true */
    private boolean showSetup() {
        JTextField serverField = new JTextField(settings.getServer());
        JTextField nameField = new JTextField(settings.getName());
        JTextField codeField = new JTextField(settings.getRoomCode());
        JCheckBox autoJoinBox = new JCheckBox("다음부터 자동으로 이 방에 입장", true);

        Color[] chosen = {Color.decode(settings.getColor())};
        JLabel swatch = new JLabel("      ");
        swatch.setOpaque(true);
        swatch.setBackground(chosen[0]);
        JButton colorButton = new JButton("색 고르기");
        colorButton.addActionListener(e -> {
            Color picked = ColorPicker.pick(null, "개미 색", chosen[0]);
            if (picked != null) {
                chosen[0] = picked;
                swatch.setBackground(picked);
            }
        });
        JPanel colorRow = new JPanel(new BorderLayout(8, 0));
        colorRow.add(swatch, BorderLayout.WEST);
        colorRow.add(colorButton, BorderLayout.CENTER);

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.add(new JLabel("서버 주소"));
        panel.add(serverField);
        panel.add(new JLabel("닉네임 (1~" + MAX_NAME_LENGTH + "자)"));
        panel.add(nameField);
        panel.add(new JLabel("개미 색"));
        panel.add(colorRow);
        panel.add(new JLabel("초대 코드 (비우면 새 방 만들기)"));
        panel.add(codeField);
        panel.add(autoJoinBox);

        while (true) {
            int result = JOptionPane.showConfirmDialog(null, panel, "데스크톱 펫 " + Updater.currentVersion(),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return false;
            }

            String name = nameField.getText().trim();
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
                JOptionPane.showMessageDialog(null, "닉네임은 1~" + MAX_NAME_LENGTH + "자로 해줘");
                continue;
            }

            settings.setServer(serverField.getText().trim().replaceAll("/+$", ""));
            settings.setName(name);
            settings.setColor(toHex(chosen[0]));
            settings.setRoomCode(codeField.getText().trim().toUpperCase());
            settings.setAutoJoin(autoJoinBox.isSelected());
            settings.save();
            return true;
        }
    }

    private void connect() {
        String base = settings.getServer();

        if (settings.getRoomCode().isBlank()) {
            try {
                settings.setRoomCode(createRoom(base));
                settings.save();
                createdRoom = true;
            } catch (Exception e) {
                fail("방 생성 실패: " + e.getMessage());
                return;
            }
        }
        roomCode = settings.getRoomCode();

        net = new NetClient(this::onMessage, this::fail);
        net.connect(base.replaceFirst("^http", "ws") + "/ws")
                .thenRun(() -> net.send(Map.of(
                        "type", "JOIN",
                        "code", roomCode,
                        "name", settings.getName(),
                        "color", settings.getColor(),
                        "item", inventory.equippedCodes(),
                        "clientId", settings.getClientId())))
                .exceptionally(e -> {
                    SwingUtilities.invokeLater(() -> fail("서버 접속 실패: " + e.getMessage()));
                    return null;
                });
    }

    private String createRoom(String base) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/rooms"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return mapper.readTree(response.body()).path("code").asText();
    }

    private void changeRoomSettings() {
        settings.setAutoJoin(false);
        settings.save();
        // 새로 뜨는 쪽이 "이미 실행 중"에 안 걸리게 먼저 놓아줌
        SingleInstance.release();
        if (!Updater.relaunch()) {
            JOptionPane.showMessageDialog(null, "다시 실행하면 설정 창이 떠");
        }
        quit();
    }

    // ===== 트레이 =====

    private void installTray() {
        if (trayIcon != null || !SystemTray.isSupported()) {
            return;
        }

        PopupMenu menu = new PopupMenu();
        menu.add(trayItem("개미 불러오기", this::recallMyAnt));
        menu.add(trayItem("채팅", this::openChatInput));
        menu.add(trayItem("최근 채팅", this::showChatLog));
        menu.addSeparator();
        menu.add(trayItem("초대 코드 복사", this::copyCode));
        menu.add(trayItem("방 설정 바꾸기", this::changeRoomSettings));
        menu.addSeparator();
        menu.add(trayItem("종료", this::quit));

        trayIcon = new TrayIcon(trayImage(Color.decode(settings.getColor())), trayTooltip(), menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> recallMyAnt()); // 더블클릭

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            trayIcon = null;
        }
    }

    private MenuItem trayItem(String label, Runnable action) {
        MenuItem item = new MenuItem(label);
        item.addActionListener(e -> action.run());
        return item;
    }

    private String trayTooltip() {
        return "데스크톱 펫 · 방 " + roomCode;
    }

    private void removeTray() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    /** 트레이 아이콘용 개미 (코드로 그림) */
    private static Image trayImage(Color color) {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(color);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(8, 24, 5, 30);
        g.drawLine(13, 25, 13, 31);
        g.drawLine(18, 24, 21, 30);
        g.fillOval(3, 14, 18, 14);
        g.fillOval(16, 8, 14, 13);
        g.drawLine(20, 9, 17, 2);
        g.drawLine(26, 9, 29, 2);

        g.setColor(Color.WHITE);
        g.fillOval(23, 12, 4, 4);

        g.dispose();
        return img;
    }

    private void recallMyAnt() {
        PetWindow mine = pets.get(selfId);
        if (mine != null) {
            mine.recall();
        }
    }

    // ===== 메시지 처리 =====

    private void onMessage(JsonNode msg) {
        String memberId = msg.path("memberId").asText();
        PetWindow target = pets.get(memberId);

        switch (msg.path("type").asText()) {
            case "WELCOME" -> {
                selfId = msg.path("selfId").asText();
                roomCode = msg.path("code").asText(roomCode);
                settings.setRoomCode(roomCode);
                settings.save();

                for (JsonNode member : msg.path("members")) {
                    spawn(member);
                }
                startIdleWatcher();
                installTray();

                if (createdRoom) {
                    copyCode();
                    JOptionPane.showMessageDialog(null,
                            "초대 코드: " + roomCode + "\n클립보드에 복사됨. 친구한테 보내!");
                }
            }
            case "MEMBER_JOINED" -> spawn(msg.path("member")).showBubble("하이");
            case "MEMBER_LEFT" -> {
                PetWindow pet = pets.remove(memberId);
                if (pet != null) {
                    pet.dispose();
                }
            }
            case "EMOTE" -> {
                if (target != null) {
                    target.showBubble(Emote.textOf(msg.path("emote").asText()));
                }
            }
            case "CHAT" -> {
                if (target != null) {
                    String text = msg.path("text").asText();
                    target.showBubble(text);
                    addChatLog(target.getPetName(), text);
                }
            }
            case "MOVE" -> {
                if (target != null) {
                    target.remoteMove(
                            msg.path("nx").asDouble(),
                            msg.path("ny").asDouble(),
                            msg.path("released").asBoolean());
                }
            }
            case "SNACK" -> {
                if (target != null) {
                    target.showBubble("간식이다!");
                }
                spawnSnack(msg.path("nx").asDouble());
            }
            case "STATUS" -> {
                if (target != null) {
                    target.setSleeping(msg.path("away").asBoolean());
                }
            }
            case "EQUIP" -> {
                if (target != null) {
                    target.setItems(Item.fromCodes(msg.path("item").asText("")));
                }
            }
            case "COLOR" -> {
                if (target != null) {
                    target.setColor(Color.decode(msg.path("color").asText()));
                }
            }
            case "GIFT_DROP" -> spawnGift(msg.path("giftId").asText(), msg.path("nx").asDouble());
            case "GIFT_CLAIMED" -> onGiftClaimed(msg.path("giftId").asText(), memberId, msg.path("item").asText());
            case "GIFT_EXPIRED" -> removeGift(msg.path("giftId").asText());
            case "ERROR" -> fail(ERRORS.getOrDefault(msg.path("code").asText(), "오류: " + msg.path("code").asText()));
            default -> {
            }
        }
    }

    private PetWindow spawn(JsonNode member) {
        String id = member.path("id").asText();
        PetWindow existing = pets.get(id);
        if (existing != null) {
            return existing;
        }

        boolean me = id.equals(selfId);
        PetWindow pet = new PetWindow(
                member.path("name").asText(),
                Color.decode(member.path("color").asText()),
                me,
                me ? this::openMenu : null,
                (nx, ny, released) -> sendMove(id, nx, ny, released));
        pet.setItems(Item.fromCodes(member.path("item").asText("")));
        pet.setSleeping(member.path("away").asBoolean(false));
        if (me) {
            pet.setOnDoubleClick(this::openChatInput);
        }
        pets.put(id, pet);
        pet.start();
        return pet;
    }

    private void sendMove(String targetId, double nx, double ny, boolean released) {
        net.send(Map.of("type", "MOVE", "targetId", targetId, "nx", nx, "ny", ny, "released", released));
    }

    // ===== 채팅 =====

    private void openChatInput() {
        PetWindow mine = pets.get(selfId);
        if (mine == null) {
            return;
        }
        if (chatInput != null && chatInput.isDisplayable()) {
            chatInput.open();
            return;
        }
        chatInput = new ChatInputDialog(mine.getChatAnchor(), this::sendChat);
        chatInput.open();
    }

    private void sendChat(String text) {
        if (text.length() > MAX_CHAT_LENGTH) {
            text = text.substring(0, MAX_CHAT_LENGTH);
        }
        net.send(Map.of("type", "CHAT", "text", text));
    }

    private void addChatLog(String name, String text) {
        chatLog.addLast(LocalTime.now().format(TIME) + "  " + name + ": " + text);
        while (chatLog.size() > MAX_CHAT_LOG) {
            chatLog.removeFirst();
        }
    }

    private void showChatLog() {
        String content = chatLog.isEmpty() ? "아직 채팅이 없어" : String.join("\n", chatLog);

        JTextArea area = new JTextArea(content, 12, 32);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
        area.setCaretPosition(area.getDocument().getLength());

        JOptionPane.showMessageDialog(null, new JScrollPane(area), "최근 채팅", JOptionPane.PLAIN_MESSAGE);
    }

    // ===== 색 =====

    private void changeColor() {
        Color picked = ColorPicker.pick(null, "개미 색", Color.decode(settings.getColor()));
        if (picked == null) {
            return;
        }
        String hex = toHex(picked);
        settings.setColor(hex);
        settings.save();

        PetWindow mine = pets.get(selfId);
        if (mine != null) {
            mine.setColor(picked);
        }
        if (trayIcon != null) {
            trayIcon.setImage(trayImage(picked));
        }
        net.send(Map.of("type", "COLOR", "color", hex));
    }

    private static String toHex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    // ===== 선물상자 =====

    private void spawnGift(String giftId, double nx) {
        if (gifts.containsKey(giftId)) {
            return;
        }
        GiftWindow gift = new GiftWindow(nx, () -> net.send(Map.of("type", "GIFT_CLAIM", "giftId", giftId)));
        gifts.put(giftId, gift);
        gift.start();
    }

    private void onGiftClaimed(String giftId, String memberId, String itemCode) {
        removeGift(giftId);

        Item item = Item.fromCode(itemCode);
        PetWindow pet = pets.get(memberId);
        if (item == null || pet == null) {
            return;
        }

        String text = item.displayName() + " 획득!";
        if (memberId.equals(selfId) && !inventory.add(item)) {
            text = item.displayName() + " (이미 있음)";
        }
        pet.showBubble(text);
        addChatLog("선물", pet.getPetName() + " → " + item.displayName());
    }

    private void removeGift(String giftId) {
        GiftWindow gift = gifts.remove(giftId);
        if (gift != null) {
            gift.dispose();
        }
    }

    // ===== 간식 =====

    private void spawnSnack(double nx) {
        SnackWindow snack = new SnackWindow(nx);
        snack.start();
        for (PetWindow pet : pets.values()) {
            pet.goTo(snack.getCenterX());
        }
    }

    private void giveSnack() {
        PetWindow mine = pets.get(selfId);
        if (mine != null) {
            net.send(Map.of("type", "SNACK", "nx", mine.getNx()));
        }
    }

    // ===== 자리비움 =====

    private void startIdleWatcher() {
        if (idleTimer != null) {
            return;
        }
        lastMouseMovedAt = System.currentTimeMillis();
        idleTimer = new Timer(1000, e -> checkIdle());
        idleTimer.start();
    }

    private void checkIdle() {
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) {
            return;
        }

        Point p = info.getLocation();
        long now = System.currentTimeMillis();
        if (!p.equals(lastMouse)) {
            lastMouse = p;
            lastMouseMovedAt = now;
            if (away) {
                setAway(false);
            }
        } else if (!away && now - lastMouseMovedAt >= AWAY_MS) {
            setAway(true);
        }
    }

    private void setAway(boolean away) {
        this.away = away;
        PetWindow mine = pets.get(selfId);
        if (mine != null) {
            mine.setSleeping(away);
        }
        net.send(Map.of("type", "STATUS", "away", away));
    }

    // ===== 아이템 =====

    private JMenu buildItemMenu() {
        int ownedCount = inventory.getOwned().size();
        JMenu menu = new JMenu("아이템 (" + ownedCount + "/" + Item.values().length + ")");

        if (ownedCount == 0) {
            JMenuItem empty = new JMenuItem("아직 없음 (선물상자를 노려봐)");
            empty.setEnabled(false);
            menu.add(empty);
            return menu;
        }

        for (Item.Slot slot : Item.Slot.values()) {
            List<Item> inSlot = inventory.getOwned().stream()
                    .filter(i -> i.getSlot() == slot)
                    .toList();
            if (inSlot.isEmpty()) {
                continue;
            }

            Item current = inventory.getEquipped(slot);
            JMenu sub = new JMenu(slot.getLabel() + (current == null ? "" : " · " + current.getLabel()));

            ButtonGroup group = new ButtonGroup();
            for (Item item : inSlot) {
                JRadioButtonMenuItem entry = new JRadioButtonMenuItem(item.displayName(), item == current);
                entry.addActionListener(e -> equip(item));
                group.add(entry);
                sub.add(entry);
            }
            sub.addSeparator();

            JMenuItem off = new JMenuItem("해제");
            off.setEnabled(current != null);
            off.addActionListener(e -> unequip(slot));
            sub.add(off);

            menu.add(sub);
        }
        return menu;
    }

    private void equip(Item item) {
        inventory.equip(item);
        applyEquipped();
    }

    private void unequip(Item.Slot slot) {
        inventory.unequip(slot);
        applyEquipped();
    }

    private void applyEquipped() {
        PetWindow mine = pets.get(selfId);
        if (mine != null) {
            mine.setItems(inventory.getEquipped());
        }
        net.send(Map.of("type", "EQUIP", "item", inventory.equippedCodes()));
    }

    // ===== 메뉴 =====

    private void openMenu(Component invoker, int x, int y) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem chat = new JMenuItem("채팅 (개미 더블클릭)");
        chat.addActionListener(e -> openChatInput());
        menu.add(chat);

        JMenuItem log = new JMenuItem("최근 채팅");
        log.addActionListener(e -> showChatLog());
        menu.add(log);
        menu.addSeparator();

        JMenuItem snack = new JMenuItem("간식 주기");
        snack.addActionListener(e -> giveSnack());
        menu.add(snack);

        menu.add(buildItemMenu());

        JMenu emotes = new JMenu("이모트");
        for (Emote emote : Emote.values()) {
            JMenuItem item = new JMenuItem(emote.getText());
            item.addActionListener(e -> net.send(Map.of("type", "EMOTE", "emote", emote.name())));
            emotes.add(item);
        }
        menu.add(emotes);

        JMenuItem color = new JMenuItem("색 바꾸기");
        color.addActionListener(e -> changeColor());
        menu.add(color);
        menu.addSeparator();

        JMenuItem copy = new JMenuItem("초대 코드 복사 (" + roomCode + ")");
        copy.addActionListener(e -> copyCode());
        menu.add(copy);

        JMenuItem room = new JMenuItem("방 설정 바꾸기");
        room.addActionListener(e -> changeRoomSettings());
        menu.add(room);

        JMenuItem quit = new JMenuItem("종료");
        quit.addActionListener(e -> quit());
        menu.add(quit);

        JMenuItem version = new JMenuItem("버전 " + Updater.currentVersion());
        version.setEnabled(false);
        menu.add(version);

        menu.show(invoker, x, y);
    }

    private void copyCode() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(roomCode), null);
    }

    private void quit() {
        removeTray();
        if (net != null) {
            net.close();
        }
        System.exit(0);
    }

    private void fail(String message) {
        // 입장도 못 했으면 다음 실행 땐 설정 창부터 (자동 입장 무한 실패 방지)
        if (selfId == null && settings != null) {
            settings.setAutoJoin(false);
            settings.save();
        }
        JOptionPane.showMessageDialog(null, message, "데스크톱 펫", JOptionPane.ERROR_MESSAGE);
        quit();
    }
}