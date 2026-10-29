package com.whiteant.pet.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

/** 개미 머리 위에 뜨는 채팅 입력칸. Enter 전송(계속 입력 가능), Esc나 다른 데 클릭하면 닫힘 */
public class ChatInputDialog extends JDialog {

    private final JTextField field = new JTextField(22);

    public ChatInputDialog(Point anchor, Consumer<String> onSend) {
        super((Frame) null, false);
        setUndecorated(true);
        setAlwaysOnTop(true);

        field.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
        field.addActionListener(e -> {
            String text = field.getText().trim();
            if (!text.isEmpty()) {
                onSend.accept(text);
            }
            field.setText("");
        });
        field.registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);

        JLabel hint = new JLabel("Enter 전송 · Esc 닫기");
        hint.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
        hint.setForeground(new Color(0x8c959f));

        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x8c959f)),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        panel.add(field, BorderLayout.CENTER);
        panel.add(hint, BorderLayout.SOUTH);
        setContentPane(panel);
        pack();

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = Math.max(screen.x, Math.min(anchor.x - getWidth() / 2, screen.x + screen.width - getWidth()));
        int y = Math.max(screen.y, anchor.y - getHeight() - 4);
        setLocation(x, y);

        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                dispose();
            }
        });
    }

    public void open() {
        setVisible(true);
        toFront();
        field.requestFocusInWindow();
    }
}