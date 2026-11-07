package team.projectpulse.security.authorizationmanagers;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import team.projectpulse.student.StudentSecurityService;

import java.util.function.Supplier;

/**
 * Guards an action on one student, admitting only the course admin who owns the course of that student's course section.
 *
 * <p>Reads {@code studentId} and delegates to {@link StudentSecurityService#isCurrentUserAdminOfStudentCourse}.
 */
@Component
public class StudentCourseOwnerAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final StudentSecurityService studentSecurityService;


    public StudentCourseOwnerAuthorizationManager(StudentSecurityService studentSecurityService) {
        this.studentSecurityService = studentSecurityService;
    }

    @Override
    public @Nullable AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication, RequestAuthorizationContext context) {
        Integer studentId = PathVariables.readId(context, "studentId");
        if (studentId == null) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(
                this.studentSecurityService.isCurrentUserAdminOfStudentCourse(studentId));
    }

}
