package com.dreamhomes.haven.property.model;

/**
 * What kind of physical property this is. APARTMENT, HOUSE, MINI_FLAT,
 * SELF_CONTAIN, ROOM_AND_PARLOUR require room counts; STUDIO, LAND, and
 * COMMERCIAL allow them to be null. The CHECK constraint in V3 (extended in
 * V21) mirrors this set so the DB rejects anything we don't recognise.
 *
 * <p>The Lagos-specific values (SELF_CONTAIN, MINI_FLAT, STUDIO,
 * ROOM_AND_PARLOUR) were added in response to the persona audit — first-time
 * renters look for "self-cons" and "mini-flats" by name, and lumping them
 * under APARTMENT made filtering impossible.</p>
 */
public enum PropertyType {
    APARTMENT,
    HOUSE,
    LAND,
    COMMERCIAL,
    SELF_CONTAIN,
    MINI_FLAT,
    STUDIO,
    ROOM_AND_PARLOUR;

    public boolean requiresRoomCounts() {
        return switch (this) {
            case APARTMENT, HOUSE, MINI_FLAT, SELF_CONTAIN, ROOM_AND_PARLOUR -> true;
            case STUDIO, LAND, COMMERCIAL -> false;
        };
    }
}
