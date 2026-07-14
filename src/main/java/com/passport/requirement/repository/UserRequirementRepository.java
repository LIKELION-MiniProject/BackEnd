package com.passport.requirement.repository;

import com.passport.requirement.domain.UserGraduationRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRequirementRepository extends JpaRepository<UserGraduationRequirement, Long> {

    Optional<UserGraduationRequirement> findByProfileId(Long profileId);
}
