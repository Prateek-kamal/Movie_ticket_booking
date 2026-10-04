package com.example.seats;

import com.example.seats.Dtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ShowService {
    private final JdbcTemplate jdbc;

    public ShowService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public ShowView create(CreateShowRequest req) {
        List<String> seats = req.seats().stream().map(String::trim).toList();
        if (seats.stream().distinct().count() != seats.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_SEATS", "Seat labels must be unique");
        }
        int limit = req.perUserLimit() == null ? 4 : req.perUserLimit();
        if (limit < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_LIMIT", "per_user_limit must be >= 1");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shows(id, name, price_paise, per_user_limit) VALUES (?,?,?,?)",
                id, req.name(), req.pricePaise(), limit);
        jdbc.batchUpdate("INSERT INTO seats(show_id, label, status) VALUES (?,?,'AVAILABLE')",
                seats, seats.size(), (ps, label) -> {
                    ps.setObject(1, id);
                    ps.setString(2, label);
                });
        return get(id);
    }

    public ShowView get(UUID id) {
        var show = jdbc.query("SELECT name, price_paise, per_user_limit FROM shows WHERE id = ?",
                (rs, i) -> new Object[]{rs.getString(1), rs.getLong(2), rs.getInt(3)}, id)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHOW_NOT_FOUND", "No such show"));

        List<SeatView> seats = jdbc.query(
                "SELECT label, status FROM seats WHERE show_id = ? ORDER BY label",
                (rs, i) -> new SeatView(rs.getString(1), rs.getString(2).toLowerCase()), id);

        int available = 0, held = 0, confirmed = 0;
        for (SeatView s : seats) {
            switch (s.status()) {
                case "available" -> available++;
                case "held" -> held++;
                default -> confirmed++;
            }
        }
        return new ShowView(id, (String) show[0], (long) show[1], (int) show[2],
                seats.size(), available, held, confirmed, seats);
    }
}
