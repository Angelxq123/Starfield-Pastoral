package com.stardew.craft.pet;

import java.util.Arrays;
import java.util.List;

/** Shared bounded selection window for the fixed-size adoption screen. */
public record PetChoicePage(List<PetVariant> entries, int page, int pages) {
    public static final int CAPACITY = 12;
    public PetChoicePage { entries = List.copyOf(entries); }
    public static PetChoicePage of(boolean initial, int requestedPage) {
        var choices = Arrays.stream(PetVariant.values()).filter(v -> initial ? v.initial() : v.adoptable()).toList();
        int pages = Math.max(1, (choices.size() + CAPACITY - 1) / CAPACITY);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        return new PetChoicePage(choices.subList(page * CAPACITY, Math.min(choices.size(), (page + 1) * CAPACITY)), page, pages);
    }
}
