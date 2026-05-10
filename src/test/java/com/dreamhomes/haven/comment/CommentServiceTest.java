package com.dreamhomes.haven.comment;

import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingResponse;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock ListingService listingService;
    @Mock NotificationApi notificationApi;

    CommentService service;

    @BeforeEach
    void setUp() {
        service = new CommentService(commentRepository, listingService, notificationApi);
    }

    @Test
    void postingCommentPersistsRowAndNotifiesListingOwner() {
        when(listingService.findById(7L)).thenReturn(liveListing(7L, /*ownerId=*/99L));
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
        when(listingService.findById(7L)).thenReturn(liveListing(7L, /*ownerId=*/99L));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.post(/*authorId=*/99L, 7L, "Anything I should clarify?");

        verify(notificationApi, never()).recordSync(any(), anyLong(), any());
    }

    @Test
    void postingOnNonExistentListingThrows404() {
        when(listingService.findById(404L)).thenThrow(new ListingNotFoundException(404L));

        assertThatThrownBy(() -> service.post(100L, 404L, "..."))
                .isInstanceOf(ListingNotFoundException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void emptyBodyIsRejectedAtServiceLayerIndependentOfControllerValidation() {
        assertThatThrownBy(() -> service.post(100L, 7L, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(listingService, never()).findById(any());
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
        when(listingService.isOwnedBy(7L, 99L)).thenReturn(true);

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
        when(listingService.isOwnedBy(7L, 200L)).thenReturn(false);

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

    private static ListingResponse liveListing(Long id, Long ownerId) {
        Instant now = Instant.now();
        return new ListingResponse(id, 1L, ownerId, ListingType.SALE,
                new BigDecimal("80000000.00"), "NGN", null, null, null,
                ListingStatus.LIVE, null, 0L, now, now, null);
    }
}
