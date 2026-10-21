package vn.edu.parking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.ParkingOperationStatus;
import vn.edu.parking.repository.ParkingOperationRepository;
import vn.edu.parking.web.dto.ParkingResponse;

@Service
public class ParkingOperationExecutionService {
    private final ParkingOperationRepository operations;
    private final ObjectMapper objectMapper;

    public ParkingOperationExecutionService(ParkingOperationRepository operations,
            ObjectMapper objectMapper) {
        this.operations = operations;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ParkingResponse execute(long reservationId, String payloadHash,
            Supplier<ParkingResponse> action) {
        var operation = operations.findForUpdateById(reservationId)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy yêu cầu đang chờ xử lý."));
        if (operation.getStatus() != ParkingOperationStatus.PROCESSING
                || !operation.getPayloadHash().equals(payloadHash))
            throw new IllegalStateException("Trạng thái yêu cầu không còn hợp lệ.");

        ParkingResponse response = action.get();
        try {
            operation.complete(objectMapper.writeValueAsString(response), Instant.now());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể lưu kết quả để phục hồi ngoại tuyến.", ex);
        }
        operations.save(operation);
        return response;
    }
}
