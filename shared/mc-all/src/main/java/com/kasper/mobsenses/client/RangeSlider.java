package com.kasper.mobsenses.client;

import com.kasper.mobsenses.compat.Btn;
import com.kasper.mobsenses.compat.Tip;
import com.kasper.mobsenses.compat.Txt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * Quantized range slider that formats live value badges (e.g. {@code Hearing Multiplier: 1.50×},
 * {@code Gunshot Range: 48 m}, {@code HUD Opacity: 85%}) across Minecraft 1.16.5 - 26.x.
 */
public final class RangeSlider extends AbstractSliderButton {

    private final Component label;
    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<String> formatter;
    private final DoubleConsumer onChanged;

    public RangeSlider(
            int x,
            int y,
            int width,
            int height,
            Component label,
            double current,
            double min,
            double max,
            double step,
            DoubleFunction<String> formatter,
            DoubleConsumer onChanged,
            Tip tip) {
        super(x, y, width, height, Txt.empty(), toNormalized(current, min, max));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.formatter = formatter;
        this.onChanged = onChanged;
        Btn.setTip(this, tip);
        updateMessage();
    }

    private static double toNormalized(double v, double min, double max) {
        if (max <= min) {
            return 0.0;
        }
        double clamped = Math.max(min, Math.min(max, v));
        return (clamped - min) / (max - min);
    }

    public double realValue() {
        double raw = min + this.value * (max - min);
        if (step > 0.0) {
            raw = Math.round(raw / step) * step;
        }
        return Math.max(min, Math.min(max, raw));
    }

    public void setRealValue(double next) {
        this.value = toNormalized(next, min, max);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        String badge = formatter.apply(realValue());
        MutableComponent msg = Txt.empty()
                .append(label)
                .append(Txt.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Txt.literal(badge).withStyle(this.active ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        setMessage(msg);
    }

    @Override
    protected void applyValue() {
        if (onChanged != null) {
            onChanged.accept(realValue());
        }
    }
}
