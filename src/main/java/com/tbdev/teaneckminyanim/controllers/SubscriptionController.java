package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaVerificationException;
import com.tbdev.teaneckminyanim.service.subscription.SendySubscriptionService;
import com.tbdev.teaneckminyanim.service.subscription.SubscriptionException;
import com.tbdev.teaneckminyanim.service.subscription.SubscriptionRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SubscriptionController {
    private final RecaptchaService recaptchaService;
    private final SendySubscriptionService subscriptionService;

    @PostMapping("/subscribe")
    public RedirectView subscribe(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "hp", required = false) String honeypot,
            @RequestParam(value = "list", required = false) String list,
            @RequestParam(value = "subform", required = false) String subform,
            @RequestParam(value = "recaptchaToken", required = false) String recaptchaToken,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            recaptchaService.verify(recaptchaToken, request);
            subscriptionService.subscribe(new SubscriptionRequest(name, email, honeypot, list, subform));
            return new RedirectView("/subscription", true);
        } catch (RecaptchaVerificationException e) {
            log.warn("Newsletter subscription rejected by reCAPTCHA: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("subscriptionError", "Please try subscribing again.");
            return new RedirectView("/subscription", true);
        } catch (SubscriptionException e) {
            log.warn("Newsletter subscription failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("subscriptionError", e.getMessage());
            return new RedirectView("/subscription", true);
        }
    }
}
