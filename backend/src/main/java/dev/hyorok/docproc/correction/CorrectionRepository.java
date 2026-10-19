package dev.hyorok.docproc.correction;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CorrectionRepository extends JpaRepository<Correction, Long> {

    // A "derived query": Spring Data reads the method name and writes the SQL.
    //   findTop30          -> LIMIT 30
    //   OrderByCreatedAtDesc -> ORDER BY created_at DESC
    // Misspell a property name and the app fails at startup, not at runtime.
    List<Correction> findTop30ByOrderByCreatedAtDesc();
}
