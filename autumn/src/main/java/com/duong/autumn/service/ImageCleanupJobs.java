package com.duong.autumn.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.images.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class ImageCleanupJobs {
    private final FileUploadService fileUploadService;

    @Scheduled(fixedDelayString = "${application.images.cleanup-delay-ms:60000}",
            initialDelayString = "${application.images.cleanup-delay-ms:60000}")
    public void retryPendingDeletions() {
        try {
            fileUploadService.retryPendingDeletions();
        } catch (RuntimeException exception) {
            log.warn("Lượt dọn ảnh chưa hoàn tất; sẽ thử lại ở lượt sau", exception);
        }
    }
}
