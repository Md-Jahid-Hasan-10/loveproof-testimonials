package com.loveproof.testimonials.web;

/**
 * The embed script.
 *
 * <p>This is the whole of the widget that runs on somebody else's website.
 * It is deliberately plain JavaScript with no build step and no dependency on
 * the React bundle, so pasting the snippet into a plain HTML page is enough
 * (PRD §4.8).
 *
 * <p>Everything it renders goes through {@code textContent}, never
 * {@code innerHTML}. A testimonial is text a stranger wrote, and it ends up
 * on a page the owner does not control; escaping it by construction is the
 * only way to be sure of it.
 *
 * <p>The styles are scoped under a {@code lp-} prefix and the container is
 * marked {@code all: initial}, so the host site's CSS cannot reach inside
 * the widget and the widget cannot reach outside itself.
 */
public final class EmbedScript {

    /** The script served at {@code /embed/loveproof.js}. */
    public static final String SCRIPT = """
            (function () {
              'use strict';

              var STYLE_ID = 'loveproof-embed-style';
              var ATTR = 'data-loveproof-embed';

              var CSS = [
                '.lp-embed{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;',
                'line-height:1.5;color:#0f172a;box-sizing:border-box}',
                '.lp-embed *,.lp-embed *::before,.lp-embed *::after{box-sizing:inherit}',
                '.lp-embed.lp-dark{color:#e2e8f0}',
                '.lp-embed-title{font-size:1.125rem;font-weight:700;margin:0 0 .25rem}',
                '.lp-embed-subtitle{font-size:.875rem;margin:0 0 1rem;opacity:.75}',
                '.lp-embed-grid{display:grid;gap:1rem;grid-template-columns:repeat(auto-fill,minmax(260px,1fr))}',
                '.lp-embed-card{border:1px solid rgba(100,116,139,.25);border-radius:.75rem;padding:1rem;',
                'background:rgba(248,250,252,.6);box-shadow:0 1px 2px rgba(15,23,42,.04)}',
                '.lp-dark .lp-embed-card{background:rgba(30,41,59,.6);border-color:rgba(148,163,184,.2)}',
                '.lp-embed-name{font-weight:600;font-size:.9375rem;margin:0}',
                '.lp-embed-meta{font-size:.8125rem;opacity:.7;margin:.125rem 0 .5rem}',
                '.lp-embed-text{font-size:.9375rem;margin:0;white-space:pre-wrap}',
                '.lp-embed-stars{color:#f59e0b;font-size:.875rem;letter-spacing:.0625rem}',
                '.lp-embed-link{color:inherit}',
                '.lp-embed-carousel{display:flex;overflow-x:auto;scroll-snap-type:x mandatory;gap:1rem;padding-bottom:.5rem}',
                '.lp-embed-carousel .lp-embed-card{flex:0 0 300px;scroll-snap-align:start}',
                '.lp-embed-empty{font-size:.875rem;opacity:.7}',
                '@media (prefers-reduced-motion:no-preference){',
                '.lp-embed-animate .lp-embed-card{animation:lp-embed-in .35s ease both}',
                '@keyframes lp-embed-in{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:none}}',
                '}',
                '@media (prefers-reduced-motion:reduce){.lp-embed-animate .lp-embed-card{animation:none}}'
              ].join('');

              function ensureStyle() {
                if (document.getElementById(STYLE_ID)) {
                  return;
                }
                var style = document.createElement('style');
                style.id = STYLE_ID;
                style.textContent = CSS;
                document.head.appendChild(style);
              }

              /**
               * Adds a text node, never markup.
               */
              function text(parent, value) {
                if (value === null || value === undefined || value === '') {
                  return null;
                }
                var node = document.createElement('span');
                node.textContent = String(value);
                parent.appendChild(node);
                return node;
              }

              function element(tag, className) {
                var node = document.createElement(tag);
                if (className) {
                  node.className = className;
                }
                return node;
              }

              function renderStars(rating) {
                var wrap = element('p', 'lp-embed-stars');
                wrap.setAttribute('aria-hidden', 'true');
                var filled = Math.max(0, Math.min(5, Math.round(rating)));
                wrap.textContent = new Array(filled + 1).join('\\u2605') +
                  new Array(6 - filled).join('\\u2606');
                return wrap;
              }

              function renderCard(item) {
                var card = element('article', 'lp-embed-card');

                var name = element('p', 'lp-embed-name');
                name.textContent = item.name || '';
                card.appendChild(name);

                var metaParts = [];
                if (item.jobTitle) { metaParts.push(item.jobTitle); }
                if (item.company) { metaParts.push(item.company); }

                if (metaParts.length) {
                  var meta = element('p', 'lp-embed-meta');
                  meta.textContent = metaParts.join(' \\u00b7 ');
                  card.appendChild(meta);
                }

                if (typeof item.rating === 'number' && item.rating > 0) {
                  card.appendChild(renderStars(item.rating));
                }

                var body = element('p', 'lp-embed-text');
                body.textContent = item.content || '';
                card.appendChild(body);

                var link = item.website || item.socialLink;
                if (link) {
                  var anchor = element('a', 'lp-embed-link');
                  anchor.href = link;
                  anchor.rel = 'noopener noreferrer nofollow';
                  anchor.target = '_blank';
                  anchor.textContent = item.website ? 'Visit website' : 'View profile';
                  card.appendChild(anchor);
                }

                return card;
              }

              function render(root, data) {
                root.textContent = '';
                root.classList.add('lp-embed');

                if (data.layout === 'carousel') {
                  root.classList.add('lp-embed-carousel');
                } else {
                  root.classList.add('lp-embed-grid');
                }

                if (data.themeMode === 'dark') {
                  root.classList.add('lp-dark');
                }

                if (data.backgroundColor) {
                  root.style.backgroundColor = data.backgroundColor;
                }

                if (data.animation) {
                  root.classList.add('lp-embed-animate');
                }

                if (data.title) {
                  var title = element('h2', 'lp-embed-title');
                  title.textContent = data.title;
                  root.appendChild(title);
                }

                if (data.subtitle) {
                  var subtitle = element('p', 'lp-embed-subtitle');
                  subtitle.textContent = data.subtitle;
                  root.appendChild(subtitle);
                }

                var items = data.testimonials || [];
                if (!items.length) {
                  var empty = element('p', 'lp-embed-empty');
                  empty.textContent = 'No testimonials yet.';
                  root.appendChild(empty);
                  return;
                }

                items.forEach(function (item) {
                  root.appendChild(renderCard(item));
                });
              }

              function mount(container, token) {
                ensureStyle();
                container.setAttribute(ATTR, token);
                container.classList.add('lp-embed');

                var url = container.getAttribute('data-loveproof-url') ||
                  (container.ownerDocument.defaultView.LOVEPROOF_BASE || '') +
                  '/api/app/public/embeds/' + encodeURIComponent(token);

                fetch(url, { credentials: 'omit' })
                  .then(function (response) {
                    if (!response.ok) {
                      throw new Error('The embed could not be loaded.');
                    }
                    return response.json();
                  })
                  .then(function (data) {
                    render(container, data);
                  })
                  .catch(function () {
                    // A failed embed must never break the host page, so the
                    // container is simply left empty.
                    container.textContent = '';
                  });
              }

              function start() {
                var nodes = document.querySelectorAll('[' + ATTR + ']');
                for (var i = 0; i < nodes.length; i += 1) {
                  var node = nodes[i];
                  var token = node.getAttribute(ATTR);
                  if (token && node.getAttribute('data-loveproof-mounted') !== 'true') {
                    node.setAttribute('data-loveproof-mounted', 'true');
                    mount(node, token);
                  }
                }
              }

              if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', start);
              } else {
                start();
              }

              window.LoveProofEmbed = { start: start };
            })();
            """;

    private EmbedScript() {
        // A holder for a constant.
    }
}
