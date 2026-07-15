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
import com.passport.requirement.domain.CertMark;
import com.passport.requirement.domain.EffectiveRequirement;
import com.passport.requirement.domain.RequirementCertificationTargets;
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
import static org.mockito.Mockito.lenient;
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
        // lenient: 아래 5분야 테스트들이 이 기본 스텁을 각자 다른 반환값으로 재정의하므로,
        // 재정의로 인해 이 스텁이 미사용 처리되어도 strict stubbing 예외가 나지 않게 한다.
        lenient().when(requirementResolutionService.resolve(any())).thenReturn(EffectiveRequirement.fromHardcoded(BigdataAiRequirement.REQUIREMENT));
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

    @Test
    void 인증_5분야_중_대상으로_남은_분야가_있으면_졸업인증_불충족으로_판정한다() {
        RequirementCertificationTargets targets = RequirementCertificationTargets.builder()
                .foreignLangCert(CertMark.DONE)
                .infoProcessing(CertMark.TARGET)
                .cpr(CertMark.NOT_TARGET)
                .socialService(CertMark.NOT_TARGET)
                .foreignLangExtra(CertMark.NOT_TARGET)
                .build();
        when(requirementResolutionService.resolve(any())).thenReturn(withCertificationTargets(targets));
        when(courseRepository.findAllByProfileId(anyLong())).thenReturn(List.of());
        when(certificationRepository.findAllByProfileId(anyLong())).thenReturn(List.of());

        DiagnosisResponse response = diagnosisService.diagnose(1L, 1L);

        assertThat(response.graduationCertification()).isNotNull();
        assertThat(response.graduationCertification().fulfilled()).isFalse();
        assertThat(response.eligibleForGraduation()).isFalse();
    }

    @Test
    void 인증_5분야가_모두_완료거나_비대상이면_졸업인증_충족으로_판정하고_졸업가능여부에_반영된다() {
        var req = BigdataAiRequirement.REQUIREMENT;
        RequirementCertificationTargets targets = RequirementCertificationTargets.builder()
                .foreignLangCert(CertMark.DONE)
                .infoProcessing(CertMark.DONE)
                .cpr(CertMark.NOT_TARGET)
                .socialService(CertMark.NOT_TARGET)
                .foreignLangExtra(CertMark.NOT_TARGET)
                .build();
        when(requirementResolutionService.resolve(any())).thenReturn(withCertificationTargets(targets));

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

        assertThat(response.graduationCertification().fulfilled()).isTrue();
        assertThat(response.eligibleForGraduation()).isTrue();
    }

    @Test
    void 유저_요건_미저장시_졸업인증_5분야_판정은_null이고_기존_졸업가능_판정에_영향을_주지_않는다() {
        // @BeforeEach 기본 스텁 = EffectiveRequirement.fromHardcoded(...) → certificationTargets=null
        when(courseRepository.findAllByProfileId(anyLong())).thenReturn(List.of());
        when(certificationRepository.findAllByProfileId(anyLong())).thenReturn(List.of());

        DiagnosisResponse response = diagnosisService.diagnose(1L, 1L);

        assertThat(response.graduationCertification()).isNull();
    }

    private EffectiveRequirement withCertificationTargets(RequirementCertificationTargets targets) {
        EffectiveRequirement base = EffectiveRequirement.fromHardcoded(BigdataAiRequirement.REQUIREMENT);
        return new EffectiveRequirement(
                base.deptCode(), base.totalCredit(), base.majorRequiredCredit(), base.majorElectiveCredit(),
                base.majorBasicCredit(), base.geRequiredCredit(), base.geElectiveCredit(), base.generalElectiveCredit(),
                base.majorTotalCredit(), base.liberalTotalCredit(), base.coreLiberalTargetCount(),
                base.graduationExam(), base.languageCertRequired(), base.volunteerCertRequired(), base.thesisCertRequired(),
                base.minGpa(), targets
        );
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
