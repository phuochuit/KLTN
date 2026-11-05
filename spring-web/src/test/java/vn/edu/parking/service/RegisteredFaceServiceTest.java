package vn.edu.parking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.edu.parking.domain.FamilyMember;
import vn.edu.parking.repository.FamilyMemberRepository;

class RegisteredFaceServiceTest {
    @Test
    void explainsMissingRegisteredImageAsUserAction() {
        var members = mock(FamilyMemberRepository.class);
        var storage = mock(ResidentImageStorage.class);
        var member = new FamilyMember();
        member.setId(12L);
        member.setRegistrationFaceImagePath("private:missing.jpg");
        when(members.findById(12L)).thenReturn(Optional.of(member));
        when(storage.readStoredReference("private:missing.jpg"))
            .thenThrow(new IllegalArgumentException("Không tìm thấy ảnh cư dân"));

        var exception = assertThrows(IllegalArgumentException.class,
            () -> new RegisteredFaceService(members, storage).load(12L));

        assertEquals("Chủ xe chưa có ảnh khuôn mặt hợp lệ. "
            + "Vui lòng cập nhật ảnh cư dân trước khi đối chiếu.", exception.getMessage());
    }
}
