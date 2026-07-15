package com.passport.persona.repository;

import com.passport.persona.domain.ProfilePersona;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfilePersonaRepository extends JpaRepository<ProfilePersona, Long> {

    Optional<ProfilePersona> findByProfileId(Long profileId);
}
