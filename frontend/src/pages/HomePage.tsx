import { Link } from 'react-router-dom'
import { Button } from '../components/Button'

/**
 * The landing page.
 *
 * It points at the two public routes: the wall, where the approved
 * testimonials are published, and the submission form.
 */

/** The three steps, so the copy and the layout cannot drift apart. */
const STEPS = [
  {
    title: 'They send their words',
    body: 'A short form collects the testimonial, the rating and the details you want alongside it.',
  },
  {
    title: 'You choose what to show',
    body: 'Favourite a testimonial, hide one, or put it on the Wall of Love — all from your dashboard.',
  },
  {
    title: 'You publish it',
    body: 'Drop the embed snippet into your own site, and the wall stays current on its own.',
  },
]

export default function HomePage() {
  return (
    <div className="flex flex-col gap-14">
      <section className="flex flex-col gap-6">
        <span className="pill w-fit bg-brand-50 text-brand-700">
          Customer testimonials
        </span>

        <h1 className="max-w-3xl text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl">
          Testimonials that build trust
        </h1>

        <p className="max-w-2xl text-lg leading-relaxed text-slate-600">
          LoveProof collects customer testimonials, moderates them before they go
          live, and publishes the approved ones on your wall.
        </p>

        <div className="flex flex-wrap gap-3">
          <Link to="/wall">
            <Button>Read the wall</Button>
          </Link>
          <Link to="/submit">
            <Button variant="secondary">Share your story</Button>
          </Link>
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        {STEPS.map((step, index) => (
          <div
            key={step.title}
            className="surface-raised p-6 transition-shadow duration-200 hover:shadow-lifted"
          >
            <span
              aria-hidden="true"
              className="flex h-9 w-9 items-center justify-center rounded-[0.625rem] bg-gradient-to-br from-brand-500 to-brand-700 text-sm font-bold text-white shadow-brand"
            >
              {index + 1}
            </span>
            <h2 className="mt-4 text-base font-semibold tracking-tight text-slate-900">
              {step.title}
            </h2>
            <p className="mt-1.5 text-sm leading-relaxed text-slate-600">
              {step.body}
            </p>
          </div>
        ))}
      </section>

      <section className="surface-raised overflow-hidden">
        <div className="border-b border-slate-200/80 bg-gradient-to-r from-brand-50/80 to-indigo-50/60 px-6 py-5">
          <h2 className="section-title">How it works</h2>
          <p className="section-subtitle">
            Three steps from a happy customer to a published quote.
          </p>
        </div>
        <ol className="flex list-decimal flex-col gap-3 px-6 py-5 pl-10 text-slate-600">
          <li>You submit your testimonial — it stays private for now.</li>
          <li>A moderator reviews it.</li>
          <li>Approved testimonials are published on the public wall.</li>
        </ol>
      </section>
    </div>
  )
}
