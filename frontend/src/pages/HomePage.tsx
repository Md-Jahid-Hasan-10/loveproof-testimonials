import { Link } from 'react-router-dom'

/**
 * The landing page.
 *
 * It points at the two public routes: the wall, where the approved
 * testimonials are published, and the submission form.
 */
export default function HomePage() {
  return (
    <div className="flex flex-col gap-8">
      <section className="flex flex-col gap-4">
        <h1 className="text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl">
          Testimonials that build trust
        </h1>
        <p className="max-w-2xl text-lg text-slate-600">
          LoveProof collects customer testimonials, moderates them before they go
          live, and publishes the approved ones on your wall.
        </p>
        <div className="flex flex-wrap gap-3">
          <Link
            to="/wall"
            className="inline-flex items-center justify-center rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700"
          >
            Read the wall
          </Link>
          <Link
            to="/submit"
            className="inline-flex items-center justify-center rounded-lg bg-white px-4 py-2.5 text-sm font-semibold text-slate-900 ring-1 ring-inset ring-slate-300 hover:bg-slate-50"
          >
            Share your story
          </Link>
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="text-lg font-semibold text-slate-900">How it works</h2>
        <ol className="mt-4 flex list-decimal flex-col gap-3 pl-5 text-slate-600">
          <li>You submit your testimonial — it stays private for now.</li>
          <li>A moderator reviews it.</li>
          <li>Approved testimonials are published on the public wall.</li>
        </ol>
      </section>
    </div>
  )
}
