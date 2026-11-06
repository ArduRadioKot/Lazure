package com.rassvet.essential.data.api;

public final class ActivityDayJson {
    public final String date;
    public final int prompts;
    public final int notes;
    public final int total;

    public ActivityDayJson(String date, int prompts, int notes, int total) {
        this.date = date;
        this.prompts = prompts;
        this.notes = notes;
        this.total = total;
    }
}
