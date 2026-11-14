package bg.kostadinov.cameras.access;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AccessGroupRepository extends JpaRepository<AccessGroup, Long> {

    List<AccessGroup> findAllByOrderByNameAsc();

    boolean existsByName(String name);

    @Query("select g from AccessGroup g join g.members m where m.id = :userId order by g.name")
    List<AccessGroup> findByMemberId(Long userId);
}
