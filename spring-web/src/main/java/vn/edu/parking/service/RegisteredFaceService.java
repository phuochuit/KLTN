package vn.edu.parking.service;

import org.springframework.stereotype.Service;
import vn.edu.parking.repository.FamilyMemberRepository;

@Service
public class RegisteredFaceService {
    private final FamilyMemberRepository members;
    private final ResidentImageStorage storage;

    public RegisteredFaceService(FamilyMemberRepository members, ResidentImageStorage storage) {
        this.members = members;
        this.storage = storage;
    }

    public RegisteredFace load(Long familyMemberId) {
        if (familyMemberId == null) throw new IllegalArgumentException("Thiếu thành viên đối chiếu");
        var member = members.findById(familyMemberId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên đối chiếu"));
        byte[] bytes = storage.readStoredReference(member.getRegistrationFaceImagePath());
        return new RegisteredFace(member.getId(), bytes, "registered-face.jpg", "image/jpeg");
    }

    public record RegisteredFace(Long familyMemberId, byte[] bytes, String filename, String mediaType) { }
}
