package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.UiOption;

import java.util.List;
import java.util.Optional;

public interface UiOptionRepository extends JpaRepository<UiOption, Long> {
    Optional<UiOption> findByCategoryIgnoreCaseAndValueIgnoreCase(String category, String value);
    List<UiOption> findByCategoryIgnoreCaseOrderByUseCountDescLastUsedAtDesc(String category);
    List<UiOption> findAllByOrderByUseCountDescLastUsedAtDesc();
}
