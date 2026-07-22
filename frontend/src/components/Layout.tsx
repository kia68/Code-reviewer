import { Link, Outlet } from "react-router";
import "./Layout.css";

export function Layout() {
  return (
    <div className="layout">
      <header className="layout-header">
        <Link to="/" className="layout-logo">
          Code Reviewer
        </Link>
      </header>
      <main className="layout-main">
        <Outlet />
      </main>
    </div>
  );
}
