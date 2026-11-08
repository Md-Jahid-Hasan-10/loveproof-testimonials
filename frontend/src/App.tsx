import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { PublicLayout } from './components/PublicLayout'
import { RouteFallback } from './components/RouteFallback'
import HomePage from './pages/HomePage'
import { AdminLayout } from './admin/AdminLayout'
import { AuthProvider } from './admin/AuthContext'
import { RequireAdmin } from './admin/RequireAdmin'
import { AppLayout } from './app/AppLayout'
import { CustomerAuthProvider } from './app/CustomerAuthContext'
import { RequireCustomer } from './app/RequireCustomer'

/**
 * The application shell.
 *
 * The public routes are open. The admin routes sit behind `RequireAdmin`,
 * which sends a signed-out visitor to the sign-in form. The customer routes
 * sit behind `RequireCustomer`, which does the same for customers.
 *
 * The two surfaces have separate session providers. They are not the same
 * session with different permissions: the backend keeps them apart, so the
 * interface keeps them apart too.
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
const CustomerSignInPage = lazy(() => import('./app/CustomerSignInPage'))
const CustomerRegisterPage = lazy(() => import('./app/CustomerRegisterPage'))
const DashboardPage = lazy(() => import('./app/DashboardPage'))
const SpaceWizardPage = lazy(() => import('./app/SpaceWizardPage'))
const SpaceDetailPage = lazy(() => import('./app/SpaceDetailPage'))
const SpaceInboxPage = lazy(() => import('./app/SpaceInboxPage'))
const SpaceEmbedPage = lazy(() => import('./app/SpaceEmbedPage'))
const SpaceSubmitPage = lazy(() => import('./pages/SpaceSubmitPage'))
const BillingPage = lazy(() => import('./app/BillingPage'))

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <CustomerAuthProvider>
          <Suspense fallback={<RouteFallback />}>
            <Routes>
              <Route element={<PublicLayout />}>
                <Route path="/" element={<HomePage />} />
                <Route path="/submit" element={<SubmitTestimonialPage />} />
                <Route path="/wall" element={<TestimonialWallPage />} />
              </Route>

              <Route path="/admin/login" element={<AdminLoginPage />} />

              {/*
                A Space's public page stands on its own: it carries the
                owner's chosen theme, not the LoveProof shell, so it is
                outside `PublicLayout`.
              */}
              <Route path="/s/:slug" element={<SpaceSubmitPage />} />

              <Route element={<RequireAdmin />}>
                <Route element={<AdminLayout />}>
                  <Route path="/admin" element={<AdminDashboardPage />} />
                  <Route path="/admin/inbox" element={<AdminInboxPage />} />
                </Route>
              </Route>

              <Route path="/app/sign-in" element={<CustomerSignInPage />} />
              <Route path="/app/register" element={<CustomerRegisterPage />} />

              <Route element={<RequireCustomer />}>
                <Route element={<AppLayout />}>
                  <Route path="/app" element={<DashboardPage />} />
                  <Route path="/app/spaces/new" element={<SpaceWizardPage />} />
                  <Route
                    path="/app/spaces/:spaceId"
                    element={<SpaceDetailPage />}
                  />
                  <Route
                    path="/app/spaces/:spaceId/inbox"
                    element={<SpaceInboxPage />}
                  />
                  <Route
                    path="/app/spaces/:spaceId/embed"
                    element={<SpaceEmbedPage />}
                  />
                  <Route path="/app/billing" element={<BillingPage />} />
                </Route>
              </Route>

              <Route path="*" element={<HomePage />} />
            </Routes>
          </Suspense>
        </CustomerAuthProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
