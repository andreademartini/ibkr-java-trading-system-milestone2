package it.andreademartini.trading.paper.patternv1;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface PatternPaperRepository extends JpaRepository<PatternPaperEntity,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PatternPaperEntity r where r.id=:id")
    Optional<PatternPaperEntity> locked(@Param("id") String id);
    List<PatternPaperEntity> findByKindOrderByIdAsc(String kind);
}
