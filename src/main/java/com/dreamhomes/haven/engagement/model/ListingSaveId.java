package com.dreamhomes.haven.engagement.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Composite PK for {@link ListingSave} — one row per (user, listing). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ListingSaveId implements Serializable {

    private Long userId;
    private Long listingId;
}
