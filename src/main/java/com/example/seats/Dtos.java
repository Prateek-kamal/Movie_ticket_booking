package com.example.seats;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public class Dtos {
    public record CreateShowRequest(
            @NotBlank String name,
            @NotEmpty List<@NotBlank String> seats,
            @Positive long pricePaise,
            Integer perUserLimit) {}

    public record ReserveRequest(List<String> seats, String idempotencyKey) {}

    public record SeatView(String seat, String status) {}

    public record ShowView(UUID id, String name, long pricePaise, int perUserLimit,
                           int totalSeats, int available, int held, int confirmed,
                           List<SeatView> seats) {}

    public record ReservationView(UUID reservationId, UUID showId, String userId,
                                  List<String> seats, long amountPaise, String status) {}

    public record ReserveResult(ReservationView reservation, boolean replay) {}
}
