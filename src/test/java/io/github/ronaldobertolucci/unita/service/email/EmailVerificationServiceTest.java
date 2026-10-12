package io.github.ronaldobertolucci.unita.service.email;

import io.github.ronaldobertolucci.unita.model.security.EmailVerificationToken;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.EmailVerificationTokenRepository;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .enabled(true)
                .build();
    }

    // -------------------------------------------------------------------------
    // sendVerificationEmail
    // -------------------------------------------------------------------------

    @Test
    void sendVerificationEmail_ShouldSaveTokenAndSendEmail() {
        when(tokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        emailVerificationService.sendVerificationEmail(user);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository, times(1)).save(tokenCaptor.capture());

        EmailVerificationToken saved = tokenCaptor.getValue();
        assertNotNull(saved.getToken());
        assertFalse(saved.isUsed());
        assertEquals(user, saved.getUser());

        verify(emailService, times(1)).sendHtmlEmailSynchronously(
                eq("john@example.com"),
                anyString(),
                anyString()
        );
    }

    @Test
    void sendVerificationEmail_ShouldGenerateUniqueTokens() {
        when(tokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        emailVerificationService.sendVerificationEmail(user);
        emailVerificationService.sendVerificationEmail(user);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository, times(2)).save(tokenCaptor.capture());

        String firstToken = tokenCaptor.getAllValues().get(0).getToken();
        String secondToken = tokenCaptor.getAllValues().get(1).getToken();
        assertNotEquals(firstToken, secondToken);
    }

    // -------------------------------------------------------------------------
    // verifyEmail
    // -------------------------------------------------------------------------

    @Test
    void verifyEmail_WhenTokenIsValid_ShouldConfirmWithoutEnablingUser() {
        user.setEnabled(false);
        EmailVerificationToken token = buildToken(false, LocalDateTime.now().plusHours(24));
        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        emailVerificationService.verifyEmail("valid-token");

        assertTrue(token.isUsed());
        assertFalse(token.getUser().isEnabled());
        verify(userRepository).confirmEmail(1L);
    }

    @Test
    void verifyEmail_WhenTokenNotFound_ShouldThrowIllegalArgumentException() {
        when(tokenRepository.findByToken("unknown-token")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> emailVerificationService.verifyEmail("unknown-token"));

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void verifyEmail_WhenTokenAlreadyUsed_ShouldThrowIllegalStateException() {
        EmailVerificationToken token = buildToken(true, LocalDateTime.now().plusHours(24));
        when(tokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

        assertThrows(IllegalStateException.class,
                () -> emailVerificationService.verifyEmail("used-token"));

        assertFalse(token.getUser().getEmailVerified());
        verify(userRepository, never()).confirmEmail(any());
    }

    @Test
    void verifyEmail_WhenTokenIsExpired_ShouldThrowIllegalStateException() {
        EmailVerificationToken token = buildToken(false, LocalDateTime.now().minusHours(1));
        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThrows(IllegalStateException.class,
                () -> emailVerificationService.verifyEmail("expired-token"));

        assertFalse(token.getUser().getEmailVerified());
        verify(userRepository, never()).confirmEmail(any());
    }

    // -------------------------------------------------------------------------
    // resendVerificationEmail
    // -------------------------------------------------------------------------

    @Test
    void resendVerificationEmail_WhenAccountNotVerified_ShouldSendNewEmail() {
        when(userRepository.findByEmailWithRoles("john@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        emailVerificationService.resendVerificationEmail("john@example.com");

        verify(tokenRepository, times(1)).save(any(EmailVerificationToken.class));
        verify(emailService, times(1)).sendHtmlEmailSynchronously(
                eq("john@example.com"),
                anyString(),
                anyString()
        );
    }

    @Test
    void resendVerificationEmail_WhenUserNotFound_ShouldReturnNormally() {
        when(userRepository.findByEmailWithRoles("unknown@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> emailVerificationService.resendVerificationEmail("unknown@example.com"));

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendHtmlEmailSynchronously(any(), any(), any());
    }

    @Test
    void resendVerificationEmail_WhenAccountAlreadyVerified_ShouldReturnNormally() {
        user.setEmailVerified(true);
        when(userRepository.findByEmailWithRoles("john@example.com")).thenReturn(Optional.of(user));

        assertDoesNotThrow(
                () -> emailVerificationService.resendVerificationEmail("john@example.com"));

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendHtmlEmailSynchronously(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    @Test
    void resendVerificationEmail_WhenDisabled_ShouldNotSend() {
        user.setEnabled(false);
        when(userRepository.findByEmailWithRoles(user.getEmail())).thenReturn(Optional.of(user));
        assertDoesNotThrow(() -> emailVerificationService.resendVerificationEmail(user.getEmail()));
        verifyNoInteractions(tokenRepository, emailService);
    }

    @Test
    void resendVerificationEmail_WhenDeliveryFails_ShouldPropagate() {
        when(userRepository.findByEmailWithRoles(user.getEmail())).thenReturn(Optional.of(user));
        doThrow(new org.springframework.mail.MailSendException("unavailable"))
                .when(emailService).sendHtmlEmailSynchronously(anyString(), anyString(), anyString());
        assertThrows(org.springframework.mail.MailSendException.class,
                () -> emailVerificationService.resendVerificationEmail(user.getEmail()));
    }

    private EmailVerificationToken buildToken(boolean used, LocalDateTime expiryDate) {
        return EmailVerificationToken.builder()
                .token(used ? "used-token" : "valid-token")
                .user(user)
                .used(used)
                .expiryDate(expiryDate)
                .build();
    }
}