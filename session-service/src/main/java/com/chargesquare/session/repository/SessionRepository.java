package com.chargesquare.session.repository;

import com.chargesquare.session.domain.Session;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<Session, Long> {

    // Stop, oturum satırını kilitler ki aynı oturuma gelen iki eşzamanlı stop iki kez faturalamasın.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.id = :id")
    Optional<Session> findByIdForUpdate(@Param("id") Long id);

    List<Session> findByUserIdOrderByStartedAtDesc(Long userId);
}
