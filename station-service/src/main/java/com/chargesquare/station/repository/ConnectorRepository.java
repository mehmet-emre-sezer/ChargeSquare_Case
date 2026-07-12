package com.chargesquare.station.repository;

import com.chargesquare.station.domain.Connector;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConnectorRepository extends JpaRepository<Connector, Long> {

    // Okuma yolları tarifeyi baştan yükler; böylece yanıt tek sorguda kalır (N+1 yok).
    @EntityGraph(attributePaths = "tariff")
    Optional<Connector> findWithTariffById(Long id);

    @Query("select c from Connector c join fetch c.tariff where c.station.id = :stationId order by c.id")
    List<Connector> findByStationIdWithTariff(@Param("stationId") Long stationId);

    // Status değişiklikleri satır kilidi alır; iki eşzamanlı start aynı connector'ı birlikte occupy edemez.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Connector c where c.id = :id")
    Optional<Connector> findByIdForUpdate(@Param("id") Long id);
}
