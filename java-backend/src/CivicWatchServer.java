import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CivicWatchServer {
    private static final Path BASE_DIR = Paths.get("").toAbsolutePath();
    private static final Path STATIC_DIR = BASE_DIR.resolve("static");
    private static final Path UPLOAD_DIR = STATIC_DIR.resolve("uploads");
    private static final Path FRONTEND_DIR = STATIC_DIR.resolve("frontend");
    private static final Path DATABASE_PATH = BASE_DIR.resolve("database.db");
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "5000"));



    private static final Map<String, Boolean> SESSIONS = new ConcurrentHashMap<>();
    private static final Pattern JSON_STRING_FIELD =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");

    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Files.createDirectories(UPLOAD_DIR);
        initDatabase();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new RootHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Java backend running on http://localhost:" + PORT);
    }

    private static void initDatabase() throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS complaints(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        description TEXT,
                        location TEXT,
                        image TEXT,
                        status TEXT
                    )
                    """);
            ensureColumnExists(connection, "complaints", "image", "TEXT");
        }
    }

    private static void ensureColumnExists(Connection connection, String table, String column, String type)
            throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    private static Connection openConnection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + DATABASE_PATH);
    }

    private static final class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();

                if (path.startsWith("/api/")) {
                    handleApi(exchange, path);
                    return;
                }

                if (path.startsWith("/static/")) {
                    serveStatic(exchange, STATIC_DIR.resolve(path.substring("/static/".length())));
                    return;
                }

                serveSpa(exchange, path);
            } catch (Exception error) {
                error.printStackTrace();
                sendJson(exchange, 500, "{\"message\":\"Internal server error.\"}");
            } finally {
                exchange.close();
            }
        }
    }

    private static void handleApi(HttpExchange exchange, String path) throws Exception {
        String method = exchange.getRequestMethod();

        if ("/api/report-issue".equals(path) && "POST".equalsIgnoreCase(method)) {
            reportIssue(exchange);
            return;
        }
        if ("/api/admin/login".equals(path) && "POST".equalsIgnoreCase(method)) {
            adminLogin(exchange);
            return;
        }
        if ("/api/admin/logout".equals(path) && "POST".equalsIgnoreCase(method)) {
            adminLogout(exchange);
            return;
        }
        if ("/api/admin/session".equals(path) && "GET".equalsIgnoreCase(method)) {
            adminSession(exchange);
            return;
        }
        if ("/api/complaints".equals(path) && "GET".equalsIgnoreCase(method)) {
            complaints(exchange);
            return;
        }
        if (path.startsWith("/api/resolve/") && "POST".equalsIgnoreCase(method)) {
            resolveComplaint(exchange, path);
            return;
        }

        sendJson(exchange, 404, "{\"message\":\"Not found.\"}");
    }

    private static void reportIssue(HttpExchange exchange) throws Exception {
        String contentType = header(exchange, "Content-Type");
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            sendJson(exchange, 400, "{\"message\":\"Description, location, and image are required.\"}");
            return;
        }

        String boundary = extractBoundary(contentType);
        if (boundary == null || boundary.isBlank()) {
            sendJson(exchange, 400, "{\"message\":\"Description, location, and image are required.\"}");
            return;
        }

        MultipartForm form = parseMultipart(readAllBytes(exchange.getRequestBody()), boundary);
        String description = form.fields.getOrDefault("desc", "").trim();
        String location = form.fields.getOrDefault("loc", "").trim();
        UploadedPart image = form.files.get("image");

        if (description.isEmpty() || location.isEmpty() || image == null || image.filename.isBlank()) {
            sendJson(exchange, 400, "{\"message\":\"Description, location, and image are required.\"}");
            return;
        }

        Files.createDirectories(UPLOAD_DIR);
        String savedFilename = buildSafeFilename(image.filename);
        Files.write(UPLOAD_DIR.resolve(savedFilename), image.content);

        Complaint complaint;
        try (Connection connection = openConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO complaints(description, location, image, status) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, description);
                insert.setString(2, location);
                insert.setString(3, savedFilename);
                insert.setString(4, "Pending");
                insert.executeUpdate();

                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new IllegalStateException("Could not read inserted complaint id.");
                    }
                    complaint = findComplaint(connection, keys.getInt(1));
                }
            }
        }

        sendJson(exchange, 201,
                "{\"message\":\"Submitted successfully.\",\"complaint\":" + complaint.toJson() + "}");
    }

    private static void adminLogin(HttpExchange exchange) throws IOException {
        Map<String, String> payload = parseSimpleJson(readRequestBody(exchange));
        String username = payload.getOrDefault("username", "").trim();
        String password = payload.getOrDefault("password", "").trim();

        if ("admin".equals(username) && "1234".equals(password)) {
            String sessionId = UUID.randomUUID().toString();
            SESSIONS.put(sessionId, true);
            exchange.getResponseHeaders().add(
                    "Set-Cookie",
                    "session_id=" + sessionId + "; Path=/; HttpOnly; SameSite=Lax");
            sendJson(exchange, 200, "{\"message\":\"Login successful.\"}");
            return;
        }

        sendJson(exchange, 401, "{\"message\":\"Invalid username or password.\"}");
    }

    private static void adminLogout(HttpExchange exchange) throws IOException {
        String sessionId = readCookie(exchange, "session_id");
        if (sessionId != null) {
            SESSIONS.remove(sessionId);
        }
        exchange.getResponseHeaders().add(
                "Set-Cookie",
                "session_id=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
        sendJson(exchange, 200, "{\"message\":\"Logged out.\"}");
    }

    private static void adminSession(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"authenticated\":" + isAdmin(exchange) + "}");
    }

    private static void complaints(HttpExchange exchange) throws Exception {
        

        List<Complaint> complaints = new ArrayList<>();
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM complaints ORDER BY id DESC")) {
            while (resultSet.next()) {
                complaints.add(Complaint.from(resultSet));
            }
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"complaints\":[");
        for (int index = 0; index < complaints.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(complaints.get(index).toJson());
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    private static void resolveComplaint(HttpExchange exchange, String path) throws Exception {
        if (!isAdmin(exchange)) {
            sendJson(exchange, 401, "{\"message\":\"Unauthorized.\"}");
            return;
        }

        String idPart = path.substring("/api/resolve/".length());
        int complaintId;
        try {
            complaintId = Integer.parseInt(idPart);
        } catch (NumberFormatException error) {
            sendJson(exchange, 404, "{\"message\":\"Complaint not found.\"}");
            return;
        }

        Complaint complaint;
        try (Connection connection = openConnection()) {
            try (PreparedStatement update =
                         connection.prepareStatement("UPDATE complaints SET status = 'Resolved' WHERE id = ?")) {
                update.setInt(1, complaintId);
                update.executeUpdate();
            }
            complaint = findComplaint(connection, complaintId);
        }

        if (complaint == null) {
            sendJson(exchange, 404, "{\"message\":\"Complaint not found.\"}");
            return;
        }

        sendJson(exchange, 200,
                "{\"message\":\"Complaint resolved.\",\"complaint\":" + complaint.toJson() + "}");
    }

    private static Complaint findComplaint(Connection connection, int complaintId) throws Exception {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT * FROM complaints WHERE id = ?")) {
            statement.setInt(1, complaintId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Complaint.from(resultSet);
                }
                return null;
            }
        }
    }

    private static boolean isAdmin(HttpExchange exchange) {
        String sessionId = readCookie(exchange, "session_id");
        return sessionId != null && Boolean.TRUE.equals(SESSIONS.get(sessionId));
    }

    private static void serveSpa(HttpExchange exchange, String path) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
            return;
        }

        String relative = path.startsWith("/") ? path.substring(1) : path;
        Path requested = FRONTEND_DIR.resolve(relative).normalize();
        if (!relative.isBlank() && requested.startsWith(FRONTEND_DIR) && Files.isRegularFile(requested)) {
            serveFile(exchange, requested);
            return;
        }

        serveFile(exchange, FRONTEND_DIR.resolve("index.html"));
    }

    private static void serveStatic(HttpExchange exchange, Path relativePath) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
            return;
        }

        Path file = relativePath.normalize();
        if (!file.startsWith(STATIC_DIR) || !Files.isRegularFile(file)) {
            sendPlain(exchange, 404, "Not Found", "text/plain; charset=utf-8");
            return;
        }

        serveFile(exchange, file);
    }

    private static void serveFile(HttpExchange exchange, Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", detectContentType(file));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String detectContentType(Path file) throws IOException {
        String type = Files.probeContentType(file);
        if (type != null) {
            return type;
        }

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    private static String detectExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dotIndex).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]+") ? extension : "";
    }

    private static String buildSafeFilename(String originalFilename) {
        String cleanName = Paths.get(originalFilename).getFileName().toString()
                .replaceAll("[^A-Za-z0-9._-]", "_");
        String extension = detectExtension(cleanName);
        String baseName = extension.isEmpty()
                ? cleanName
                : cleanName.substring(0, cleanName.length() - extension.length());
        if (baseName.isBlank()) {
            baseName = "upload";
        }
        return baseName + "_" + Instant.now().toEpochMilli() + extension;
    }

    private static String extractBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                String boundary = trimmed.substring("boundary=".length());
                if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() >= 2) {
                    return boundary.substring(1, boundary.length() - 1);
                }
                return boundary;
            }
        }
        return null;
    }

    private static MultipartForm parseMultipart(byte[] payload, String boundary) throws IOException {
        MultipartForm form = new MultipartForm();
        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        int cursor = 0;

        while (true) {
            int boundaryIndex = indexOf(payload, boundaryBytes, cursor);
            if (boundaryIndex < 0) {
                break;
            }
            int partStart = boundaryIndex + boundaryBytes.length;
            if (partStart + 1 < payload.length && payload[partStart] == '-' && payload[partStart + 1] == '-') {
                break;
            }
            if (partStart + 1 < payload.length && payload[partStart] == '\r' && payload[partStart + 1] == '\n') {
                partStart += 2;
            }

            int nextBoundary = indexOf(payload, boundaryBytes, partStart);
            if (nextBoundary < 0) {
                break;
            }

            int partEnd = nextBoundary;
            if (partEnd >= 2 && payload[partEnd - 2] == '\r' && payload[partEnd - 1] == '\n') {
                partEnd -= 2;
            }

            parsePart(payload, partStart, partEnd, form);
            cursor = nextBoundary;
        }

        return form;
    }

    private static void parsePart(byte[] payload, int start, int end, MultipartForm form) throws IOException {
        byte[] separator = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        int headerEnd = indexOf(payload, separator, start);
        if (headerEnd < 0 || headerEnd >= end) {
            return;
        }

        String headers = new String(payload, start, headerEnd - start, StandardCharsets.ISO_8859_1);
        byte[] body = copyOfRange(payload, headerEnd + separator.length, end);

        String contentDisposition = null;
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-disposition:")) {
                contentDisposition = line;
                break;
            }
        }
        if (contentDisposition == null) {
            return;
        }

        String name = readDispositionValue(contentDisposition, "name");
        String filename = readDispositionValue(contentDisposition, "filename");
        if (name == null || name.isBlank()) {
            return;
        }

        if (filename != null) {
            form.files.put(name, new UploadedPart(filename, body));
        } else {
            form.fields.put(name, new String(body, StandardCharsets.UTF_8));
        }
    }

    private static String readDispositionValue(String header, String key) throws IOException {
        Pattern pattern = Pattern.compile(key + "=\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(header);
        if (!matcher.find()) {
            return null;
        }
        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private static int indexOf(byte[] source, byte[] target, int fromIndex) {
        outer:
        for (int index = Math.max(fromIndex, 0); index <= source.length - target.length; index++) {
            for (int offset = 0; offset < target.length; offset++) {
                if (source[index + offset] != target[offset]) {
                    continue outer;
                }
            }
            return index;
        }
        return -1;
    }

    private static byte[] copyOfRange(byte[] source, int start, int end) {
        byte[] result = new byte[Math.max(0, end - start)];
        System.arraycopy(source, start, result, 0, result.length);
        return result;
    }

    private static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> values = new HashMap<>();
        Matcher matcher = JSON_STRING_FIELD.matcher(json);
        while (matcher.find()) {
            values.put(matcher.group(1), unescapeJson(matcher.group(2)));
        }
        return values;
    }

    private static String unescapeJson(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private static String readCookie(HttpExchange exchange, String cookieName) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) {
            return null;
        }
        for (String header : cookieHeaders) {
            String[] cookies = header.split(";");
            for (String cookie : cookies) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && cookieName.equals(parts[0].trim())) {
                    return parts[1].trim();
                }
            }
        }
        return null;
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        return new String(readAllBytes(exchange.getRequestBody()), StandardCharsets.UTF_8);
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        sendPlain(exchange, statusCode, body, "application/json; charset=utf-8");
    }

    private static void sendPlain(HttpExchange exchange, int statusCode, String body, String contentType)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private record UploadedPart(String filename, byte[] content) {
    }

    private static final class MultipartForm {
        private final Map<String, String> fields = new HashMap<>();
        private final Map<String, UploadedPart> files = new HashMap<>();
    }

    private record Complaint(int id, String description, String location, String image, String status) {
        private static Complaint from(ResultSet resultSet) throws Exception {
            return new Complaint(
                    resultSet.getInt("id"),
                    resultSet.getString("description"),
                    resultSet.getString("location"),
                    resultSet.getString("image"),
                    resultSet.getString("status"));
        }

        private String imageUrl() {
            return image == null || image.isBlank() ? null : "/static/uploads/" + image;
        }

        private String toJson() {
            return "{"
                    + "\"id\":" + id + ","
                    + "\"description\":" + escapeJson(description) + ","
                    + "\"location\":" + escapeJson(location) + ","
                    + "\"image\":" + escapeJson(imageUrl()) + ","
                    + "\"status\":" + escapeJson(status)
                    + "}";
        }
    }
}
