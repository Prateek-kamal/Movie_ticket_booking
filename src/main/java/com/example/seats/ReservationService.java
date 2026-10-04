package com.example.seats;

import com.example.seats.Dtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {
    private final JdbcTemplate jdbc;

    public ReservationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public ReserveResult reserve(String userId, UUID showId, List<String> requested, String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "idempotency key required");
        }
        if (requested == null || requested.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_SEATS", "seats required");
        }
        List<String> seats = requested.stream().map(String::trim).distinct().sorted().toList();
        String hash = showId + "|" + String.join(",", seats);

        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", userId + ":" + showId);

        var show = jdbc.query("SELECT price_paise, per_user_limit FROM shows WHERE id = ?",
                (rs, i) -> new long[]{rs.getLong(1), rs.getInt(2)}, showId)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHOW_NOT_FOUND", "No such show"));

        var prior = jdbc.query(
                "SELECT request_hash, reservation_id FROM idempotency_keys WHERE user_id = ? AND idem_key = ?",
                (rs, i) -> new Object[]{rs.getString(1), rs.getObject(2, UUID.class)}, userId, key);
        if (!prior.isEmpty()) {
            if (!prior.get(0)[0].equals(hash)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "Key already used with a different request");
            }
            return new ReserveResult(load((UUID) prior.get(0)[1]), true);
        }

        Integer mine = jdbc.queryForObject(
                "SELECT count(*) FROM seats WHERE show_id = ? AND user_id = ?", Integer.class, showId, userId);
        if (mine + seats.size() > show[1]) {
            throw new ApiException(HttpStatus.CONFLICT, "PER_USER_LIMIT", "Per-user seat limit exceeded");
        }

        UUID rid = UUID.randomUUID();
        jdbc.update("INSERT INTO reservations(id, show_id, user_id, amount_paise, status) VALUES (?,?,?,?, 'CONFIRMED')",
                rid, showId, userId, show[0] * seats.size());

        for (String seat : seats) {
            int changed = jdbc.update(
                    "UPDATE seats SET status = 'CONFIRMED', user_id = ?, reservation_id = ? " +
                    "WHERE show_id = ? AND label = ? AND status = 'AVAILABLE'",
                    userId, rid, showId, seat);
            if (changed == 0) {
                Integer exists = jdbc.queryForObject(
                        "SELECT count(*) FROM seats WHERE show_id = ? AND label = ?", Integer.class, showId, seat);
                if (exists == 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_SEAT", "No such seat: " + seat);
                }
                throw new ApiException(HttpStatus.CONFLICT, "SEAT_TAKEN", "Seat already taken: " + seat);
            }
        }

        jdbc.update("INSERT INTO idempotency_keys(user_id, idem_key, request_hash, reservation_id) VALUES (?,?,?,?)",
                userId, key, hash, rid);
        return new ReserveResult(load(rid), false);
    }

    @Transactional
    public ReservationView cancel(String userId, UUID reservationId) {
        var row = jdbc.query("SELECT user_id, status FROM reservations WHERE id = ? FOR UPDATE",
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}, reservationId)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "No such reservation"));
        if (!row[0].equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_OWNER", "Not your reservation");
        }
        if (row[1].equals("CANCELLED")) {
            return load(reservationId);
        }
        jdbc.update("UPDATE reservations SET status = 'CANCELLED' WHERE id = ?", reservationId);
        jdbc.update("UPDATE seats SET status = 'AVAILABLE', user_id = NULL, reservation_id = NULL " +
                    "WHERE reservation_id = ?", reservationId);
        return load(reservationId);
    }

    private ReservationView load(UUID rid) {
        var r = jdbc.queryForObject(
                "SELECT show_id, user_id, amount_paise, status FROM reservations WHERE id = ?",
                (rs, i) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getString(4)}, rid);
        List<String> labels = jdbc.queryForList(
                "SELECT label FROM seats WHERE reservation_id = ? ORDER BY label", String.class, rid);
        return new ReservationView(rid, (UUID) r[0], (String) r[1], labels, (long) r[2],
                ((String) r[3]).toLowerCase());
    }
}
