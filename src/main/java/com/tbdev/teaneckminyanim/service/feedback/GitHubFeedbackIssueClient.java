package com.tbdev.teaneckminyanim.service.feedback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubFeedbackIssueClient implements FeedbackIssueClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String GITHUB_API_VERSION = "2022-11-28";

    private final ApplicationSettingsService settingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public CreatedGitHubIssue createIssue(FeedbackIssueRequest request)
            throws FeedbackConfigurationException, FeedbackIssueCreationException {
        GitHubSettings settings = loadSettings();

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/"
                            + encodePath(settings.owner())
                            + "/"
                            + encodePath(settings.repo())
                            + "/issues"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + settings.token())
                    .header("Content-Type", "application/json")
                    .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(issueJson(request)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FeedbackIssueCreationException(
                        "GitHub issue create returned HTTP " + response.statusCode() + ": "
                                + summarizeGitHubError(response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            int number = root.path("number").asInt();
            String url = root.path("html_url").asText(null);
            if (number <= 0 || url == null || url.isBlank()) {
                throw new FeedbackIssueCreationException("GitHub issue response did not include an issue number and URL.");
            }

            return new CreatedGitHubIssue(number, url);
        } catch (IOException e) {
            throw new FeedbackIssueCreationException("GitHub issue create failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FeedbackIssueCreationException("GitHub issue create was interrupted.", e);
        }
    }

    private GitHubSettings loadSettings() throws FeedbackConfigurationException {
        String owner = normalize(settingsService.getFeedbackGithubOwner());
        String repo = normalize(settingsService.getFeedbackGithubRepo());
        String token = normalize(settingsService.getFeedbackGithubToken());

        if (owner.isBlank()) {
            throw new FeedbackConfigurationException("Feedback GitHub owner is not configured.");
        }
        if (repo.isBlank()) {
            throw new FeedbackConfigurationException("Feedback GitHub repository is not configured.");
        }
        if (token.isBlank()) {
            throw new FeedbackConfigurationException("Feedback GitHub token is not configured.");
        }

        return new GitHubSettings(owner, repo, token);
    }

    private String issueJson(FeedbackIssueRequest request) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", request.title());
        payload.put("body", request.body());
        List<String> labels = request.labels();
        if (labels != null && !labels.isEmpty()) {
            payload.put("labels", labels);
        }
        return objectMapper.writeValueAsString(payload);
    }

    private String summarizeGitHubError(String body) {
        if (body == null || body.isBlank()) {
            return "empty response body";
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
        } catch (IOException e) {
            log.debug("Could not parse GitHub error response", e);
        }

        return body.length() <= 300 ? body : body.substring(0, 300);
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record GitHubSettings(String owner, String repo, String token) {
    }
}
