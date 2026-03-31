import { useEffect, useState } from "react";
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";

const navItems = [
  { to: "/", label: "Home" },
  { to: "/report-issue", label: "Report" },
  { to: "/admin/login", label: "Admin" }
];

function Shell({ children, authenticated, onLogout }) {
  const location = useLocation();

  return (
    <div className="app-shell">
      <div className="bg-orb orb-a" />
      <div className="bg-orb orb-b" />
      <header className="topbar">
        <Link className="brand" to="/">
          CivicWatch
        </Link>
        <nav className="nav-links">
          {navItems.map((item) => (
            <Link
              key={item.to}
              className={location.pathname === item.to ? "nav-link active" : "nav-link"}
              to={item.to}
            >
              {item.label}
            </Link>
          ))}
          {authenticated ? (
            <>
              <Link
                className={location.pathname === "/admin/dashboard" ? "nav-link active" : "nav-link"}
                to="/admin/dashboard"
              >
                Dashboard
              </Link>
              <button className="ghost-button" onClick={onLogout} type="button">
                Logout
              </button>
            </>
          ) : null}
        </nav>
      </header>
      <main>{children}</main>
    </div>
  );
}

function HomePage() {
  return (
    <section className="hero-section">
      <div className="hero-copy">
        <p className="eyebrow">Smart public infrastructure monitoring</p>
        <h1>Spot an issue. Report it fast. Help the city respond better.</h1>
        <p className="lede">
          CivicWatch helps residents report potholes, garbage, broken lights, and similar urban
          issues through one clean dashboard.
        </p>
        <div className="hero-actions">
          <Link className="primary-button" to="/report-issue">
            Report an issue
          </Link>
          <Link className="secondary-button" to="/admin/login">
            Admin access
          </Link>
        </div>
      </div>

      <div className="feature-grid">
        <article className="panel feature-card">
          <span className="feature-icon">01</span>
          <h2>Resident reporting</h2>
          <p>Submit details, location, and an image in one place from a responsive form.</p>
        </article>
        <article className="panel feature-card">
          <span className="feature-icon">02</span>
          <h2>Admin triage</h2>
          <p>Teams can review incoming complaints, track statuses, and resolve cases quickly.</p>
        </article>
      </div>
    </section>
  );
}

function ReportPage() {
  const [formData, setFormData] = useState({ desc: "", loc: "", image: null });
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const handleChange = (event) => {
    const { name, value, files } = event.target;
    setFormData((current) => ({
      ...current,
      [name]: files ? files[0] : value
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    setMessage("");

    const payload = new FormData();
    payload.append("desc", formData.desc);
    payload.append("loc", formData.loc);
    if (formData.image) {
      payload.append("image", formData.image);
    }

    try {
      const response = await fetch("/api/report-issue", {
        method: "POST",
        body: payload
      });
      const result = await response.json();

      if (!response.ok) {
        throw new Error(result.message || "Unable to submit issue.");
      }

      setMessage(result.message);
      setFormData({ desc: "", loc: "", image: null });
      event.target.reset();
    } catch (submissionError) {
      setError(submissionError.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="page-wrap">
      <div className="panel form-panel">
        <p className="eyebrow">Resident form</p>
        <h1>Report infrastructure issue</h1>
        <p className="section-copy">
          Send a short description, the location, and a supporting image for the issue.
        </p>
        <form className="stack-form" onSubmit={handleSubmit}>
          <label>
            Description
            <textarea
              name="desc"
              placeholder="Describe the issue in detail..."
              value={formData.desc}
              onChange={handleChange}
              required
            />
          </label>
          <label>
            Location
            <input
              name="loc"
              placeholder="Enter location"
              type="text"
              value={formData.loc}
              onChange={handleChange}
              required
            />
          </label>
          <label>
            Image
            <input accept="image/*" name="image" type="file" onChange={handleChange} required />
          </label>
          <button className="primary-button" disabled={submitting} type="submit">
            {submitting ? "Submitting..." : "Submit issue"}
          </button>
        </form>
        {message ? <p className="feedback success">{message}</p> : null}
        {error ? <p className="feedback error">{error}</p> : null}
      </div>
    </section>
  );
}

function LoginPage({ authenticated, onAuthenticated }) {
  const navigate = useNavigate();
  const [credentials, setCredentials] = useState({ username: "", password: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (authenticated) {
      navigate("/admin/dashboard");
    }
  }, [authenticated, navigate]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      const response = await fetch("/api/admin/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(credentials)
      });
      const result = await response.json();

      if (!response.ok) {
        throw new Error(result.message || "Login failed.");
      }

      onAuthenticated(true);
      navigate("/admin/dashboard");
    } catch (loginError) {
      setError(loginError.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="page-wrap">
      <div className="panel form-panel compact-panel">
        <p className="eyebrow">Admin login</p>
        <h1>Secure operator access</h1>
        <p className="section-copy">Use the existing demo credentials to enter the dashboard.</p>
        <form className="stack-form" onSubmit={handleSubmit}>
          <label>
            Username
            <input
              name="username"
              placeholder="admin"
              type="text"
              value={credentials.username}
              onChange={(event) =>
                setCredentials((current) => ({ ...current, username: event.target.value }))
              }
              required
            />
          </label>
          <label>
            Password
            <input
              name="password"
              placeholder="Enter password"
              type="password"
              value={credentials.password}
              onChange={(event) =>
                setCredentials((current) => ({ ...current, password: event.target.value }))
              }
              required
            />
          </label>
          <button className="primary-button" disabled={submitting} type="submit">
            {submitting ? "Signing in..." : "Login"}
          </button>
        </form>
        <p className="hint">Demo credentials: admin / 1234</p>
        {error ? <p className="feedback error">{error}</p> : null}
      </div>
    </section>
  );
}

function DashboardPage({ authenticated }) {
  const navigate = useNavigate();
  const [complaints, setComplaints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadComplaints = async () => {
    setLoading(true);
    setError("");

    try {
      const response = await fetch("/api/complaints");
      const result = await response.json();

      if (response.status === 401) {
        navigate("/admin/login");
        return;
      }

      if (!response.ok) {
        throw new Error(result.message || "Unable to load complaints.");
      }

      setComplaints(result.complaints);
    } catch (loadError) {
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!authenticated) {
      navigate("/admin/login");
      return;
    }

    loadComplaints();
  }, [authenticated]);

  const handleResolve = async (complaintId) => {
    try {
      const response = await fetch(`/api/resolve/${complaintId}`, { method: "POST" });
      const result = await response.json();

      if (!response.ok) {
        throw new Error(result.message || "Unable to resolve complaint.");
      }

      setComplaints((current) =>
        current.map((complaint) =>
          complaint.id === complaintId ? result.complaint : complaint
        )
      );
    } catch (resolveError) {
      setError(resolveError.message);
    }
  };

  if (!authenticated) {
    return <Navigate replace to="/admin/login" />;
  }

  return (
    <section className="page-wrap">
      <div className="panel dashboard-panel">
        <div className="dashboard-header">
          <div>
            <p className="eyebrow">Operations dashboard</p>
            <h1>Reported complaints</h1>
          </div>
          <button className="secondary-button" onClick={loadComplaints} type="button">
            Refresh
          </button>
        </div>

        {loading ? <p className="section-copy">Loading complaints...</p> : null}
        {error ? <p className="feedback error">{error}</p> : null}

        {!loading && complaints.length === 0 ? (
          <p className="section-copy">No complaints submitted yet.</p>
        ) : null}

        {!loading && complaints.length > 0 ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Description</th>
                  <th>Location</th>
                  <th>Image</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {complaints.map((complaint) => (
                  <tr key={complaint.id}>
                    <td>{complaint.id}</td>
                    <td>{complaint.description}</td>
                    <td>{complaint.location}</td>
                    <td>
                      {complaint.image ? (
                        <img alt={`Complaint ${complaint.id}`} className="thumb" src={complaint.image} />
                      ) : (
                        "No image"
                      )}
                    </td>
                    <td>
                      <span
                        className={
                          complaint.status === "Resolved" ? "status-pill resolved" : "status-pill pending"
                        }
                      >
                        {complaint.status}
                      </span>
                    </td>
                    <td>
                      <button
                        className="ghost-button"
                        disabled={complaint.status === "Resolved"}
                        onClick={() => handleResolve(complaint.id)}
                        type="button"
                      >
                        {complaint.status === "Resolved" ? "Resolved" : "Resolve"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </div>
    </section>
  );
}

export default function App() {
  const [authenticated, setAuthenticated] = useState(false);

  useEffect(() => {
    const checkSession = async () => {
      try {
        const response = await fetch("/api/admin/session");
        const result = await response.json();
        setAuthenticated(Boolean(result.authenticated));
      } catch (error) {
        setAuthenticated(false);
      }
    };

    checkSession();
  }, []);

  const handleLogout = async () => {
    await fetch("/api/admin/logout", { method: "POST" });
    setAuthenticated(false);
  };

  return (
    <Shell authenticated={authenticated} onLogout={handleLogout}>
      <Routes>
        <Route element={<HomePage />} path="/" />
        <Route element={<ReportPage />} path="/report-issue" />
        <Route
          element={<LoginPage authenticated={authenticated} onAuthenticated={setAuthenticated} />}
          path="/admin/login"
        />
        <Route element={<DashboardPage authenticated={authenticated} />} path="/admin/dashboard" />
        <Route element={<Navigate replace to="/" />} path="*" />
      </Routes>
    </Shell>
  );
}
