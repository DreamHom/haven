package com.dreamhomes.haven.comment;

import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock ListingRepository listingRepository;
    @Mock NotificationApi notificationApi;

    CommentService service;

    @BeforeEach
    void setUp() {
        service = new CommentService(commentRepository, listingRepository, notificationApi);
    }

    @Test
    void postingCommentPersistsRowAndNotifiesListingOwner() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(liveListing(7L, /*ownerId=*/99L)));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(123L);
            return c;
        });

        Comment posted = service.post(/*authorId=*/100L, 7L, "Hi, is this still available?");

        ArgumentCaptor<Comment> commentCap = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(commentCap.capture());
        assertThat(commentCap.getValue().getListingId()).isEqualTo(7L);
        assertThat(commentCap.getValue().getAuthorUserId()).isEqualTo(100L);
        assertThat(commentCap.getValue().getBody()).isEqualTo("Hi, is this still available?");
        assertThat(commentCap.getValue().getDeletedAt()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCap = ArgumentCaptor.forClass(Map.class);
        verify(notificationApi).recordSync(eq(NotificationKind.COMMENT_POSTED), eq(99L), payloadCap.capture());
        assertThat(payloadCap.getValue()).containsEntry("commentId", 123L).containsEntry("listingId", 7L);
        assertThat(posted.getId()).isEqualTo(123L);
    }

    @Test
    void ownerCommentingOnTheirOwnListingDoesNotSelfNotify() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(liveListing(7L, /*ownerId=*/99L)));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.post(/*authorId=*/99L, 7L, "Anything I should clarify?");

        verify(notificationApi, never()).recordSync(any(), anyLong(), any());
    }

    @Test
    void postingOnNonExistentListingThrows404() {
        when(listingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.post(100L, 404L, "..."))
                .isInstanceOf(ListingNotFoundException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void emptyBodyIsRejectedAtServiceLayerIndependentOfControllerValidation() {
        assertThatThrownBy(() -> service.post(100L, 7L, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(listingRepository, never()).findById(any());
    }

    @Test
    void authorCanDeleteTheirOwnComment() {
        Comment existing = active(50L, /*authorId=*/100L, /*listingId=*/7L);
        when(commentRepository.findById(50L)).thenReturn(Optional.of(existing));

        service.delete(/*callerId=*/100L, /*callerRole=*/Role.APPLICANT, 50L, null);

        ArgumentCaptor<Comment> cap = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(cap.capture());
        assertThat(cap.getValue().getDeletedAt()).isNotNull();
        assertThat(cap.getValue().getDeletedByUserId()).isEqualTo(100L);
    }

    @Test
    void listingOwnerCanDeleteCommentOnTheirListing() {
        Comment existing = active(50L, /*authorId=*/100L, 7L);
        when(commentRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(listingRepository.findById(7L)).thenReturn(Optional.of(liveListing(7L, /*ownerId=*/99L)));

        service.delete(/*callerId=*/99L, Role.OWNER, 50L, "Off-topic");

        ArgumentCaptor<Comment> cap = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(cap.capture());
        assertThat(cap.getValue().getDeletionReason()).isEqualTo("Off-topic");
    }

    @Test
    void adminCanDeleteAnyComment() {
        Comment existing = active(50L, 100L, 7L);
        when(commentRepository.findById(50L)).thenReturn(Optional.of(existing));

        service.delete(/*callerId=*/1L, Role.ADMIN, 50L, "Spam");

        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void randomUserCannotDeleteSomeoneElsesCommentEvenIfTheyAreAnOwnerOfADifferentListing() {
        Comment existing = active(50L, /*authorId=*/100L, /*listingId=*/7L);
        when(commentRepository.findById(50L)).thenReturn(Optional.of(existing));
        // Caller is an OWNER but of a different listing — listing 7's owner is 99, caller is 200.
        when(listingRepository.findById(7L)).thenReturn(Optional.of(liveListing(7L, /*ownerId=*/99L)));

        assertThatThrownBy(() -> service.delete(/*callerId=*/200L, Role.OWNER, 50L, "any"))
                .isInstanceOf(NotAuthorisedToDeleteCommentException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void deletingAlreadyDeletedCommentReturnsConflict() {
        Comment existing = active(50L, 100L, 7L);
        existing.setDeletedAt(Instant.now());
        existing.setDeletedByUserId(1L);
        when(commentRepository.findById(50L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete(1L, Role.ADMIN, 50L, null))
                .isInstanceOf(CommentAlreadyDeletedException.class);
    }

    @Test
    void deletingNonExistentCommentReturns404() {
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, Role.ADMIN, 999L, null))
                .isInstanceOf(CommentNotFoundException.class);
    }

    private static Comment active(Long id, Long authorId, Long listingId) {
        return Comment.builder()
                .id(id).listingId(listingId).authorUserId(authorId)
                .body("body").createdAt(Instant.now()).build();
    }

    private static Listing liveListing(Long id, Long ownerId) {
        Instant now = Instant.now();
        return Listing.builder()
                .id(id).propertyId(1L).ownerId(ownerId)
                .listingType(ListingType.SALE).askingPrice(new BigDecimal("80000000.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(now).updatedAt(now).version(0L).build();
    }
}
