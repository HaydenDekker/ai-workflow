import {
  createMenuItems,
  useViewConfig,
} from "@vaadin/hilla-file-router/runtime.js";
import { effect, signal } from "@vaadin/hilla-react-signals";
import { AppLayout, Icon } from "@vaadin/react-components";
import { Suspense, useEffect } from "react";
import { NavLink, Outlet, useLocation } from "react-router";
import "@vaadin/vaadin-lumo-styles/utility.css";

// Flow routes (server-side Vaadin views) — not auto-discovered by Hilla file router
const flowRoutes = [
  { to: "agents", title: "Agent List", icon: "line-awesome/svg/list-solid.svg" },
  { to: "scanners", title: "Scanners", icon: "line-awesome/svg/radar-solid.svg" },
  { to: "observability", title: "Observability", icon: "line-awesome/svg/chart-bar-solid.svg" },
];

const documentTitleSignal = signal("");
effect(() => {
  document.title = documentTitleSignal.value;
});

export default function MainLayout() {
  const currentTitle = useViewConfig()?.title;
  const location = useLocation();

  // Redirect root ("") to the Flow-based Agent List
  useEffect(() => {
    if (location.pathname === "" || location.pathname === "/") {
      window.location.replace("/agents");
    }
  }, [location.pathname]);

  useEffect(() => {
    if (currentTitle) {
      documentTitleSignal.value = currentTitle;
    }
  });

  return (
    <AppLayout>
      <header className="box-border flex flex-col w-full" slot="navbar">
        <div className="flex items-center px-l">
          <h1 className="my-m me-auto text-l">AI Workflow</h1>
        </div>
        <nav className="flex overflow-auto px-m py-xs">
          <ul className="flex gap-s list-none m-0 p-0">
            {createMenuItems().map(({ to, title, icon }) => (
              <li key={"li" + to}>
                <NavLink
                  className="flex gap-xs h-m items-center px-s text-body"
                  to={to}
                  key={to}
                >
                  {icon ? <Icon src={icon}></Icon> : <></>}
                  <span className="font-medium text-m whitespace-nowrap">
                    {title}
                  </span>
                </NavLink>
              </li>
            ))}
            {flowRoutes.map(({ to, title, icon }) => (
              <li key={"flow-li" + to}>
                <a
                  className="flex gap-xs h-m items-center px-s text-body"
                  href={to}
                  key={to}
                >
                  {icon ? <Icon src={icon}></Icon> : <></>}
                  <span className="font-medium text-m whitespace-nowrap">
                    {title}
                  </span>
                </a>
              </li>
            ))}
          </ul>
        </nav>
      </header>

      <Suspense>
        <Outlet />
      </Suspense>
    </AppLayout>
  );
}
