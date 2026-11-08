import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { PublicLayout } from './components/PublicLayout'
import { RouteFallback } from './components/RouteFallback'
import HomePage from './pages/HomePage'
import { AdminLayout } from './admin/AdminLayout'
import { AuthProvider } from './admin/AuthContext'
import { RequireAdmin } from './admin/RequireAdmin'

/**
 * The application shell.
 *
 * The public routes are open. The admin routes sit behind `RequireAdmin`,
 * which sends a signed-out visitor to the sign-in form.
 *
 * The pages are loaded lazily, so the first paint only carries the code the
 * landing page needs. The admin surface in particular is code nobody but a
 * moderator will ever run, and it should not be in the bundle a visitor
 * downloads.
 */
const SubmitTestimonialPage = lazy(() => import('./pages/SubmitTestimonialPage'))
const TestimonialWallPage = lazy(() => import('./pages/TestimonialWallPage'))
const AdminDashboardPage = lazy(() => import('./admin/AdminDashboardPage'))
const AdminInboxPage = lazy(() => import('./admin/AdminInboxPage'))
const AdminLoginPage = lazy(() => import('./admin/AdminLoginPage'))

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Suspense fallback={<RouteFallback />}>
          <Routes>
            <Route element={<PublicLayout />}>
              <Route path="/" element={<HomePage />} />
              <Route path="/submit" element={<SubmitTestimonialPage />} />
              <Route path="/wall" element={<TestimonialWallPage />} />
            </Route>

            <Route path="/admin/login" element={<AdminLoginPage />} />

            <Route element={<RequireAdmin />}>
              <Route element={<AdminLayout />}>
                <Route path="/admin" element={<AdminDashboardPage />} />
                <Route path="/admin/inbox" element={<AdminInboxPage />} />
              </Route>
            </Route>

            <Route path="*" element={<HomePage />} />
          </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  )
}
