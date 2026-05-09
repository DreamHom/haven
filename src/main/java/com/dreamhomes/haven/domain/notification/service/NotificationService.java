package com.dreamhomes.haven.domain.notification.service;

import com.dreamhomes.haven.domain.notification.model.Notification;
import com.dreamhomes.haven.domain.notification.model.NotificationType;
import com.dreamhomes.haven.domain.notification.repository.NotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification create(Long userId, NotificationType type, String payload) {
        var n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setPayload(payload);
        return notificationRepository.save(n);
    }

    @Transactional(readOnly = true)
    public List<Notification> listForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}

