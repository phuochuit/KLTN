package vn.edu.parking.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.EvidenceAssetRole;
import vn.edu.parking.domain.GateEvidenceAsset;

public interface GateEvidenceAssetRepository extends JpaRepository<GateEvidenceAsset, String> {
    List<GateEvidenceAsset> findByEvidenceIdAndDeletedAtIsNull(String evidenceId);
    Optional<GateEvidenceAsset> findFirstByEvidenceIdAndAssetRoleAndDeletedAtIsNull(
        String evidenceId, EvidenceAssetRole assetRole);
    Optional<GateEvidenceAsset> findByIdAndEvidenceIdAndDeletedAtIsNull(
        String id, String evidenceId);
}
