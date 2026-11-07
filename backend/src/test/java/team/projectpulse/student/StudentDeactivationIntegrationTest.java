package team.projectpulse.student;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.activity.ActivityRepository;
import team.projectpulse.evaluation.PeerEvaluationRepository;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.StatusCode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    ActivityRepository activityRepository;

    @Autowired
    PeerEvaluationRepository peerEvaluationRepository;

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

    private Student john() {
        return this.studentRepository.findById(JOHN_ID).orElseThrow();
    }

    private long johnsActivities() {
        return this.activityRepository.findAll().stream()
                .filter(activity -> activity.getStudent().getId().equals(JOHN_ID)).count();
    }

    private long johnsPeerEvaluations() {
        return this.peerEvaluationRepository.findAll().stream()
                .filter(evaluation -> evaluation.getEvaluator().getId().equals(JOHN_ID) || evaluation.getEvaluatee().getId().equals(JOHN_ID)).count();
    }

    @Test
    void testAssignedInstructorDeactivatesStudent() throws Exception { // Main success scenario
        Integer teamId = john().getTeam().getTeamId();
        long activities = johnsActivities();
        long peerEvaluations = johnsPeerEvaluations();
        assertThat(activities).isPositive();
        assertThat(peerEvaluations).isPositive();

        deactivate(JOHN_ID, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.id").value(JOHN_ID))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.teamId").value(teamId));

        assertThat(john().isEnabled()).isFalse();
        assertThat(john().getTeam().getTeamId()).isEqualTo(teamId); // BR-deactivated-student-team
        assertThat(johnsActivities()).isEqualTo(activities);
        assertThat(johnsPeerEvaluations()).isEqualTo(peerEvaluations);
    }

    @Test
    void testOwningCourseAdminNotAssignedToTheCourseSectionDeactivatesStudent() throws Exception { // Extension 3b, the permitted side
        Section section = john().getSection();
        Instructor bingyang = section.getCourse().getCourseAdmin();
        section.getInstructors().remove(bingyang);
        assertThat(section.getInstructors()).doesNotContain(bingyang);

        deactivate(JOHN_ID, this.adminBingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false));

        assertThat(john().isEnabled()).isFalse();
    }

    @Test
    void testStudentAlreadyDeactivatedIsRefusedWithNoChange() throws Exception { // Extension 6a
        deactivate(JOHN_ID, this.instructorBillToken).andExpect(status().isOk());

        deactivate(JOHN_ID, this.adminBingyangToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT))
                .andExpect(jsonPath("$.message").value("This student is already deactivated."));

        assertThat(john().isEnabled()).isFalse();
    }

    @Test
    void testDeactivatedStudentLosesAccessOnHerNextRequest() throws Exception { // POST-2
        deactivate(JOHN_ID, this.instructorBillToken).andExpect(status().isOk());

        this.mockMvc.perform(get(this.baseUrl + "/students/" + JOHN_ID).header(HttpHeaders.AUTHORIZATION, this.studentJohnToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    private ResultActions editJohn(boolean enabled, String token) throws Exception {
        String body = this.jsonMapper.writeValueAsString(Map.of(
                "username", "j.smith@abc.edu", "firstName", "John (edited)", "lastName", "Smith", "email", "j.smith@abc.edu", "enabled", enabled));
        return this.mockMvc.perform(put(this.baseUrl + "/students/" + JOHN_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body).header(HttpHeaders.AUTHORIZATION, token));
    }

    @Test
    void testInstructorCannotDeactivateByEditing() throws Exception {
        editJohn(false, this.instructorBillToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.INVALID_ARGUMENT))
                .andExpect(jsonPath("$.message").value("A student cannot be deactivated by editing. Use Deactivate on the student's details page."));

        assertThat(john().isEnabled()).isTrue();
        assertThat(john().getFirstName()).isEqualTo("John");
    }

    @Test
    void testInstructorEditingAnActiveStudentKeepsHerActive() throws Exception {
        editJohn(true, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("John (edited)"))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void testInstructorCanStillReactivateByEditingUntilReactivationIsBuilt() throws Exception { // Interim, see UC-STU-reactivate-student
        deactivate(JOHN_ID, this.instructorBillToken).andExpect(status().isOk());

        editJohn(true, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        assertThat(john().isEnabled()).isTrue();
    }

    @Test
    void testStudentEditingHerselfCannotChangeHerStatus() throws Exception {
        editJohn(false, this.studentJohnToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        assertThat(john().isEnabled()).isTrue();
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
