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
            String refreshError = refreshSchedulesForDigest("Weekly minyan review");
            if (refreshError != null) {
                return new WeeklyMinyanReviewSendResult(range, 0, 0, 0, 0, false, refreshError);
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

    public WeeklyMinyanReviewTestSendResult sendWeeklyReviewTestEmail(TNMUser recipient, boolean refreshBeforeSend) {
        return sendWeeklyReviewTestEmail(recipient, refreshBeforeSend, LocalDate.now(settingsService.getZoneId()));
    }

    public WeeklyMinyanReviewTestSendResult sendWeeklyReviewTestEmailForOrganization(
            TNMUser recipient,
            String organizationId,
            boolean refreshBeforeSend) {
        return sendWeeklyReviewTestEmailForOrganization(
                recipient,
                organizationId,
                refreshBeforeSend,
                LocalDate.now(settingsService.getZoneId()));
    }

    public WeeklyMinyanReviewTestSendResult sendWeeklyReviewTestEmail(
            TNMUser recipient,
            boolean refreshBeforeSend,
            LocalDate referenceDate) {
        DateRange range = upcomingSundayThroughSaturday(referenceDate);

        if (recipient == null || !recipient.isAdmin()) {
            return WeeklyMinyanReviewTestSendResult.notSent(range, null, true, null,
                    "Only admin accounts can receive weekly digest test emails.");
        }

        if (!hasText(recipient.getEmail())) {
            return WeeklyMinyanReviewTestSendResult.notSent(range, null, true, null,
                    "Your account does not have an email address.");
        }

        if (refreshBeforeSend) {
            String refreshError = refreshSchedulesForDigest("Weekly minyan review test");
            if (refreshError != null) {
                return WeeklyMinyanReviewTestSendResult.notSent(range, null, false, refreshError,
                        "Weekly digest test could not refresh schedules: " + refreshError);
            }
        }

        Map<String, Organization> organizationsById = enabledOrganizationsById();

        if (recipient.isSuperAdmin()) {
            List<OrganizationScheduleSummary> allOrgSummaries = buildOrganizationSummaries(
                    organizationsById.values().stream()
                            .sorted(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER))
                            .toList(),
                    range);
            return WeeklyMinyanReviewTestSendResult.from(range, "All shuls",
                    sendSuperAdminDigest(recipient, allOrgSummaries, range, true));
        }

        String orgId = trimToNull(recipient.getOrganizationId());
        Organization organization = orgId == null ? null : organizationsById.get(orgId);
        if (organization == null) {
            return WeeklyMinyanReviewTestSendResult.notSent(range, null, true, null,
                    "Your organization is disabled or could not be found.");
        }

        OrganizationScheduleSummary summary = buildOrganizationSummaries(List.of(organization), range).getFirst();
        return WeeklyMinyanReviewTestSendResult.from(range, organization.getName(),
                sendOrganizationDigest(recipient, organization, summary.events(), range, true));
    }

    public WeeklyMinyanReviewTestSendResult sendWeeklyReviewTestEmailForOrganization(
            TNMUser recipient,
            String organizationId,
            boolean refreshBeforeSend,
            LocalDate referenceDate) {
        DateRange range = upcomingSundayThroughSaturday(referenceDate);

        if (recipient == null || !recipient.isSuperAdmin()) {
            return WeeklyMinyanReviewTestSendResult.notSent(range, null, true, null,
                    "Only super admin accounts can send a shul digest test.");
        }

        if (!hasText(recipient.getEmail())) {
            return WeeklyMinyanReviewTestSendResult.notSent(range, null, true, null,
                    "Your account does not have an email address.");
        }

        String selectedOrganizationId = trimToNull(organizationId);
        if (selectedOrganizationId == null) {
            return WeeklyMinyanReviewTestSendResult.notSent(range, null, true, null,
                    "Select a shul to send a shul digest test.");
        }

        if (refreshBeforeSend) {
            String refreshError = refreshSchedulesForDigest("Weekly minyan review shul test");
            if (refreshError != null) {
                return WeeklyMinyanReviewTestSendResult.notSent(range, null, false, refreshError,
                        "Weekly digest test could not refresh schedules: " + refreshError);
            }
        }

        Organization organization = enabledOrganizationsById().get(selectedOrganizationId);
        if (organization == null) {
            return WeeklyMinyanReviewTestSendResult.notSent(range, null, true, null,
                    "Selected shul is disabled or could not be found.");
        }

        OrganizationScheduleSummary summary = buildOrganizationSummaries(List.of(organization), range).getFirst();
        return WeeklyMinyanReviewTestSendResult.from(range, organization.getName(),
                sendOrganizationDigest(recipient, organization, summary.events(), range, true));
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
        return sendOrganizationDigest(recipient, organization, events, range, false);
    }

    private EmailSendResult sendOrganizationDigest(
            TNMUser recipient,
            Organization organization,
            List<ScheduleEventDto> events,
            DateRange range,
            boolean testMessage) {
        Map<LocalDate, List<ScheduleEventDto>> eventsByDate = groupByDate(events);
        String reviewUrl = organizationReviewUrl(organization.getId(), range);
        String subject = testPrefix(testMessage) + settingsService.getSiteName() + " weekly minyan review: "
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
                .metadata("test", Boolean.toString(testMessage))
                .build();

        return send(message, recipient);
    }

    private EmailSendResult sendSuperAdminDigest(
            TNMUser recipient,
            List<OrganizationScheduleSummary> summaries,
            DateRange range) {
        return sendSuperAdminDigest(recipient, summaries, range, false);
    }

    private EmailSendResult sendSuperAdminDigest(
            TNMUser recipient,
            List<OrganizationScheduleSummary> summaries,
            DateRange range,
            boolean testMessage) {
        String reviewUrl = superAdminReviewUrl(range);
        String subject = testPrefix(testMessage) + settingsService.getSiteName()
                + " weekly minyan review: all shuls, " + range.displayLabel();

        EmailMessage message = EmailMessage.builder()
                .to(recipient.getEmail())
                .subject(subject)
                .textBody(buildSuperAdminTextBody(summaries, range, reviewUrl))
                .htmlBody(buildSuperAdminHtmlBody(summaries, range, reviewUrl))
                .metadata("source", "weekly-minyan-review")
                .metadata("digestType", "super-admin")
                .metadata("rangeStart", range.startDate().toString())
                .metadata("rangeEnd", range.endDate().toString())
                .metadata("test", Boolean.toString(testMessage))
                .build();

        return send(message, recipient);
    }

    private String refreshSchedulesForDigest(String operationName) {
        try {
            calendarImportService.importAllEnabledOrganizations();
            materializationService.materializeAll();
            return null;
        } catch (RuntimeException e) {
            log.error("{} refresh failed; skipping digest send", operationName, e);
            return e.getMessage();
        }
    }

    private String testPrefix(boolean testMessage) {
        return testMessage ? "[TEST] " : "";
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
        EmailTheme theme = emailTheme(organization);
        int totalEvents = eventsByDate.values().stream().mapToInt(List::size).sum();
        StringBuilder html = new StringBuilder();
        appendEmailStart(html, theme, "Weekly minyan review", organization.getName(), range.displayLabel());
        html.append("<div style=\"border-left:4px solid ")
                .append(theme.accentColor())
                .append(";background:#f8fafc;border-radius:8px;padding:14px 16px;margin-bottom:20px;\">");
        html.append("<div style=\"font-size:14px;color:#374151;line-height:1.55;\">");
        html.append("<strong style=\"color:#111827;\">")
                .append(totalEvents)
                .append("</strong> minyanim scheduled across <strong style=\"color:#111827;\">")
                .append(eventsByDate.size())
                .append("</strong> day(s).</div>");
        html.append("<div style=\"margin-top:4px;font-size:13px;color:#6b7280;\">Review the coming week and update manual overrides as needed.</div>");
        html.append("</div>");

        if (eventsByDate.isEmpty()) {
            html.append("<p style=\"margin:0 0 20px;color:#4b5563;line-height:1.55;\">No minyanim are scheduled for this week.</p>");
        } else {
            for (Map.Entry<LocalDate, List<ScheduleEventDto>> day : eventsByDate.entrySet()) {
                html.append("<h2 style=\"margin:20px 0 8px;font-size:16px;color:#111827;\">")
                        .append(escape(DAY_FORMATTER.format(day.getKey())))
                        .append("</h2>");
                html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;\">");
                for (ScheduleEventDto event : day.getValue()) {
                    html.append("<tr>");
                    html.append("<td style=\"width:96px;padding:10px 12px;border-top:1px solid #e5e7eb;color:#111827;font-weight:600;white-space:nowrap;background:#ffffff;\">")
                            .append("<span style=\"display:inline-block;padding:4px 8px;border-radius:999px;background:")
                            .append(theme.accentPaleColor())
                            .append(";color:")
                            .append(theme.accentTextColor())
                            .append(";font-size:13px;\">")
                            .append(escape(formatTime(event)))
                            .append("</span>")
                            .append("</td>");
                    html.append("<td style=\"padding:10px 12px;border-top:1px solid #e5e7eb;color:#374151;background:#ffffff;\">")
                            .append(formatEventHtml(event, theme))
                            .append("</td>");
                    html.append("</tr>");
                }
                html.append("</table>");
            }
        }

        appendButton(html, reviewUrl, "Review Overrides", theme);
        appendEmailEnd(html, theme);
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
        EmailTheme theme = emailTheme(null);
        int totalEvents = summaries.stream().mapToInt(OrganizationScheduleSummary::eventCount).sum();
        StringBuilder html = new StringBuilder();
        appendEmailStart(html, theme, "Weekly minyan review", "All shuls", range.displayLabel());
        html.append("<div style=\"border-left:4px solid ")
                .append(theme.primaryColor())
                .append(";background:#f8fafc;border-radius:8px;padding:14px 16px;margin-bottom:20px;\">");
        html.append("<div style=\"font-size:14px;color:#374151;line-height:1.55;\"><strong style=\"color:#111827;\">")
                .append(summaries.size())
                .append("</strong> shuls, <strong style=\"color:#111827;\">")
                .append(totalEvents)
                .append("</strong> minyanim total.</div>");
        html.append("<div style=\"margin-top:4px;font-size:13px;color:#6b7280;\">Summary only, with the override review link below.</div>");
        html.append("</div>");

        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;\">");
        html.append("<tr>");
        html.append("<th align=\"left\" style=\"padding:10px 12px;background:#f8fafc;border-bottom:1px solid #d1d5db;color:#374151;font-size:12px;text-transform:uppercase;letter-spacing:0.04em;\">Shul</th>");
        html.append("<th align=\"right\" style=\"padding:10px 12px;background:#f8fafc;border-bottom:1px solid #d1d5db;color:#374151;font-size:12px;text-transform:uppercase;letter-spacing:0.04em;\">Minyanim</th>");
        html.append("<th align=\"right\" style=\"padding:10px 12px;background:#f8fafc;border-bottom:1px solid #d1d5db;color:#374151;font-size:12px;text-transform:uppercase;letter-spacing:0.04em;\">Days</th>");
        html.append("<th align=\"left\" style=\"padding:10px 12px;background:#f8fafc;border-bottom:1px solid #d1d5db;color:#374151;font-size:12px;text-transform:uppercase;letter-spacing:0.04em;\">Notes</th>");
        html.append("</tr>");
        for (OrganizationScheduleSummary summary : summaries) {
            String orgColor = organizationAccentColor(summary.organization(), theme.primaryColor());
            html.append("<tr>");
            html.append("<td style=\"padding:10px 12px;border-bottom:1px solid #e5e7eb;color:#111827;\">")
                    .append("<span style=\"display:inline-block;width:10px;height:10px;border-radius:999px;background:")
                    .append(orgColor)
                    .append(";margin-right:8px;vertical-align:middle;\"></span>")
                    .append(escape(summary.organization().getName()))
                    .append("</td>");
            html.append("<td align=\"right\" style=\"padding:10px 12px;border-bottom:1px solid #e5e7eb;color:#374151;font-weight:700;\">")
                    .append(summary.eventCount())
                    .append("</td>");
            html.append("<td align=\"right\" style=\"padding:10px 12px;border-bottom:1px solid #e5e7eb;color:#374151;\">")
                    .append(summary.daysWithSchedule())
                    .append("</td>");
            html.append("<td style=\"padding:10px 12px;border-bottom:1px solid #e5e7eb;color:#4b5563;\">")
                    .append(escape(summary.notesLabel()))
                    .append("</td>");
            html.append("</tr>");
        }
        html.append("</table>");

        appendButton(html, reviewUrl, "Review Overrides", theme);
        appendEmailEnd(html, theme);
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

    private String formatEventHtml(ScheduleEventDto event, EmailTheme theme) {
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
            html.append(" <span style=\"display:inline-block;margin-top:4px;padding:2px 6px;border-radius:4px;background:")
                    .append(theme.primaryPaleColor())
                    .append(";color:")
                    .append(theme.primaryDarkColor())
                    .append(";font-size:12px;\">Calendar Import</span>");
        }
        return html.toString();
    }

    private void appendTextDetail(StringBuilder text, String value) {
        if (hasText(value)) {
            text.append(" - ").append(value.trim());
        }
    }

    private void appendEmailStart(
            StringBuilder html,
            EmailTheme theme,
            String title,
            String subtitle,
            String rangeLabel) {
        html.append("<div style=\"display:none;max-height:0;overflow:hidden;color:#f5f7fb;\">")
                .append("Review the upcoming weekly minyan schedule.")
                .append("</div>");
        html.append("<div style=\"margin:0;padding:0;background:#f5f7fb;font-family:Montserrat,Arial,sans-serif;color:#1f2937;\">");
        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;background:#f5f7fb;\">");
        html.append("<tr><td style=\"padding:24px 12px;\">");
        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;max-width:760px;margin:0 auto;border-collapse:separate;border-spacing:0;\">");
        html.append("<tr><td style=\"background:")
                .append(theme.primaryColor())
                .append(";color:")
                .append(theme.primaryTextColor())
                .append(";padding:22px 24px;border-radius:8px 8px 0 0;\">");
        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;\">");
        html.append("<tr>");
        html.append("<td width=\"48\" style=\"width:48px;vertical-align:middle;\">")
                .append("<img src=\"")
                .append(escape(theme.logoUrl()))
                .append("\" width=\"40\" height=\"40\" alt=\"")
                .append(escape(theme.siteName()))
                .append("\" style=\"display:block;width:40px;height:40px;border-radius:10px;background:#ffffff;\">")
                .append("</td>");
        html.append("<td style=\"vertical-align:middle;padding-left:12px;\">")
                .append("<div style=\"font-size:18px;font-weight:700;line-height:1.2;\">")
                .append(escape(theme.siteName()))
                .append("</div>")
                .append("<div style=\"font-size:12px;line-height:1.4;opacity:0.86;\">Minyanim and zmanim</div>")
                .append("</td>");
        html.append("</tr></table>");
        html.append("<div style=\"margin-top:22px;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.08em;opacity:0.86;\">Schedule review</div>");
        html.append("<h1 style=\"margin:6px 0 6px;font-size:26px;line-height:1.2;font-weight:700;color:")
                .append(theme.primaryTextColor())
                .append(";\">")
                .append(escape(title))
                .append("</h1>");
        html.append("<div style=\"font-size:14px;line-height:1.5;opacity:0.92;\">")
                .append(escape(subtitle))
                .append(" - ")
                .append(escape(rangeLabel))
                .append("</div>");
        html.append("</td></tr>");
        html.append("<tr><td style=\"padding:24px;background:#ffffff;border:1px solid #e5e7eb;border-top:0;border-radius:0 0 8px 8px;\">");
    }

    private void appendButton(StringBuilder html, String url, String label, EmailTheme theme) {
        html.append("<div style=\"margin-top:24px;\">");
        html.append("<a href=\"")
                .append(escape(url))
                .append("\" style=\"display:inline-block;background:")
                .append(theme.primaryColor())
                .append(";color:")
                .append(theme.primaryTextColor())
                .append(";text-decoration:none;padding:12px 18px;border-radius:999px;font-weight:700;\">")
                .append(escape(label))
                .append("</a>");
        html.append("</div>");
    }

    private void appendEmailEnd(StringBuilder html, EmailTheme theme) {
        if (hasText(theme.supportEmail())) {
            html.append("<p style=\"margin:22px 0 0;font-size:12px;line-height:1.5;color:#6b7280;\">")
                    .append("Questions? Contact <a href=\"mailto:")
                    .append(escape(theme.supportEmail()))
                    .append("\" style=\"color:")
                    .append(theme.primaryDarkColor())
                    .append(";text-decoration:none;font-weight:600;\">")
                    .append(escape(theme.supportEmail()))
                    .append("</a>.</p>");
        }
        html.append("</td></tr></table>");
        html.append("</td></tr></table>");
        html.append("</div>");
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

    private EmailTheme emailTheme(Organization organization) {
        String siteName = Optional.ofNullable(settingsService.getSiteName())
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse("Minyanim");
        String primaryColor = normalizeHexColor(settingsService.getAppColor(), "#275ED8");
        String accentColor = organizationAccentColor(organization, primaryColor);
        return new EmailTheme(
                siteName,
                primaryColor,
                mixHexColor(primaryColor, "#000000", 0.22),
                mixHexColor(primaryColor, "#FFFFFF", 0.88),
                contrastTextColor(primaryColor),
                accentColor,
                mixHexColor(accentColor, "#FFFFFF", 0.88),
                mixHexColor(accentColor, "#000000", 0.48),
                rootUrl() + "/assets/icons/favicon.png",
                Optional.ofNullable(settingsService.getSupportEmail())
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .orElse(null));
    }

    private String organizationAccentColor(Organization organization, String fallbackColor) {
        if (organization == null) {
            return fallbackColor;
        }
        return normalizeHexColor(organization.getOrgColor(), fallbackColor);
    }

    private String normalizeHexColor(String value, String fallbackColor) {
        String normalizedFallback = fallbackColor == null ? "#275ED8" : fallbackColor;
        if (value == null) {
            return normalizedFallback;
        }

        String trimmed = value.trim();
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }

        if (trimmed.matches("#[0-9a-fA-F]{3}")) {
            return ("#"
                    + trimmed.charAt(1) + trimmed.charAt(1)
                    + trimmed.charAt(2) + trimmed.charAt(2)
                    + trimmed.charAt(3) + trimmed.charAt(3)).toUpperCase(Locale.ROOT);
        }

        if (trimmed.matches("#[0-9a-fA-F]{6}")) {
            return trimmed.toUpperCase(Locale.ROOT);
        }

        return normalizedFallback;
    }

    private String mixHexColor(String color, String targetColor, double targetWeight) {
        int[] source = rgb(color);
        int[] target = rgb(targetColor);
        double clampedWeight = Math.max(0, Math.min(1, targetWeight));
        int red = (int) Math.round(source[0] * (1 - clampedWeight) + target[0] * clampedWeight);
        int green = (int) Math.round(source[1] * (1 - clampedWeight) + target[1] * clampedWeight);
        int blue = (int) Math.round(source[2] * (1 - clampedWeight) + target[2] * clampedWeight);
        return String.format("#%02X%02X%02X", red, green, blue);
    }

    private int[] rgb(String color) {
        String hex = normalizeHexColor(color, "#275ED8").substring(1);
        return new int[] {
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    private String contrastTextColor(String backgroundColor) {
        int[] rgb = rgb(backgroundColor);
        double red = colorChannel(rgb[0]);
        double green = colorChannel(rgb[1]);
        double blue = colorChannel(rgb[2]);
        double luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
        return luminance > 0.55 ? "#111827" : "#FFFFFF";
    }

    private double colorChannel(int value) {
        double channel = value / 255.0;
        return channel <= 0.03928
                ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
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

    public record WeeklyMinyanReviewTestSendResult(
            DateRange range,
            String digestLabel,
            boolean sent,
            boolean refreshSucceeded,
            String refreshError,
            String message) {
        public boolean success() {
            return sent && refreshSucceeded;
        }

        static WeeklyMinyanReviewTestSendResult from(DateRange range, String digestLabel, EmailSendResult result) {
            return new WeeklyMinyanReviewTestSendResult(
                    range,
                    digestLabel,
                    result.isSuccess(),
                    true,
                    null,
                    result.getMessage());
        }

        static WeeklyMinyanReviewTestSendResult notSent(
                DateRange range,
                String digestLabel,
                boolean refreshSucceeded,
                String refreshError,
                String message) {
            return new WeeklyMinyanReviewTestSendResult(range, digestLabel, false, refreshSucceeded, refreshError, message);
        }
    }

    private record EmailTheme(
            String siteName,
            String primaryColor,
            String primaryDarkColor,
            String primaryPaleColor,
            String primaryTextColor,
            String accentColor,
            String accentPaleColor,
            String accentTextColor,
            String logoUrl,
            String supportEmail) {
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
