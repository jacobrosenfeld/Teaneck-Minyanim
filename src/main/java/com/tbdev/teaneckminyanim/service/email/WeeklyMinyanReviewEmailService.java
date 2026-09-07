package com.tbdev.teaneckminyanim.service.email;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.api.dto.ScheduleEventDto;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationService;
import com.tbdev.teaneckminyanim.service.EffectiveScheduleService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.ScheduleEnrichmentService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.calendar.CalendarImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyMinyanReviewEmailService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter DAY_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private final TNMUserService userService;
    private final OrganizationService organizationService;
    private final EffectiveScheduleService effectiveScheduleService;
    private final ScheduleEnrichmentService scheduleEnrichmentService;
    private final CalendarImportService calendarImportService;
    private final CalendarMaterializationService materializationService;
    private final ApplicationSettingsService settingsService;
    private final EmailService emailService;

    public WeeklyMinyanReviewSendResult sendScheduledWeeklyReviewEmails() {
        return sendWeeklyReviewEmails(true, LocalDate.now(settingsService.getZoneId()));
    }

    public WeeklyMinyanReviewSendResult sendWeeklyReviewEmails(boolean refreshBeforeSend, LocalDate referenceDate) {
        DateRange range = upcomingSundayThroughSaturday(referenceDate);

        if (refreshBeforeSend) {
            try {
                calendarImportService.importAllEnabledOrganizations();
                materializationService.materializeAll();
            } catch (RuntimeException e) {
                log.error("Weekly minyan review refresh failed; skipping digest send", e);
                return new WeeklyMinyanReviewSendResult(range, 0, 0, 0, 0, false, e.getMessage());
            }
        }

        List<TNMUser> recipients = userService.getWeeklyMinyanReviewEmailRecipients();
        Map<String, Organization> organizationsById = enabledOrganizationsById();
        List<OrganizationScheduleSummary> allOrgSummaries = buildOrganizationSummaries(
                organizationsById.values().stream()
                        .sorted(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList(),
                range);
        Map<String, OrganizationScheduleSummary> summariesByOrgId = allOrgSummaries.stream()
                .collect(Collectors.toMap(summary -> summary.organization().getId(), summary -> summary));

        int sent = 0;
        int failed = 0;
        int skipped = 0;

        for (TNMUser recipient : recipients) {
            if (recipient.isSuperAdmin()) {
                if (sendSuperAdminDigest(recipient, allOrgSummaries, range).isSuccess()) {
                    sent++;
                } else {
                    failed++;
                }
                continue;
            }

            String orgId = trimToNull(recipient.getOrganizationId());
            Organization organization = orgId == null ? null : organizationsById.get(orgId);
            if (organization == null) {
                skipped++;
                log.warn("Skipping weekly review email for account {} because organization {} was not found or is disabled",
                        recipient.getId(), recipient.getOrganizationId());
                continue;
            }

            OrganizationScheduleSummary summary = summariesByOrgId.get(organization.getId());
            if (sendOrganizationDigest(recipient, organization, summary.events(), range).isSuccess()) {
                sent++;
            } else {
                failed++;
            }
        }

        return new WeeklyMinyanReviewSendResult(range, recipients.size(), sent, failed, skipped, true, null);
    }

    public DateRange upcomingSundayThroughSaturday(LocalDate referenceDate) {
        LocalDate start = referenceDate.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        return new DateRange(start, start.plusDays(6));
    }

    private EmailSendResult sendOrganizationDigest(
            TNMUser recipient,
            Organization organization,
            List<ScheduleEventDto> events,
            DateRange range) {
        Map<LocalDate, List<ScheduleEventDto>> eventsByDate = groupByDate(events);
        String reviewUrl = organizationReviewUrl(organization.getId(), range);
        String subject = settingsService.getSiteName() + " weekly minyan review: "
                + organization.getName() + ", " + range.displayLabel();

        EmailMessage message = EmailMessage.builder()
                .to(recipient.getEmail())
                .subject(subject)
                .textBody(buildOrganizationTextBody(organization, eventsByDate, range, reviewUrl))
                .htmlBody(buildOrganizationHtmlBody(organization, eventsByDate, range, reviewUrl))
                .organizationId(organization.getId())
                .metadata("source", "weekly-minyan-review")
                .metadata("digestType", "organization")
                .metadata("rangeStart", range.startDate().toString())
                .metadata("rangeEnd", range.endDate().toString())
                .build();

        return send(message, recipient);
    }

    private EmailSendResult sendSuperAdminDigest(
            TNMUser recipient,
            List<OrganizationScheduleSummary> summaries,
            DateRange range) {
        String reviewUrl = superAdminReviewUrl(range);
        String subject = settingsService.getSiteName() + " weekly minyan review: all shuls, " + range.displayLabel();

        EmailMessage message = EmailMessage.builder()
                .to(recipient.getEmail())
                .subject(subject)
                .textBody(buildSuperAdminTextBody(summaries, range, reviewUrl))
                .htmlBody(buildSuperAdminHtmlBody(summaries, range, reviewUrl))
                .metadata("source", "weekly-minyan-review")
                .metadata("digestType", "super-admin")
                .metadata("rangeStart", range.startDate().toString())
                .metadata("rangeEnd", range.endDate().toString())
                .build();

        return send(message, recipient);
    }

    private EmailSendResult send(EmailMessage message, TNMUser recipient) {
        EmailSendResult result = emailService.send(message);
        if (!result.isSuccess()) {
            log.warn("Weekly minyan review email failed for account {}: {}", recipient.getId(), result.getMessage());
        }
        return result;
    }

    private Map<String, Organization> enabledOrganizationsById() {
        return organizationService.getAll().stream()
                .filter(Organization::isEnabled)
                .filter(org -> trimToNull(org.getId()) != null)
                .collect(Collectors.toMap(Organization::getId, org -> org, (left, right) -> left, LinkedHashMap::new));
    }

    private List<OrganizationScheduleSummary> buildOrganizationSummaries(List<Organization> organizations, DateRange range) {
        return organizations.stream()
                .map(org -> {
                    List<CalendarEvent> events = effectiveScheduleService.getEffectiveEventsInRange(
                            org.getId(), range.startDate(), range.endDate());
                    List<ScheduleEventDto> enrichedEvents = scheduleEnrichmentService.annotateZmanim(events.stream()
                            .sorted(Comparator.comparing(CalendarEvent::getDate).thenComparing(CalendarEvent::getStartTime))
                            .map(event -> ScheduleEventDto.from(event, org))
                            .toList());
                    return new OrganizationScheduleSummary(org, enrichedEvents);
                })
                .toList();
    }

    private Map<LocalDate, List<ScheduleEventDto>> groupByDate(List<ScheduleEventDto> events) {
        return events.stream()
                .sorted(Comparator.comparing(ScheduleEventDto::date).thenComparing(ScheduleEventDto::startTime))
                .collect(Collectors.groupingBy(
                        event -> LocalDate.parse(event.date()),
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private String buildOrganizationTextBody(
            Organization organization,
            Map<LocalDate, List<ScheduleEventDto>> eventsByDate,
            DateRange range,
            String reviewUrl) {
        StringBuilder body = new StringBuilder();
        body.append("Weekly minyan review for ").append(organization.getName()).append("\n");
        body.append(range.displayLabel()).append("\n\n");

        if (eventsByDate.isEmpty()) {
            body.append("No minyanim are scheduled for this week.\n\n");
        } else {
            for (Map.Entry<LocalDate, List<ScheduleEventDto>> day : eventsByDate.entrySet()) {
                body.append(DAY_FORMATTER.format(day.getKey())).append("\n");
                for (ScheduleEventDto event : day.getValue()) {
                    body.append("- ").append(formatEventText(event)).append("\n");
                }
                body.append("\n");
            }
        }

        body.append("Review Overrides: ").append(reviewUrl).append("\n");
        return body.toString();
    }

    private String buildOrganizationHtmlBody(
            Organization organization,
            Map<LocalDate, List<ScheduleEventDto>> eventsByDate,
            DateRange range,
            String reviewUrl) {
        StringBuilder html = new StringBuilder();
        appendEmailStart(html);
        html.append("<h1 style=\"margin:0 0 8px;font-size:22px;color:#111827;\">Weekly minyan review</h1>");
        html.append("<p style=\"margin:0 0 20px;color:#4b5563;\">")
                .append(escape(organization.getName()))
                .append(" - ")
                .append(escape(range.displayLabel()))
                .append("</p>");

        if (eventsByDate.isEmpty()) {
            html.append("<p style=\"margin:0 0 20px;color:#4b5563;\">No minyanim are scheduled for this week.</p>");
        } else {
            for (Map.Entry<LocalDate, List<ScheduleEventDto>> day : eventsByDate.entrySet()) {
                html.append("<h2 style=\"margin:20px 0 8px;font-size:16px;color:#111827;\">")
                        .append(escape(DAY_FORMATTER.format(day.getKey())))
                        .append("</h2>");
                html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;\">");
                for (ScheduleEventDto event : day.getValue()) {
                    html.append("<tr>");
                    html.append("<td style=\"width:82px;padding:8px 8px 8px 0;border-top:1px solid #e5e7eb;color:#111827;font-weight:600;white-space:nowrap;\">")
                            .append(escape(formatTime(event)))
                            .append("</td>");
                    html.append("<td style=\"padding:8px 0;border-top:1px solid #e5e7eb;color:#374151;\">")
                            .append(formatEventHtml(event))
                            .append("</td>");
                    html.append("</tr>");
                }
                html.append("</table>");
            }
        }

        appendButton(html, reviewUrl, "Review Overrides");
        appendEmailEnd(html);
        return html.toString();
    }

    private String buildSuperAdminTextBody(
            List<OrganizationScheduleSummary> summaries,
            DateRange range,
            String reviewUrl) {
        StringBuilder body = new StringBuilder();
        body.append("Weekly minyan review for all shuls\n");
        body.append(range.displayLabel()).append("\n\n");
        body.append("Total shuls: ").append(summaries.size()).append("\n");
        body.append("Total minyanim: ").append(summaries.stream().mapToInt(OrganizationScheduleSummary::eventCount).sum()).append("\n\n");

        for (OrganizationScheduleSummary summary : summaries) {
            body.append("- ")
                    .append(summary.organization().getName())
                    .append(": ")
                    .append(summary.eventCount())
                    .append(" minyanim across ")
                    .append(summary.daysWithSchedule())
                    .append(" day(s)");
            if (summary.manualEventCount() > 0) {
                body.append(", ").append(summary.manualEventCount()).append(" manual");
            }
            if (summary.importedDayCount() > 0) {
                body.append(", ").append(summary.importedDayCount()).append(" imported day(s)");
            }
            body.append("\n");
        }

        body.append("\nReview Overrides: ").append(reviewUrl).append("\n");
        return body.toString();
    }

    private String buildSuperAdminHtmlBody(
            List<OrganizationScheduleSummary> summaries,
            DateRange range,
            String reviewUrl) {
        int totalEvents = summaries.stream().mapToInt(OrganizationScheduleSummary::eventCount).sum();
        StringBuilder html = new StringBuilder();
        appendEmailStart(html);
        html.append("<h1 style=\"margin:0 0 8px;font-size:22px;color:#111827;\">Weekly minyan review</h1>");
        html.append("<p style=\"margin:0 0 20px;color:#4b5563;\">All shuls - ")
                .append(escape(range.displayLabel()))
                .append("</p>");
        html.append("<p style=\"margin:0 0 16px;color:#374151;\"><strong>")
                .append(summaries.size())
                .append("</strong> shuls, <strong>")
                .append(totalEvents)
                .append("</strong> minyanim total.</p>");

        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;\">");
        html.append("<tr>");
        html.append("<th align=\"left\" style=\"padding:8px 8px 8px 0;border-bottom:1px solid #d1d5db;color:#374151;\">Shul</th>");
        html.append("<th align=\"right\" style=\"padding:8px;border-bottom:1px solid #d1d5db;color:#374151;\">Minyanim</th>");
        html.append("<th align=\"right\" style=\"padding:8px;border-bottom:1px solid #d1d5db;color:#374151;\">Days</th>");
        html.append("<th align=\"left\" style=\"padding:8px 0 8px 8px;border-bottom:1px solid #d1d5db;color:#374151;\">Notes</th>");
        html.append("</tr>");
        for (OrganizationScheduleSummary summary : summaries) {
            html.append("<tr>");
            html.append("<td style=\"padding:9px 8px 9px 0;border-bottom:1px solid #e5e7eb;color:#111827;\">")
                    .append(escape(summary.organization().getName()))
                    .append("</td>");
            html.append("<td align=\"right\" style=\"padding:9px 8px;border-bottom:1px solid #e5e7eb;color:#374151;\">")
                    .append(summary.eventCount())
                    .append("</td>");
            html.append("<td align=\"right\" style=\"padding:9px 8px;border-bottom:1px solid #e5e7eb;color:#374151;\">")
                    .append(summary.daysWithSchedule())
                    .append("</td>");
            html.append("<td style=\"padding:9px 0 9px 8px;border-bottom:1px solid #e5e7eb;color:#4b5563;\">")
                    .append(escape(summary.notesLabel()))
                    .append("</td>");
            html.append("</tr>");
        }
        html.append("</table>");

        appendButton(html, reviewUrl, "Review Overrides");
        appendEmailEnd(html);
        return html.toString();
    }

    private String formatEventText(ScheduleEventDto event) {
        StringBuilder text = new StringBuilder();
        text.append(formatTime(event)).append(" ").append(event.minyanTypeDisplay());
        if (hasText(event.dynamicTimeString())) {
            text.append(" (").append(event.dynamicTimeString().trim()).append(")");
        }
        appendTextDetail(text, event.locationName());
        appendTextDetail(text, event.notes());
        if (EventSource.MANUAL.name().equals(event.source())) {
            text.append(" [Manual]");
        } else if (EventSource.IMPORTED.name().equals(event.source())) {
            text.append(" [Calendar Import]");
        }
        return text.toString();
    }

    private String formatEventHtml(ScheduleEventDto event) {
        StringBuilder html = new StringBuilder();
        html.append("<strong>").append(escape(event.minyanTypeDisplay())).append("</strong>");
        if (hasText(event.dynamicTimeString())) {
            html.append(" <span style=\"color:#6b7280;\">(")
                    .append(escape(event.dynamicTimeString().trim()))
                    .append(")</span>");
        }
        if (hasText(event.locationName())) {
            html.append("<div style=\"margin-top:2px;color:#4b5563;\">")
                    .append(escape(event.locationName().trim()))
                    .append("</div>");
        }
        if (hasText(event.notes())) {
            html.append("<div style=\"margin-top:2px;color:#6b7280;\">")
                    .append(escape(event.notes().trim()))
                    .append("</div>");
        }
        if (EventSource.MANUAL.name().equals(event.source())) {
            html.append(" <span style=\"display:inline-block;margin-top:4px;padding:2px 6px;border-radius:4px;background:#fef3c7;color:#92400e;font-size:12px;\">Manual</span>");
        } else if (EventSource.IMPORTED.name().equals(event.source())) {
            html.append(" <span style=\"display:inline-block;margin-top:4px;padding:2px 6px;border-radius:4px;background:#dbeafe;color:#1d4ed8;font-size:12px;\">Calendar Import</span>");
        }
        return html.toString();
    }

    private void appendTextDetail(StringBuilder text, String value) {
        if (hasText(value)) {
            text.append(" - ").append(value.trim());
        }
    }

    private void appendEmailStart(StringBuilder html) {
        html.append("<div style=\"margin:0;padding:24px;background:#f9fafb;font-family:Arial,sans-serif;\">");
        html.append("<div style=\"max-width:720px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;padding:24px;\">");
    }

    private void appendButton(StringBuilder html, String url, String label) {
        html.append("<div style=\"margin-top:24px;\">");
        html.append("<a href=\"")
                .append(escape(url))
                .append("\" style=\"display:inline-block;background:#275ed8;color:#ffffff;text-decoration:none;padding:11px 16px;border-radius:6px;font-weight:700;\">")
                .append(escape(label))
                .append("</a>");
        html.append("</div>");
    }

    private void appendEmailEnd(StringBuilder html) {
        html.append("</div></div>");
    }

    private String organizationReviewUrl(String organizationId, DateRange range) {
        return rootUrl() + "/admin/" + organizationId + "/overrides?startDate="
                + range.startDate() + "&endDate=" + range.endDate();
    }

    private String superAdminReviewUrl(DateRange range) {
        return rootUrl() + "/admin/super/overrides?startDate="
                + range.startDate() + "&endDate=" + range.endDate();
    }

    private String rootUrl() {
        String rootUrl = Optional.ofNullable(settingsService.getSiteRootUrl())
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse("http://localhost:8080");
        while (rootUrl.endsWith("/")) {
            rootUrl = rootUrl.substring(0, rootUrl.length() - 1);
        }
        return rootUrl;
    }

    private String formatTime(ScheduleEventDto event) {
        return !hasText(event.startTime()) ? "" : TIME_FORMATTER.format(java.time.LocalTime.parse(event.startTime()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(Objects.toString(value, ""));
    }

    public record DateRange(LocalDate startDate, LocalDate endDate) {
        public String displayLabel() {
            return DATE_FORMATTER.format(startDate) + " - " + DATE_FORMATTER.format(endDate);
        }
    }

    public record WeeklyMinyanReviewSendResult(
            DateRange range,
            int recipientCount,
            int sentCount,
            int failedCount,
            int skippedCount,
            boolean refreshSucceeded,
            String refreshError) {
    }

    private record OrganizationScheduleSummary(Organization organization, List<ScheduleEventDto> events) {
        int eventCount() {
            return events.size();
        }

        int daysWithSchedule() {
            return (int) events.stream().map(ScheduleEventDto::date).distinct().count();
        }

        int manualEventCount() {
            return (int) events.stream().filter(event -> EventSource.MANUAL.name().equals(event.source())).count();
        }

        int importedDayCount() {
            return (int) events.stream()
                    .filter(event -> EventSource.IMPORTED.name().equals(event.source()))
                    .map(ScheduleEventDto::date)
                    .distinct()
                    .count();
        }

        String notesLabel() {
            if (manualEventCount() == 0 && importedDayCount() == 0) {
                return "";
            }
            StringBuilder notes = new StringBuilder();
            if (manualEventCount() > 0) {
                notes.append(manualEventCount()).append(" manual");
            }
            if (importedDayCount() > 0) {
                if (!notes.isEmpty()) {
                    notes.append(", ");
                }
                notes.append(importedDayCount()).append(" imported day(s)");
            }
            return notes.toString();
        }
    }
}
