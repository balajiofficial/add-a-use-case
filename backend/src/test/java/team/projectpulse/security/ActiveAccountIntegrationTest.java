package team.projectpulse.security;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.system.StatusCode;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-SEC-active-account: a bearer token is honored only while its account is active, and login is refused while it
 * is not. The accounts are deactivated directly on the entity, because this check does not care how they got there.
 */
@DisplayName("Integration tests for the active-account check on bearer tokens")
class ActiveAccountIntegrationTest extends AbstractIntegrationTest {

    private static final String STUDENT_JOHN = "j.smith@abc.edu";

    private static final String INSTRUCTOR_BILL = "b.gates@abc.edu";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtEncoder jwtEncoder;

    @Value("${api.endpoint.base-url}")
    String baseUrl;


    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    private PeerEvaluationUser user(String email) {
        return this.userRepository.findByEmail(email).orElseThrow();
    }

    private void deactivate(String email) {
        PeerEvaluationUser user = user(email);
        user.setEnabled(false);
        this.userRepository.save(user);
    }

    private ResultActions getOwnRecord(String collection, String email, String token) throws Exception {
        return this.mockMvc.perform(get(this.baseUrl + "/" + collection + "/" + user(email).getId())
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    /**
     * A token signed by this server, so it passes the signature check and reaches the claim check.
     */
    private String tokenWithClaims(JwtClaimsSet.Builder claims) {
        Instant now = Instant.now();
        JwtClaimsSet claimsSet = claims.issuer("self").issuedAt(now).expiresAt(now.plus(2, ChronoUnit.HOURS))
                .claim("authorities", "ROLE_admin ROLE_instructor")
                .build();
        return "Bearer " + this.jwtEncoder.encode(JwtEncoderParameters.from(claimsSet)).getTokenValue();
    }

    private void assertTokenRefused(ResultActions resultActions) throws Exception {
        resultActions.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("The access token provided is expired, revoked, malformed, or invalid for other reasons."));
    }

    @Test
    void testActiveStudentTokenIsAccepted() throws Exception {
        getOwnRecord("students", STUDENT_JOHN, login(STUDENT_JOHN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.email").value(STUDENT_JOHN));
    }

    @Test
    void testActiveInstructorTokenIsAccepted() throws Exception {
        getOwnRecord("instructors", INSTRUCTOR_BILL, login(INSTRUCTOR_BILL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true));
    }

    @Test
    void testTokenIssuedBeforeStudentWasDeactivatedIsRefused() throws Exception {
        String token = login(STUDENT_JOHN);

        deactivate(STUDENT_JOHN);

        assertTokenRefused(getOwnRecord("students", STUDENT_JOHN, token));
    }

    @Test
    void testTokenIssuedBeforeInstructorWasDeactivatedIsRefused() throws Exception {
        String token = login(INSTRUCTOR_BILL);

        deactivate(INSTRUCTOR_BILL);

        assertTokenRefused(getOwnRecord("instructors", INSTRUCTOR_BILL, token));
    }

    @Test
    void testTokenIsAcceptedAgainOnceTheAccountIsActiveAgain() throws Exception {
        String token = login(STUDENT_JOHN);
        deactivate(STUDENT_JOHN);
        PeerEvaluationUser john = user(STUDENT_JOHN);
        john.setEnabled(true);
        this.userRepository.save(john);

        getOwnRecord("students", STUDENT_JOHN, token).andExpect(status().isOk());
    }

    @Test
    void testTokenOfAnAccountThatNoLongerExistsIsRefused() throws Exception {
        String token = tokenWithClaims(JwtClaimsSet.builder().subject("gone@abc.edu").claim("userId", 999999));

        assertTokenRefused(getOwnRecord("students", STUDENT_JOHN, token));
    }

    @Test
    void testTokenWithoutAUserIdClaimIsRefused() throws Exception {
        String token = tokenWithClaims(JwtClaimsSet.builder().subject(STUDENT_JOHN));

        assertTokenRefused(getOwnRecord("students", STUDENT_JOHN, token));
    }

    @Test
    void testExpiredTokenOfAnActiveAccountIsStillRefused() throws Exception {
        Instant longAgo = Instant.now().minus(3, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("self").issuedAt(longAgo).expiresAt(longAgo.plus(2, ChronoUnit.HOURS))
                .subject(STUDENT_JOHN)
                .claim("userId", user(STUDENT_JOHN).getId())
                .claim("authorities", "ROLE_student")
                .build();
        String token = "Bearer " + this.jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        assertTokenRefused(getOwnRecord("students", STUDENT_JOHN, token));
    }

    @Test
    void testDeactivatedStudentCannotLogIn() throws Exception {
        deactivate(STUDENT_JOHN);

        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(STUDENT_JOHN, "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("User account is abnormal."));
    }

}
