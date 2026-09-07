package com.tbdev.teaneckminyanim.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyMinyanReviewEmailScheduler {

    private final WeeklyMinyanReviewEmailService weeklyMinyanReviewEmailService;

    @Scheduled(cron = "0 0 22 * * SAT", zone = "America/New_York")
    public void sendWeeklyReviewEmails() {
        WeeklyMinyanReviewEmailService.WeeklyMinyanReviewSendResult result =
                weeklyMinyanReviewEmailService.sendScheduledWeeklyReviewEmails();

        log.info("Weekly minyan review email job completed for {} to {}: {} sent, {} failed, {} skipped",
                result.range().startDate(),
                result.range().endDate(),
                result.sentCount(),
                result.failedCount(),
                result.skippedCount());
    }
}
