package team.projectpulse.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.projectpulse.course.Course;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.UserUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StudentSecurityServiceTest {

    private static final int STUDENT_ID = 4;

    private static final int COURSE_ADMIN_ID = 1;

    @Mock
    StudentRepository studentRepository;
    @Mock
    UserUtils userUtils;

    @InjectMocks
    StudentSecurityService studentSecurityService;

    Student student;


    @BeforeEach
    void setUp() {
        Instructor courseAdmin = new Instructor();
        courseAdmin.setId(COURSE_ADMIN_ID);
        Course course = new Course();
        course.setCourseAdmin(courseAdmin);
        Section section = new Section();
        section.setCourse(course);
        this.student = new Student();
        this.student.setId(STUDENT_ID);
        this.student.setSection(section);
    }

    @Test
    void testCourseAdminOfStudentCourseIsAdmittedWithoutAnAssignmentToTheCourseSection() {
        given(this.studentRepository.findById(STUDENT_ID)).willReturn(Optional.of(this.student));
        given(this.userUtils.getUserId()).willReturn(COURSE_ADMIN_ID);

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(STUDENT_ID)).isTrue();
    }

    @Test
    void testAnyoneElseIsNotTheCourseAdminOfStudentCourse() {
        given(this.studentRepository.findById(STUDENT_ID)).willReturn(Optional.of(this.student));
        given(this.userUtils.getUserId()).willReturn(2);

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(STUDENT_ID)).isFalse();
    }

    @Test
    void testIdNamingNoStudentIsDeniedRatherThanThrowing() {
        given(this.studentRepository.findById(999999)).willReturn(Optional.empty());

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(999999)).isFalse();
    }

}
