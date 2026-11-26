package com.clinicbooking.appointment.application.port.out;

import java.time.LocalDate;

public interface HolidayPort {
    boolean isHoliday(LocalDate date);
}
