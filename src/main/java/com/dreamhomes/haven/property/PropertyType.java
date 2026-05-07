package com.dreamhomes.haven.property;

/**
 * What kind of physical property this is. APARTMENT and HOUSE require room counts;
 * LAND and COMMERCIAL allow them to be null. The CHECK constraint in V3 mirrors
 * this set so the DB rejects anything we don't recognise.
 */
public enum PropertyType {
    APARTMENT,
    HOUSE,
    LAND,
    COMMERCIAL;

    public boolean requiresRoomCounts() {
        return this == APARTMENT || this == HOUSE;
    }
}
