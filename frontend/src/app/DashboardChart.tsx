import type { DailyCount } from '../api'

/**
 * The daily submissions chart.
 *
 * <p>The chart is drawn by hand rather than with a charting library: the
 * series is a list of one number per day, and a library would add more weight
 * to the bundle than the picture itself is worth.
 *
 * <p>Every point is also listed in a table below the picture. A chart drawn
 * in SVG is invisible to a screen reader, and the numbers are the point of
 * the chart — the picture is only a way of seeing them at a glance.
 */
interface DashboardChartProps {
  daily: DailyCount[]
  /** The number of days the chart covers, for the heading. */
  rangeDays: number
}

/** The chart's drawing area, in user units. */
const WIDTH = 640
const HEIGHT = 160
/** The room left above the line for the highest point. */
const PADDING_TOP = 12
const PADDING_BOTTOM = 4
const PADDING_LEFT = 4
const PADDING_RIGHT = 4

/**
 * Formats a date for the axis and the table.
 *
 * @param date the ISO date
 * @return a short, readable form
 */
function shortDate(date: string): string {
  const parsed = new Date(`${date}T00:00:00Z`)
  if (Number.isNaN(parsed.getTime())) {
    return date
  }
  return parsed.toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  })
}

/**
 * The chart's data table.
 *
 * <p>Only the days that actually have submissions are listed: a table with
 * thirty empty rows would bury the data it is supposed to make available.
 * The zero days are still in the picture.
 */
function ChartTable({ daily }: { daily: DailyCount[] }) {
  const withSubmissions = daily.filter((day) => day.count > 0)

  if (withSubmissions.length === 0) {
    return (
      <p className="mt-4 text-sm text-slate-600">
        No submissions in this period yet.
      </p>
    )
  }

  return (
    <details className="mt-4">
      <summary className="cursor-pointer text-sm font-medium text-slate-700">
        Show the numbers
      </summary>
      <table className="mt-2 w-full text-left text-sm">
        <caption className="sr-only">
          Testimonials submitted per day
        </caption>
        <thead>
          <tr className="border-b border-slate-200">
            <th scope="col" className="py-1 pr-4 font-medium text-slate-700">
              Day
            </th>
            <th scope="col" className="py-1 font-medium text-slate-700">
              Testimonials
            </th>
          </tr>
        </thead>
        <tbody>
          {withSubmissions.map((day) => (
            <tr key={day.date} className="border-b border-slate-100">
              <th
                scope="row"
                className="py-1 pr-4 font-normal text-slate-700"
              >
                {shortDate(day.date)}
              </th>
              <td className="py-1 text-slate-900">{day.count}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  )
}

/**
 * Draws the daily series.
 *
 * <p>When every day is zero the line would be drawn along the bottom edge,
 * which reads as "no data" rather than "no submissions", so the chart says
 * so in words instead.
 */
export function DashboardChart({ daily, rangeDays }: DashboardChartProps) {
  if (!daily || daily.length === 0) {
    return (
      <p className="text-sm text-slate-600">
        There is no data to chart for this period.
      </p>
    )
  }

  const peak = daily.reduce((highest, day) => Math.max(highest, day.count), 0)

  if (peak === 0) {
    return (
      <div>
        <p className="text-sm text-slate-600">
          No submissions in the last {rangeDays} days.
        </p>
        <ChartTable daily={daily} />
      </div>
    )
  }

  const drawableWidth = WIDTH - PADDING_LEFT - PADDING_RIGHT
  const drawableHeight = HEIGHT - PADDING_TOP - PADDING_BOTTOM
  const step = daily.length > 1 ? drawableWidth / (daily.length - 1) : 0

  const points = daily.map((day, index) => {
    const x = PADDING_LEFT + index * step
    const y =
      PADDING_TOP + drawableHeight - (day.count / peak) * drawableHeight
    return { x, y, ...day }
  })

  const line = points
    .map((point) => `${point.x.toFixed(2)},${point.y.toFixed(2)}`)
    .join(' ')

  return (
    <div>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        className="h-40 w-full"
        role="img"
        aria-label={`Testimonials submitted per day over the last ${rangeDays} days. The busiest day had ${peak}.`}
      >
        <polyline
          points={line}
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinejoin="round"
          strokeLinecap="round"
          className="text-brand-500"
        />
        {points.map((point) => (
          <circle
            key={point.date}
            cx={point.x}
            cy={point.y}
            r={point.count > 0 ? 3 : 0}
            className="fill-brand-600"
          >
            <title>
              {shortDate(point.date)}: {point.count}
            </title>
          </circle>
        ))}
      </svg>

      <p className="mt-1 flex justify-between text-xs text-slate-500">
        <span>{shortDate(daily[0].date)}</span>
        <span>
          {shortDate(daily[daily.length - 1].date)}
        </span>
      </p>

      <ChartTable daily={daily} />
    </div>
  )
}
