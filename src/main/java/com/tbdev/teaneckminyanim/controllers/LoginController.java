package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkAuthenticationException;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkService;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Date;

@Controller
@RequiredArgsConstructor
public class LoginController {
    
    private final ApplicationSettingsService settingsService;
    private final MagicLinkService magicLinkService;
    private final TNMUserService userService;
    private final UserCredentialRepository userCredentialRepository;
    
    @ModelAttribute("siteName")
    public String siteName() {
        return settingsService.getSiteName();
    }
    
    @ModelAttribute("supportEmail")
    public String supportEmail() {
        return settingsService.getSupportEmail();
    }

    @ModelAttribute("recaptchaEnabled")
    public boolean recaptchaEnabled() {
        return settingsService.isRecaptchaEnabled();
    }

    @ModelAttribute("recaptchaSiteKey")
    public String recaptchaSiteKey() {
        return settingsService.getRecaptchaSiteKey();
    }
    
    @GetMapping("/admin/login")
    public ModelAndView login(@RequestParam(value = "error",required = false) String error, @RequestParam(value = "logout",	required = false) boolean logout) {
        System.out.println("LoginController.login() called");
        ModelAndView mv = new ModelAndView();
        mv.setViewName("admin/login");

        // Add current time for navbar display
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d, yyyy h:mm a");
        Date datenow = new Date();
        String timenow = dateFormat.format(datenow);
        mv.getModel().put("timenow", timenow);

        if (logout) {
            System.out.println("Logging out...");
            SecurityContextHolder.getContext().setAuthentication(null);
            System.out.println("Logout Successful");
            mv.getModel().put("logout", true);
        }

        if (error != null && !error.isEmpty()) {
            mv.getModel().put("error", error);
        }

        return mv;
    }

    @PostMapping("/admin/login/magic-link")
    public ModelAndView requestMagicLink(@RequestParam(value = "identifier", required = false) String identifier,
                                         @RequestParam(value = "email", required = false) String email,
                                         HttpServletRequest request) {
        ModelAndView mv = login(null, false);
        MagicLinkService.MagicLinkRequestResult result = magicLinkService.requestLoginLink(
                firstPresent(identifier, email),
                request);
        mv.addObject("magicLinkMessage", result.message());
        return mv;
    }

    @PostMapping(value = "/admin/login/magic-link/request",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public MagicLinkResponse requestMagicLinkJson(@RequestBody LoginIdentifierRequest loginRequest,
                                                  HttpServletRequest request) {
        MagicLinkService.MagicLinkRequestResult result = magicLinkService.requestLoginLink(
                loginRequest == null ? null : loginRequest.identifier(),
                request);
        return new MagicLinkResponse(result.message());
    }

    @PostMapping(value = "/admin/login/auth-methods",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public LoginMethodsResponse loginMethods(@RequestBody LoginIdentifierRequest loginRequest) {
        String identifier = loginRequest == null ? null : loginRequest.identifier();
        TNMUser user = userService.findByIdentifier(identifier);
        if (user == null || user.isDisabled()) {
            return new LoginMethodsResponse(false, true);
        }
        List<?> credentials = userCredentialRepository.findByUserId(
                new Bytes(user.getId().getBytes(StandardCharsets.UTF_8)));
        boolean hasPasskey = credentials != null && !credentials.isEmpty();
        return new LoginMethodsResponse(hasPasskey, true);
    }

    @GetMapping("/admin/login/magic-link/verify")
    public ModelAndView verifyMagicLink(@RequestParam(value = "token", required = false) String token,
                                        HttpServletRequest request) {
        try {
            TNMUser user = magicLinkService.consumeLoginToken(token);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user.getUsername(),
                    null,
                    List.of(new SimpleGrantedAuthority(user.role().getName())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());
            return new ModelAndView("redirect:/admin");
        } catch (MagicLinkAuthenticationException e) {
            return login(e.getMessage(), false);
        }
    }

    @GetMapping("/login")
    public ModelAndView loginShortcut(@RequestParam(value = "error",required = false) String error, @RequestParam(value = "logout",	required = false) boolean logout) {
        return login(error, logout);
    }

    private String firstPresent(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    public record LoginIdentifierRequest(String identifier) {
    }

    public record LoginMethodsResponse(boolean passkeyAvailable, boolean magicLinkAvailable) {
    }

    public record MagicLinkResponse(String message) {
    }
}
