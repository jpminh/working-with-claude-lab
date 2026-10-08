package com.marlowefinch.ops;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * The stand-up line for a date range: the four headline numbers plus the carrier with the
 * lowest on-time rate and the ticket category with the most tickets opened. Both names are
 * null when the range has no deliveries or no tickets; ties go to the first name alphabetically.
 */
public record Summary(
        LocalDate from,
        LocalDate to,
        Double onTimeRate,
        long openTickets,
        BigDecimal revenue,
        long orders,
        String worstCarrier,
        String busiestTicketCategory) {

    static Summary of(Kpis kpis, List<CarrierOnTime> carriers, List<TicketCategoryCount> categories) {
        String worstCarrier = carriers.stream()
                .filter(c -> c.delivered() > 0)
                .min(Comparator.comparing(CarrierOnTime::rate).thenComparing(CarrierOnTime::carrier))
                .map(CarrierOnTime::carrier)
                .orElse(null);
        String busiestTicketCategory = categories.stream()
                .min(Comparator.comparing(TicketCategoryCount::total).reversed()
                        .thenComparing(TicketCategoryCount::category))
                .map(TicketCategoryCount::category)
                .orElse(null);
        return new Summary(kpis.from(), kpis.to(), kpis.onTimeRate(), kpis.openTickets(),
                kpis.revenue(), kpis.orders(), worstCarrier, busiestTicketCategory);
    }
}
