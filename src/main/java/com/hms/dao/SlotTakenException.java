package com.hms.dao;

import java.time.LocalDateTime;

public class SlotTakenException extends RuntimeException {
    public SlotTakenException(long doctorId, LocalDateTime start, Throwable cause) {
        super("Doctor " + doctorId + " already has an appointment at " + start, cause);
    }
}
