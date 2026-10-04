package com.example.seats;

import com.example.seats.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class ShowController {
    private final ShowService shows;
    private final ReservationService reservations;
    private final Auth auth;

    public ShowController(ShowService shows, ReservationService reservations, Auth auth) {
        this.shows = shows;
        this.reservations = reservations;
        this.auth = auth;
    }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowView create(@RequestHeader(value = "Authorization", required = false) String authz,
                           @Valid @RequestBody CreateShowRequest req) {
        auth.admin(authz);
        return shows.create(req);
    }

    @GetMapping("/shows/{id}")
    public ShowView get(@PathVariable UUID id) {
        return shows.get(id);
    }

    @PostMapping("/shows/{id}/reserve")
    public ResponseEntity<ReservationView> reserve(
            @RequestHeader(value = "Authorization", required = false) String authz,
            @RequestHeader(value = "Idempotency-Key", required = false) String headerKey,
            @PathVariable UUID id,
            @RequestBody ReserveRequest req) {
        String userId = auth.user(authz);
        String key = headerKey != null ? headerKey : req.idempotencyKey();
        ReserveResult result = reservations.reserve(userId, id, req.seats(), key);
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.reservation());
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationView cancel(@RequestHeader(value = "Authorization", required = false) String authz,
                                  @PathVariable UUID id) {
        return reservations.cancel(auth.user(authz), id);
    }
}
