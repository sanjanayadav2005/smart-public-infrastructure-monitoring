from pathlib import Path
import os
import sqlite3

from flask import Flask, jsonify, request, send_from_directory, session


BASE_DIR = Path(__file__).resolve().parent
STATIC_DIR = BASE_DIR / "static"
UPLOAD_DIR = STATIC_DIR / "uploads"
FRONTEND_DIST_DIR = STATIC_DIR / "frontend"

app = Flask(__name__, static_folder=str(STATIC_DIR), static_url_path="/static")
app.secret_key = "secret123"
app.config["UPLOAD_FOLDER"] = str(UPLOAD_DIR)


def init_db():
    conn = sqlite3.connect(BASE_DIR / "database.db")
    conn.execute(
        """CREATE TABLE IF NOT EXISTS complaints(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            description TEXT,
            location TEXT,
            image TEXT,
            status TEXT
        )"""
    )
    conn.close()


def get_connection():
    conn = sqlite3.connect(BASE_DIR / "database.db")
    conn.row_factory = sqlite3.Row
    return conn


def serialize_complaint(row):
    return {
        "id": row["id"],
        "description": row["description"],
        "location": row["location"],
        "image": f"/static/uploads/{row['image']}" if row["image"] else None,
        "status": row["status"],
    }


def ensure_upload_folder():
    os.makedirs(app.config["UPLOAD_FOLDER"], exist_ok=True)


def is_admin():
    return bool(session.get("admin"))


@app.route("/api/report-issue", methods=["POST"])
def report_issue():
    desc = request.form.get("desc", "").strip()
    loc = request.form.get("loc", "").strip()
    file = request.files.get("image")

    if not desc or not loc or not file or not file.filename:
        return jsonify({"message": "Description, location, and image are required."}), 400

    ensure_upload_folder()
    filename = file.filename
    path = os.path.join(app.config["UPLOAD_FOLDER"], filename)
    file.save(path)

    conn = get_connection()
    cursor = conn.execute(
        "INSERT INTO complaints(description, location, image, status) VALUES (?, ?, ?, ?)",
        (desc, loc, filename, "Pending"),
    )
    complaint_id = cursor.lastrowid
    conn.commit()
    row = conn.execute("SELECT * FROM complaints WHERE id = ?", (complaint_id,)).fetchone()
    conn.close()

    return (
        jsonify(
            {
                "message": "Submitted successfully.",
                "complaint": serialize_complaint(row),
            }
        ),
        201,
    )


@app.route("/api/admin/login", methods=["POST"])
def admin_login():
    payload = request.get_json(silent=True) or {}
    username = payload.get("username", "").strip()
    password = payload.get("password", "").strip()

    if username == "admin" and password == "1234":
        session["admin"] = True
        return jsonify({"message": "Login successful."})

    return jsonify({"message": "Invalid username or password."}), 401


@app.route("/api/admin/logout", methods=["POST"])
def admin_logout():
    session.pop("admin", None)
    return jsonify({"message": "Logged out."})


@app.route("/api/admin/session", methods=["GET"])
def admin_session():
    return jsonify({"authenticated": is_admin()})


@app.route("/api/complaints", methods=["GET"])
def complaints():
    if not is_admin():
        return jsonify({"message": "Unauthorized."}), 401

    conn = get_connection()
    data = conn.execute("SELECT * FROM complaints ORDER BY id DESC").fetchall()
    conn.close()
    return jsonify({"complaints": [serialize_complaint(row) for row in data]})


@app.route("/api/resolve/<int:complaint_id>", methods=["POST"])
def resolve(complaint_id):
    if not is_admin():
        return jsonify({"message": "Unauthorized."}), 401

    conn = get_connection()
    conn.execute("UPDATE complaints SET status = 'Resolved' WHERE id = ?", (complaint_id,))
    conn.commit()
    row = conn.execute("SELECT * FROM complaints WHERE id = ?", (complaint_id,)).fetchone()
    conn.close()

    if row is None:
        return jsonify({"message": "Complaint not found."}), 404

    return jsonify({"message": "Complaint resolved.", "complaint": serialize_complaint(row)})


@app.route("/", defaults={"path": ""})
@app.route("/<path:path>")
def spa(path):
    requested = FRONTEND_DIST_DIR / path
    if path and requested.exists() and requested.is_file():
        return send_from_directory(FRONTEND_DIST_DIR, path)

    return send_from_directory(FRONTEND_DIST_DIR, "index.html")


if __name__ == "__main__":
    ensure_upload_folder()
    init_db()
    app.run(debug=True)
