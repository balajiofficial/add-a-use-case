package team.projectpulse.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import team.projectpulse.user.UserRepository;

/**
 * Refuses a token whose account is deactivated or no longer exists, so access ends with the account's next request
 * rather than when the token expires (FR-SEC-active-account).
 *
 * <p>It runs on the decoder because a failed validation already reaches the client as the same 401 as an expired
 * token: {@code InvalidBearerTokenException}, then {@code ExceptionHandlerAdvice}, then the SPA's redirect to login.
 * It reads the account's present state, so a token issued before a deactivation works again if the account is
 * reactivated before the token expires.
 */
@Component
public class ActiveAccountJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ACCOUNT_NOT_ACTIVE = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "The token's account is not active.", null);

    private final UserRepository userRepository;


    public ActiveAccountJwtValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        // A JSON number decodes as a Long. JwtProvider always writes this claim, so anything else is refused.
        if (jwt.getClaim("userId") instanceof Number userId
                && this.userRepository.existsByIdAndEnabledTrue(userId.intValue())) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(ACCOUNT_NOT_ACTIVE);
    }

}
