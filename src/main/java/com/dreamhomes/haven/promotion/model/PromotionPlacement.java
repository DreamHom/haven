package com.dreamhomes.haven.promotion.model;


public enum PromotionPlacement {
    HOMEPAGE_FEATURED("Featured"),
    LISTING_SEARCH_TOP("Sponsored"),
    AGENT_DIRECTORY_TOP("Featured");

    private final String label;

    PromotionPlacement(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}