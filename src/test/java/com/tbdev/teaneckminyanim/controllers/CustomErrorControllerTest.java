package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.VersionService;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomErrorControllerTest {

    private ApplicationSettingsService settingsService;
    private VersionService versionService;
    private CustomErrorController controller;

    @BeforeEach
    void setUp() {
        settingsService = mock(ApplicationSettingsService.class);
        versionService = mock(VersionService.class);
        when(settingsService.getSiteName()).thenReturn("Teaneck Minyanim");
        when(settingsService.getSupportEmail()).thenReturn("support@example.com");
        when(settingsService.getAppleSmartAppBannerContent()).thenReturn(null);
        when(versionService.getVersion()).thenReturn("test-version");

        controller = new CustomErrorController(settingsService, versionService);
    }

    @Test
    void browser404RendersThemedPage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/does-not-exist");

        MockHttpServletResponse response = new MockHttpServletResponse();
        Object result = controller.handleError(request, response);

        assertInstanceOf(ModelAndView.class, result);
        ModelAndView mv = (ModelAndView) result;
        assertEquals("error", mv.getViewName());
        assertEquals(404, response.getStatus());
        assertEquals("This page didn't make the minyan.", mv.getModel().get("errorTitle"));
        assertEquals("/assets/icons/errors/error-404-bulletin.svg", mv.getModel().get("errorIconPath"));
        assertEquals("Today's Minyanim", mv.getModel().get("secondaryCtaLabel"));
    }

    @Test
    void browser500RendersBorochuCopy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/test/errors/500");

        MockHttpServletResponse response = new MockHttpServletResponse();
        Object result = controller.handleError(request, response);

        assertInstanceOf(ModelAndView.class, result);
        ModelAndView mv = (ModelAndView) result;
        assertEquals(500, response.getStatus());
        assertEquals("You missed borochu.", mv.getModel().get("errorTitle"));
        assertEquals("Try Again", mv.getModel().get("primaryCtaLabel"));
        assertEquals("/test/errors/500", mv.getModel().get("primaryCtaHref"));
    }

    @Test
    void api404ReturnsJsonPayload() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/not-a-route");

        MockHttpServletResponse response = new MockHttpServletResponse();
        Object result = controller.handleError(request, response);

        assertInstanceOf(ResponseEntity.class, result);
        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<Void>> entity = (ResponseEntity<ApiResponse<Void>>) result;
        assertEquals(404, entity.getStatusCode().value());
        assertNotNull(entity.getBody());
        assertEquals("NOT_FOUND", entity.getBody().error().code());
        assertEquals("Resource not found", entity.getBody().error().message());
    }

    @Test
    void api429IncludesRetryAfterHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 429);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/rate-limited");

        MockHttpServletResponse response = new MockHttpServletResponse();
        Object result = controller.handleError(request, response);

        assertInstanceOf(ResponseEntity.class, result);
        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<Void>> entity = (ResponseEntity<ApiResponse<Void>>) result;
        assertEquals(429, entity.getStatusCode().value());
        assertEquals("60", entity.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertNotNull(entity.getBody());
        assertEquals("RATE_LIMITED", entity.getBody().error().code());
    }
}
