import { Outlet, createRootRoute } from '@tanstack/react-router'

import '../styles.css'

export const Route = createRootRoute({
  component: RootComponent,
})

function RootComponent() {
  return (
    <>
      {/* Global font: "Inter Tight" (weights 200–900) — sole font for the whole page */}
      <link rel="preconnect" href="https://fonts.googleapis.com" />
      <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
      <link
        rel="stylesheet"
        href="https://fonts.googleapis.com/css2?family=Inter+Tight:wght@200..900&display=swap"
      />
      <Outlet />
    </>
  )
}
