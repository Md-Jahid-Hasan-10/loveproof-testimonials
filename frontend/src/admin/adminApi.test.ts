import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  approveTestimonial,
  deleteTestimonial,
  getDashboardStats,
  getSession,
  listInbox,
  rejectTestimonial,
  restoreTestimonial,
  signIn,
  signOut,
} from '../api'

/**
 * The admin API client.
 *
 * These tests pin the wire contract: the paths, the methods, the bodies and
 * the CSRF header. The CSRF header matters most — the backend refuses any
 * state-changing admin request that arrives without it, so a regression here
 * would break every moderation button at once.
 */

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

describe('the admin API client', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    // The CSRF token is read from a readable cookie.
    document.cookie = 'XSRF-TOKEN=test-token'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('signs in by posting the credentials', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        username: 'admin',
        displayName: 'LoveProof Admin',
        role: 'ADMIN',
        lastLoginAt: null,
      }),
    )

    const session = await signIn('admin', 'loveproof')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/session')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({
      username: 'admin',
      password: 'loveproof',
    })
    expect(session.username).toBe('admin')
  })

  it('reads the current session', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ username: 'admin' }))

    await getSession()

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/session')
    expect(options.method).toBe('GET')
  })

  it('signs out with a DELETE', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await signOut()

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/session')
    expect(options.method).toBe('DELETE')
  })

  it('sends the CSRF token on state-changing calls', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await signOut()

    const [, options] = fetchMock.mock.calls[0]
    expect(options.headers['X-XSRF-TOKEN']).toBe('test-token')
  })

  it('does not send the CSRF token on reads', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}))

    await getSession()

    const [, options] = fetchMock.mock.calls[0]
    expect(options.headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('reads the dashboard counts', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        total: 3,
        pending: 1,
        approved: 1,
        rejected: 1,
        deleted: 1,
        last7Days: 2,
        averageRating: 4.5,
      }),
    )

    const stats = await getDashboardStats()

    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/testimonials/stats')
    expect(stats.pending).toBe(1)
    expect(stats.averageRating).toBe(4.5)
  })

  it('lists the inbox with a status filter and a search', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ content: [], page: 0, size: 20, total: 0, totalPages: 0 }),
    )

    await listInbox({ status: 'PENDING', q: 'alice', page: 0, size: 20 })

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe(
      '/api/admin/testimonials?status=PENDING&q=alice&page=0&size=20',
    )
  })

  it('omits the search when it is empty', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ content: [], page: 0, size: 20, total: 0, totalPages: 0 }),
    )

    await listInbox({ status: 'APPROVED', q: '' })

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/testimonials?status=APPROVED')
  })

  it('approves a testimonial', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: 'abc', status: 'APPROVED' }))

    await approveTestimonial('abc')

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/testimonials/abc/approve')
    expect(options.method).toBe('POST')
  })

  it('rejects a testimonial with a reason', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: 'abc', status: 'REJECTED' }))

    await rejectTestimonial('abc', 'Off topic.')

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/testimonials/abc/reject')
    expect(JSON.parse(options.body)).toEqual({ reason: 'Off topic.' })
  })

  it('deletes a testimonial', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: 'abc' }))

    await deleteTestimonial('abc')

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/testimonials/abc')
    expect(options.method).toBe('DELETE')
  })

  it('restores a deleted testimonial', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: 'abc' }))

    await restoreTestimonial('abc')

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/testimonials/abc/restore')
    expect(options.method).toBe('POST')
  })

  it('raises an ApiError carrying the server error envelope', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        {
          code: 'VALIDATION_FAILED',
          message: 'The request was rejected.',
          fieldErrors: { reason: 'A rejection reason is required.' },
          timestamp: '2026-01-01T00:00:00Z',
          path: '/api/admin/testimonials/abc/reject',
        },
        400,
      ),
    )

    await expect(rejectTestimonial('abc', '')).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
      code: 'VALIDATION_FAILED',
      fieldErrors: { reason: 'A rejection reason is required.' },
    })
  })
})
