package vn.edu.parking.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.GateShiftAssignment;
import vn.edu.parking.domain.PaymentMethod;
import vn.edu.parking.domain.PaymentStatus;
import vn.edu.parking.domain.SessionStatus;
import vn.edu.parking.repository.GateShiftAssignmentRepository;
import vn.edu.parking.repository.ManualOverrideEventRepository;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.ParkingSlotRepository;
import vn.edu.parking.repository.PaymentTransactionRepository;

@Service
public class ShiftSummaryService {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final GateShiftAssignmentRepository shifts;
    private final PaymentTransactionRepository payments;
    private final ParkingSessionRepository sessions;
    private final ParkingSlotRepository slots;
    private final ManualOverrideEventRepository overrides;

    public ShiftSummaryService(GateShiftAssignmentRepository shifts,
            PaymentTransactionRepository payments, ParkingSessionRepository sessions,
            ParkingSlotRepository slots, ManualOverrideEventRepository overrides) {
        this.shifts = shifts;
        this.payments = payments;
        this.sessions = sessions;
        this.slots = slots;
        this.overrides = overrides;
    }

    @Transactional(readOnly = true)
    public Optional<ShiftSummary> current(long accountId) {
        Instant now = Instant.now();
        return shifts.findByAccount_IdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
            accountId, now, now).stream().findFirst().map(shift -> summarize(shift, now));
    }

    @Transactional(readOnly = true)
    public Optional<ShiftSummary> previous() {
        return shifts.findFirstByEndsAtLessThanEqualOrderByEndsAtDesc(Instant.now())
            .map(shift -> summarize(shift, shift.getEndsAt()));
    }

    private ShiftSummary summarize(GateShiftAssignment shift, Instant requestedEnd) {
        Instant effectiveEnd = requestedEnd.isBefore(shift.getEndsAt()) ? requestedEnd : shift.getEndsAt();
        LocalDateTime from = LocalDateTime.ofInstant(shift.getStartsAt(), VIETNAM);
        LocalDateTime to = LocalDateTime.ofInstant(effectiveEnd, VIETNAM);
        var shiftPayments = payments.findByGateShiftAssignment_IdOrderByPaidAtAsc(shift.getId());
        BigDecimal cash = total(shiftPayments, PaymentMethod.CASH);
        BigDecimal bank = total(shiftPayments, PaymentMethod.BANK_TRANSFER);
        BigDecimal pos = total(shiftPayments, PaymentMethod.POS);
        long pending = shiftPayments.stream().filter(item -> item.getStatus() == PaymentStatus.PAID).count();
        var activeSlots = slots.findByActiveTrueOrderBySlotCodeAsc();
        long occupiedSlots = activeSlots.stream().filter(slot -> slot.getCurrentSession() != null).count();
        String operatorName = shift.getAccount().getFullName();
        if (operatorName == null || operatorName.isBlank()) operatorName = shift.getAccount().getUsername();
        return new ShiftSummary(shift.getId(), shift.getShiftName(), operatorName, shift.getStartsAt(),
            shift.getEndsAt(), effectiveEnd, shift.getDuty(), cash.add(bank).add(pos), cash, bank, pos,
            sessions.countByEntryTimeBetween(from, to),
            sessions.countByStatusAndExitTimeBetween(SessionStatus.COMPLETED, from, to),
            sessions.countByStatus(SessionStatus.OPEN), occupiedSlots,
            activeSlots.size() - occupiedSlots, pending,
            overrides.countByGateShiftAssignment_Id(shift.getId()));
    }

    private static BigDecimal total(java.util.List<vn.edu.parking.domain.PaymentTransaction> payments,
            PaymentMethod method) {
        return payments.stream().filter(item -> item.getPaymentMethod() == method)
            .map(vn.edu.parking.domain.PaymentTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record ShiftSummary(Long shiftId, String shiftName, String operatorName,
        Instant startsAt, Instant endsAt,
        Instant summarizedAt, String duty, BigDecimal totalRevenue, BigDecimal cashRevenue,
        BigDecimal bankTransferRevenue, BigDecimal posRevenue, long vehicleEntries,
        long vehicleExits, long vehiclesCurrentlyInside, long occupiedSlots, long availableSlots,
        long pendingReconciliation, long manualOverrides) { }
}
