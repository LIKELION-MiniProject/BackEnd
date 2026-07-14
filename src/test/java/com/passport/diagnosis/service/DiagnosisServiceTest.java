package com.passport.diagnosis.service;

import com.passport.auth.domain.User;
import com.passport.certification.domain.Certification;
import com.passport.certification.domain.Certification.CertificationStatus;
import com.passport.certification.domain.Certification.CertificationType;
import com.passport.certification.repository.CertificationRepository;
import com.passport.course.domain.Course;
import com.passport.course.domain.Course.CourseCategory;
import com.passport.course.domain.Course.Grade;
import com.passport.course.repository.CourseRepository;
import com.passport.diagnosis.dto.DiagnosisResponse;
import com.passport.profile.domain.Profile;
import com.passport.profile.service.ProfileService;
import com.passport.requirement.BigdataAiRequirement;
import com.passport.requirement.domain.EffectiveRequirement;
import com.passport.requirement.service.RequirementResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosisServiceTest {

    @Mock
    private ProfileService profileService;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CertificationRepository certificationRepository;
    @Mock
    private RequirementResolutionService requirementResolutionService;

    @InjectMocks
    private DiagnosisService diagnosisService;

    private Profile profile;

    @BeforeEach
    void setUp() {
        User user = User.builder().email("test@passport.ac.kr").password("hashed").nickname("테스터").build();
        profile = Profile.builder()
                .user(user)
                .deptCode(BigdataAiRequirement.DEPT_CODE)
                .studentId("2021000000")
                .admissionYear(2021)
                .name("테스터")
                .build();

        when(profileService.findOwnedProfile(anyLong(), anyLong())).thenReturn(profile);
        when(requirementResolutionService.resolve(any())).thenReturn(EffectiveRequirement.fromHardcoded(BigdataAiRequirement.REQUIREMENT));
    }

    @Test
    void 모든_요건을_충족하면_졸업_가능으로_판정한다() {
        var req = BigdataAiRequirement.REQUIREMENT;

        List<Course> courses = List.of(
                courseOf(req.majorRequiredCredit(), CourseCategory.MAJOR_REQUIRED, Grade.A),
                courseOf(req.majorElectiveCredit(), CourseCategory.MAJOR_ELECTIVE, Grade.A),
                courseOf(req.geRequiredCredit(), CourseCategory.GE_REQUIRED, Grade.A),
                courseOf(req.geElectiveCredit(), CourseCategory.GE_ELECTIVE, Grade.A),
                courseOf(req.generalElectiveCredit(), CourseCategory.GENERAL_ELECTIVE, Grade.A)
        );
        when(courseRepository.findAllByProfileId(anyLong())).thenReturn(courses);

        when(certificationRepository.findAllByProfileId(anyLong())).thenReturn(List.of(
                cert(CertificationType.LANGUAGE, CertificationStatus.PASS),
                cert(CertificationType.VOLUNTEER, CertificationStatus.PASS)
        ));

        DiagnosisResponse response = diagnosisService.diagnose(1L, 1L);

        assertThat(response.eligibleForGraduation()).isTrue();
        assertThat(response.totalCredit().shortfall()).isZero();
        assertThat(response.gpa().current()).isEqualTo(4.0);
    }

    @Test
    void 전공필수_학점이_부족하면_졸업_불가로_판정한다() {
        when(courseRepository.findAllByProfileId(anyLong())).thenReturn(
                List.of(courseOf(3, CourseCategory.MAJOR_REQUIRED, Grade.A)) // 21학점 필요한데 3학점만 이수
        );
        when(certificationRepository.findAllByProfileId(anyLong())).thenReturn(List.of());

        DiagnosisResponse response = diagnosisService.diagnose(1L, 1L);

        assertThat(response.eligibleForGraduation()).isFalse();
        DiagnosisResponse.CategoryProgress majorRequired = response.categories().stream()
                .filter(c -> c.category() == CourseCategory.MAJOR_REQUIRED)
                .findFirst().orElseThrow();
        assertThat(majorRequired.shortfall()).isEqualTo(BigdataAiRequirement.REQUIREMENT.majorRequiredCredit() - 3);
    }

    @Test
    void 필수_인증을_제출하지_않으면_졸업_불가로_판정한다() {
        when(courseRepository.findAllByProfileId(anyLong())).thenReturn(List.of());
        when(certificationRepository.findAllByProfileId(anyLong())).thenReturn(List.of());

        DiagnosisResponse response = diagnosisService.diagnose(1L, 1L);

        assertThat(response.eligibleForGraduation()).isFalse();
        DiagnosisResponse.CertificationProgress language = response.certifications().stream()
                .filter(c -> c.type() == CertificationType.LANGUAGE)
                .findFirst().orElseThrow();
        assertThat(language.fulfilled()).isFalse();
        assertThat(language.status()).isEqualTo(CertificationStatus.NOT_SUBMITTED);
    }

    private Course courseOf(int credit, CourseCategory category, Grade grade) {
        return Course.builder()
                .profile(profile)
                .name("과목")
                .credit(credit)
                .category(category)
                .grade(grade)
                .year(2026)
                .semester(1)
                .build();
    }

    private Certification cert(CertificationType type, CertificationStatus status) {
        return Certification.builder().profile(profile).type(type).status(status).build();
    }
}
