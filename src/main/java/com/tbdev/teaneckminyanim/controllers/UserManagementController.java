package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.UserManagementService;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkService;
import com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

@Controller
@RequiredArgsConstructor
public class UserManagementController {
    private final UserManagementService management;
    private final MagicLinkService magicLinks;
    private final PasskeyCredentialService passkeys;

    @PostMapping("/admin/users/create")
    public String create(@RequestParam String username, @RequestParam String email,
                         @RequestParam String access,
                         @RequestParam(required = false) Set<String> organizationIds,
                         HttpServletRequest request,
                         RedirectAttributes redirect) {
        management.requireSuperAdmin();
        try {
            TNMUser user = management.create(username, email, access, organizationIds);
            boolean sent = magicLinks.sendManagedWelcomeLink(user, request);
            redirect.addFlashAttribute(sent ? "successmessage" : "errormessage", sent
                    ? "Created " + user.getUsername() + " and sent their welcome email."
                    : "Created " + user.getUsername() + ", but the welcome email could not be sent. Use Resend welcome email after checking Email Settings.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errormessage", e.getMessage());
            redirect.addFlashAttribute("draftUsername", username);
            redirect.addFlashAttribute("draftEmail", email);
            redirect.addFlashAttribute("draftAccess", access);
            redirect.addFlashAttribute("draftOrganizationIds",
                    organizationIds == null ? Set.of() : organizationIds);
            redirect.addFlashAttribute("openCreateUser", true);
        }
        return "redirect:/admin/accounts";
    }

    @PostMapping("/admin/users/update")
    public String update(@RequestParam String id, @RequestParam String email, @RequestParam String access,
                         @RequestParam(required = false) Set<String> organizationIds,
                         @RequestParam(defaultValue = "false") boolean returnToAccount,
                         HttpServletRequest request, RedirectAttributes redirect) {
        management.requireSuperAdmin();
        try {
            UserManagementService.UserUpdateResult result = management.update(
                    id, email, access, organizationIds);
            if (result.emailChanged()) {
                boolean sent = magicLinks.sendManagedWelcomeLink(result.user(), request);
                redirect.addFlashAttribute(sent
                                ? (returnToAccount ? "successMessage" : "successmessage")
                                : (returnToAccount ? "mainErrorMessage" : "errormessage"), sent
                        ? "User permissions saved and a welcome email was sent to the new address."
                        : "User permissions and email were saved, but the welcome email could not be sent to the new address.");
            } else {
                redirect.addFlashAttribute(returnToAccount ? "successMessage" : "successmessage",
                        "User permissions saved.");
            }
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute(returnToAccount ? "mainErrorMessage" : "errormessage", e.getMessage());
            redirect.addFlashAttribute("editUserId", id);
        }
        return returnToAccount ? "redirect:/admin/account?id=" + id : "redirect:/admin/accounts";
    }

    @PostMapping("/admin/users/send-link")
    public String sendLink(@RequestParam String id, HttpServletRequest request, RedirectAttributes redirect) {
        TNMUser target = management.requireTarget(id);
        boolean sent = magicLinks.sendManagedLoginLink(target, request);
        redirect.addFlashAttribute(sent ? "successmessage" : "errormessage", sent
                ? "Sign-in link sent. It expires in 15 minutes."
                : "Sign-in link could not be sent. Check account status and Email Settings.");
        return "redirect:/admin/accounts";
    }

    @PostMapping("/admin/users/send-welcome")
    public String sendWelcome(@RequestParam String id, HttpServletRequest request, RedirectAttributes redirect) {
        TNMUser target = management.requireTarget(id);
        boolean sent = magicLinks.sendManagedWelcomeLink(target, request);
        redirect.addFlashAttribute(sent ? "successmessage" : "errormessage", sent
                ? "Welcome email sent with role, shul access, and a secure activation link."
                : "Welcome email could not be sent. Check account status and Email Settings.");
        return "redirect:/admin/accounts";
    }

    @PostMapping("/admin/users/delete")
    public String delete(@RequestParam String id, RedirectAttributes redirect) {
        try {
            String username = management.delete(id);
            redirect.addFlashAttribute("successmessage", "Deleted " + username + ".");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errormessage", e.getMessage());
            redirect.addFlashAttribute("editUserId", id);
        }
        return "redirect:/admin/accounts";
    }

    @PostMapping("/admin/users/status")
    public String updateStatus(@RequestParam String id, @RequestParam boolean enabled, RedirectAttributes redirect) {
        try {
            management.setEnabled(id, enabled);
            redirect.addFlashAttribute("successmessage", enabled ? "Account enabled." : "Account disabled.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errormessage", e.getMessage());
        }
        redirect.addFlashAttribute("editUserId", id);
        return "redirect:/admin/accounts";
    }

    @PostMapping("/admin/users/revoke-passkeys")
    public String revoke(@RequestParam String id, RedirectAttributes redirect) {
        TNMUser actor = management.requireSuperAdmin();
        if (actor.getId().equals(id)) {
            redirect.addFlashAttribute("errormessage", "Manage your own passkeys from your account security page.");
        } else {
            passkeys.revokeAll(management.requireTarget(id));
            redirect.addFlashAttribute("successmessage", "Passkeys revoked. Send a sign-in link to help the user recover access.");
        }
        return "redirect:/admin/accounts";
    }
}
