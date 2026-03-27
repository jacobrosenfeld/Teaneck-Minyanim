package com.tbdev.teaneckminyanim.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class ErrorPreviewControllerTest {

    private final ErrorPreviewController controller = new ErrorPreviewController();

    @Test
    void preview404ThrowsNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.previewError(404));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void preview500ThrowsRuntimeException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> controller.previewError(500));
        assertEquals("Previewing 500 error page", ex.getMessage());
    }
}
