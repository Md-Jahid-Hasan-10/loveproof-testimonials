import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { PublicLayout } from './components/PublicLayout'
import HomePage from './pages/HomePage'
import SubmitTestimonialPage from './pages/SubmitTestimonialPage'
import TestimonialWallPage from './pages/TestimonialWallPage'
import { AdminLayout } from './admin/AdminLayout'
import AdminDashboardPage from './admin/AdminDashboardPage'
import AdminInboxPage from './admin/AdminInboxPage'
import AdminLoginPage from './admin/AdminLoginPage'
import { AuthProvider } from './admin/AuthContext'
import { RequireAdmin } from './admin/RequireAdmin'

/**
 * The application shell.
 *
 * The public routes are open. The admin routes sit behind `RequireAdmin`,
 * which sends a signed-out visitor to the sign-in form.
 */
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
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
      </AuthProvider>
    </BrowserRouter>
  )
}
