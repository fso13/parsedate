package ru.drudenko.parsedate.dto;

import java.time.LocalDate;

public class ParseDateRq {
    private String text;
    private LocalDate baseDate = LocalDate.now();
    private boolean isHistory;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public LocalDate getBaseDate() {
        return baseDate;
    }

    public void setBaseDate(LocalDate baseDate) {
        this.baseDate = baseDate;
    }

    public boolean isHistory() {
        return isHistory;
    }

    public void setHistory(boolean history) {
        isHistory = history;
    }
}
