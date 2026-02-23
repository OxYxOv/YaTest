import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Main {
    private static final DateTimeFormatter OUTPUT_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        int port = readPort(args);
        BookingRepository repository = new BookingRepository(
                env("DB_URL", "jdbc:postgresql://localhost:5432/postgres"),
                env("DB_USER", "postgres"),
                env("DB_PASSWORD", "postgres")
        );
        repository.ensureSchema();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", exchange -> writeJson(exchange, 200, "{\"status\":\"ok\"}"));
        server.createContext("/bookings", new BookingHandler(repository));
        server.setExecutor(null);
        server.start();
    }

    static int readPort(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return 8080;
    }

    static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    static boolean isOverlapping(LocalDateTime fromA, LocalDateTime toA, LocalDateTime fromB, LocalDateTime toB) {
        return fromA.isBefore(toB) && toA.isAfter(fromB);
    }

    static LocalDateTime parseTime(String value) {
        String normalized = value.trim().replace(' ', 'T');
        return LocalDateTime.parse(normalized);
    }

    static class BookingHandler implements HttpHandler {
        private final BookingRepository repository;

        BookingHandler(BookingRepository repository) {
            this.repository = repository;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
                if ("/bookings".equals(path)) {
                    if ("GET".equals(method)) {
                        listBookings(exchange);
                        return;
                    }
                    if ("POST".equals(method)) {
                        createBooking(exchange);
                        return;
                    }
                    writeJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                    return;
                }

                if (path.startsWith("/bookings/")) {
                    int id = parseId(path.substring("/bookings/".length()));
                    if (id <= 0) {
                        writeJson(exchange, 400, "{\"error\":\"invalid_id\"}");
                        return;
                    }

                    if ("GET".equals(method)) {
                        getBooking(exchange, id);
                        return;
                    }
                    if ("DELETE".equals(method)) {
                        deleteBooking(exchange, id);
                        return;
                    }
                    writeJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                    return;
                }

                writeJson(exchange, 404, "{\"error\":\"not_found\"}");
            } catch (NumberFormatException e) {
                writeJson(exchange, 400, "{\"error\":\"invalid_number\"}");
            } catch (IllegalArgumentException e) {
                writeJson(exchange, 400, "{\"error\":\"bad_request\"}");
            } catch (DateTimeParseException e) {
                writeJson(exchange, 400, "{\"error\":\"invalid_time\"}");
            } catch (SQLException e) {
                writeJson(exchange, 500, "{\"error\":\"db_error\"}");
            }
        }

        private void listBookings(HttpExchange exchange) throws IOException, SQLException {
            List<Booking> bookings = repository.list();
            StringBuilder body = new StringBuilder("[");
            for (int i = 0; i < bookings.size(); i++) {
                if (i > 0) {
                    body.append(',');
                }
                body.append(bookings.get(i).toJson());
            }
            body.append(']');
            writeJson(exchange, 200, body.toString());
        }

        private void getBooking(HttpExchange exchange, int id) throws IOException, SQLException {
            Booking booking = repository.getById(id);
            if (booking == null) {
                writeJson(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            writeJson(exchange, 200, booking.toJson());
        }

        private void deleteBooking(HttpExchange exchange, int id) throws IOException, SQLException {
            if (repository.delete(id)) {
                writeJson(exchange, 200, "{\"status\":\"deleted\"}");
                return;
            }
            writeJson(exchange, 404, "{\"error\":\"not_found\"}");
        }

        private void createBooking(HttpExchange exchange) throws IOException, SQLException {
            Map<String, String> data = parseJson(readBody(exchange));

            int userId = readInt(data, "user_id", "userId");
            int placeId = readInt(data, "place_id", "placeId");
            LocalDateTime timeFrom = parseTime(readString(data, "time_from", "timeFrom"));
            LocalDateTime timeTo = parseTime(readString(data, "time_to", "timeTo"));

            if (!timeFrom.isBefore(timeTo)) {
                writeJson(exchange, 400, "{\"error\":\"invalid_time_range\"}");
                return;
            }

            if (repository.hasOverlap(placeId, timeFrom, timeTo)) {
                writeJson(exchange, 409, "{\"error\":\"place_already_booked\"}");
                return;
            }

            Booking created = repository.create(userId, placeId, timeFrom, timeTo);
            writeJson(exchange, 201, created.toJson());
        }

        private int parseId(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        private static int readInt(Map<String, String> data, String key, String alternativeKey) {
            String value = data.containsKey(key) ? data.get(key) : data.get(alternativeKey);
            if (value == null) {
                throw new IllegalArgumentException("Missing required field: " + key + " or " + alternativeKey);
            }
            return Integer.parseInt(value);
        }

        private static String readString(Map<String, String> data, String key, String alternativeKey) {
            String value = data.containsKey(key) ? data.get(key) : data.get(alternativeKey);
            if (value == null) {
                throw new IllegalArgumentException("Missing required field: " + key + " or " + alternativeKey);
            }
            return value;
        }

        private static Map<String, String> parseJson(String body) {
            try {
                Map<String, Object> raw = OBJECT_MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
                Map<String, String> data = new java.util.HashMap<>();
                for (Map.Entry<String, Object> entry : raw.entrySet()) {
                    if (entry.getValue() != null) {
                        data.put(entry.getKey(), String.valueOf(entry.getValue()));
                    }
                }
                return data;
            } catch (IOException e) {
                throw new IllegalArgumentException("Invalid JSON body", e);
            }
        }
    }

    static class BookingRepository {
        private final String url;
        private final String user;
        private final String password;

        BookingRepository(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        void ensureSchema() {
            try (Connection connection = open();
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS bookings (id SERIAL PRIMARY KEY, user_id INTEGER NOT NULL, place_id INTEGER NOT NULL, time_from TIMESTAMP NOT NULL, time_to TIMESTAMP NOT NULL)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_bookings_place_id ON bookings(place_id)");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        Booking create(int userId, int placeId, LocalDateTime timeFrom, LocalDateTime timeTo) throws SQLException {
            try (Connection connection = open();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO bookings(user_id, place_id, time_from, time_to) VALUES (?, ?, ?, ?) RETURNING id, user_id, place_id, time_from, time_to"
                 )) {
                statement.setInt(1, userId);
                statement.setInt(2, placeId);
                statement.setTimestamp(3, Timestamp.valueOf(timeFrom));
                statement.setTimestamp(4, Timestamp.valueOf(timeTo));
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return readBooking(resultSet);
                }
            }
        }

        List<Booking> list() throws SQLException {
            try (Connection connection = open();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT id, user_id, place_id, time_from, time_to FROM bookings ORDER BY id"
                 );
                 ResultSet resultSet = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (resultSet.next()) {
                    bookings.add(readBooking(resultSet));
                }
                return bookings;
            }
        }

        Booking getById(int id) throws SQLException {
            try (Connection connection = open();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT id, user_id, place_id, time_from, time_to FROM bookings WHERE id = ?"
                 )) {
                statement.setInt(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return null;
                    }
                    return readBooking(resultSet);
                }
            }
        }

        boolean delete(int id) throws SQLException {
            try (Connection connection = open();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM bookings WHERE id = ?")) {
                statement.setInt(1, id);
                return statement.executeUpdate() > 0;
            }
        }

        boolean hasOverlap(int placeId, LocalDateTime from, LocalDateTime to) throws SQLException {
            try (Connection connection = open();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT 1 FROM bookings WHERE place_id = ? AND time_from < ? AND time_to > ? LIMIT 1"
                 )) {
                statement.setInt(1, placeId);
                statement.setTimestamp(2, Timestamp.valueOf(to));
                statement.setTimestamp(3, Timestamp.valueOf(from));
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        }

        private Connection open() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        private Booking readBooking(ResultSet resultSet) throws SQLException {
            return new Booking(
                    resultSet.getInt("id"),
                    resultSet.getInt("user_id"),
                    resultSet.getInt("place_id"),
                    resultSet.getTimestamp("time_from").toLocalDateTime(),
                    resultSet.getTimestamp("time_to").toLocalDateTime()
            );
        }
    }

    static class Booking {
        private final int id;
        private final int userId;
        private final int placeId;
        private final LocalDateTime timeFrom;
        private final LocalDateTime timeTo;

        Booking(int id, int userId, int placeId, LocalDateTime timeFrom, LocalDateTime timeTo) {
            this.id = id;
            this.userId = userId;
            this.placeId = placeId;
            this.timeFrom = timeFrom;
            this.timeTo = timeTo;
        }

        String toJson() {
            return "{\"id\":" + id
                    + ",\"user_id\":" + userId
                    + ",\"place_id\":" + placeId
                    + ",\"time_from\":\"" + OUTPUT_TIME_FORMAT.format(timeFrom)
                    + "\",\"time_to\":\"" + OUTPUT_TIME_FORMAT.format(timeTo)
                    + "\"}";
        }
    }
}
