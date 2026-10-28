import { Outlet, createRootRoute } from '@tanstack/react-router'

import '../styles.css'

export const Route = createRootRoute({
  component: RootComponent,
})

function RootComponent() {
  return (
    <>
      {/* 全局字体 "Inter Tight"（可变字重 200–900）已自托管，
          @font-face 声明在 ../styles.css —— 这里不再挂任何外部 <link>。 */}
      <Outlet />
    </>
  )
}
