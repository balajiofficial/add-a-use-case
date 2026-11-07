package team.projectpulse.student;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.system.StatusCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student. Seed data: John (id 4) and Eric are teammates in a course section of Bingyang's course,
 * to which Bingyang and Bill are assigned. Tim owns a different course.
 */
@DisplayName("Integration tests for deactivating a student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    private static final int JOHN_ID = 4;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StudentRepository studentRepository;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String adminBingyangToken;

    String instructorBillToken;

    String adminTimToken;

    String studentJohnToken;

    String studentEricToken;


    @BeforeEach
    void setUp() throws Exception {
        this.adminBingyangToken = login("b.wei@abc.edu");
        this.instructorBillToken = login("b.gates@abc.edu");
        this.adminTimToken = login("t.cook@abc.edu");
        this.studentJohnToken = login("j.smith@abc.edu");
        this.studentEricToken = login("e.hudson@abc.edu");
    }

    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    private ResultActions deactivate(int studentId, String token) throws Exception {
        return this.mockMvc.perform(post(this.baseUrl + "/students/" + studentId + "/deactivate")
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private void assertRefusedWithNoChange(ResultActions resultActions) throws Exception {
        resultActions.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN))
                .andExpect(jsonPath("$.message").value("No permission."));
        assertThat(this.studentRepository.findById(JOHN_ID).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void testInstructorOfAnotherCourseSectionIsRefused() throws Exception { // Extension 3b
        assertRefusedWithNoChange(deactivate(JOHN_ID, this.adminTimToken));
    }

    @Test
    void testStudentHerselfIsRefused() throws Exception { // Extension 3b
        assertRefusedWithNoChange(deactivate(JOHN_ID, this.studentJohnToken));
    }

    @Test
    void testTeammateIsRefused() throws Exception { // Extension 3b
        assertRefusedWithNoChange(deactivate(JOHN_ID, this.studentEricToken));
    }

    @Test
    void testIdNamingNoStudentIsRefused() throws Exception { // Extension 3b: guards fail closed
        deactivate(999999, this.instructorBillToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

}
