package com.whiteant.pet.client;

import javax.swing.*;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 색상판(HSV) 먼저 보여주는 색 선택창. 취소하면 null */
public final class ColorPicker {

    private ColorPicker() {
    }

    public static Color pick(Component parent, String title, Color initial) {
        JColorChooser chooser = new JColorChooser(initial);

        // 견본/HSL/CMYK 빼고 HSV(판) → RGB(슬라이더) 순서로
        List<AbstractColorChooserPanel> keep = new ArrayList<>();
        for (AbstractColorChooserPanel panel : chooser.getChooserPanels()) {
            String name = panel.getDisplayName();
            if (name.contains("HSV") || name.contains("RGB")) {
                keep.add(panel);
            }
        }
        keep.sort(Comparator.comparingInt(p -> p.getDisplayName().contains("HSV") ? 0 : 1));
        if (!keep.isEmpty()) {
            chooser.setChooserPanels(keep.toArray(new AbstractColorChooserPanel[0]));
        }

        Color[] result = {null};
        JDialog dialog = JColorChooser.createDialog(parent, title, true, chooser,
                e -> result[0] = chooser.getColor(), null);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
        return result[0];
    }
}