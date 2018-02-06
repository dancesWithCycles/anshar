package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.apache.commons.lang3.StringUtils;

public class LeftPaddingAdapter extends ValueAdapter {

    private final int paddingLength;
    private final char paddingChar;


    public LeftPaddingAdapter(Class clazz, int paddingLength, char paddingChar) {
        super(clazz);
        this.paddingLength = paddingLength;
        this.paddingChar = paddingChar;
    }

    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return StringUtils.leftPad(text, paddingLength, paddingChar);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeftPaddingAdapter)) return false;

        LeftPaddingAdapter that = (LeftPaddingAdapter) o;

        if (paddingLength != that.paddingLength) return false;
        if (!super.getClassToApply().equals(that.getClassToApply())) return false;
        return paddingChar == that.paddingChar;

    }
}
