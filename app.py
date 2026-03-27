from flask import Flask, render_template, request, redirect, session, url_for
import sqlite3
import os

app = Flask(__name__)
app.secret_key = "secret123"

UPLOAD_FOLDER = "static/uploads"
app.config["UPLOAD_FOLDER"] = UPLOAD_FOLDER

# DB INIT
def init_db():
    conn = sqlite3.connect("database.db")
    conn.execute('''CREATE TABLE IF NOT EXISTS complaints(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        description TEXT,
        location TEXT,
        image TEXT,
        status TEXT
    )''')
    conn.close()

@app.route('/')
def home():
    return render_template("index.html")

# REPORT ISSUE
@app.route('/report-issue', methods=["GET", "POST"])
def report():
    if request.method == "POST":
        desc = request.form["desc"]
        loc = request.form["loc"]
        file = request.files["image"]

        filename = file.filename
        path = os.path.join(app.config["UPLOAD_FOLDER"], filename)
        file.save(path)

        conn = sqlite3.connect("database.db")
        conn.execute("INSERT INTO complaints(description,location,image,status) VALUES (?,?,?,?)",
                     (desc, loc, filename, "Pending"))
        conn.commit()
        conn.close()

        return "Submitted Successfully!"

    return render_template("report.html")

# ADMIN LOGIN
@app.route('/admin/login', methods=["GET","POST"])
def admin_login():
    if request.method == "POST":
        username = request.form["username"]
        password = request.form["password"]

        if username == "admin" and password == "1234":
            session["admin"] = True
            return redirect("/admin/dashboard")

    return render_template("admin_login.html")

# DASHBOARD
@app.route('/admin/dashboard')
def dashboard():
    if not session.get("admin"):
        return redirect("/admin/login")

    conn = sqlite3.connect("database.db")
    data = conn.execute("SELECT * FROM complaints").fetchall()
    conn.close()

    return render_template("admin_dashboard.html", data=data)

# UPDATE STATUS
@app.route('/resolve/<int:id>')
def resolve(id):
    conn = sqlite3.connect("database.db")
    conn.execute("UPDATE complaints SET status='Resolved' WHERE id=?", (id,))
    conn.commit()
    conn.close()
    return redirect("/admin/dashboard")

if __name__ == "__main__":
    init_db()
    app.run(debug=True)