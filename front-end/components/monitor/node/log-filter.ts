/** Options of the log tail's grep box. */
export type FilterOptions = {
  caseSensitive: boolean;
  regex: boolean;
  /** Keep the lines that do NOT match, like `grep -v`. */
  invert: boolean;
};

export type Matcher = {
  test: (text: string) => boolean;
  /** Global pattern for highlighting; null when inverted, as kept lines hold no match. */
  highlight: RegExp | null;
};

/** A text segment of a line, flagged when it is part of a match. */
export type Segment = { text: string; match: boolean };

const escapeRegExp = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

/**
 * Compile the grep box into a matcher: null when empty (show everything),
 * "invalid" when the regex does not parse.
 */
export function compileFilter(query: string, opts: FilterOptions): Matcher | null | "invalid" {
  if (!query) return null;
  const flags = opts.caseSensitive ? "" : "i";
  let once: RegExp;
  try {
    once = new RegExp(opts.regex ? query : escapeRegExp(query), flags);
  } catch {
    return "invalid";
  }
  return {
    // `once` has no g flag, so test() keeps no lastIndex between lines
    test: (text) => once.test(text) !== opts.invert,
    highlight: opts.invert ? null : new RegExp(once.source, `${flags}g`),
  };
}

/** Split `text` into matched and unmatched segments, in order. */
export function highlightSegments(text: string, pattern: RegExp): Segment[] {
  const out: Segment[] = [];
  let last = 0;
  for (const m of text.matchAll(pattern)) {
    // A pattern like `a*` also matches the empty string everywhere: nothing to mark
    if (!m[0]) continue;
    if (m.index > last) out.push({ text: text.slice(last, m.index), match: false });
    out.push({ text: m[0], match: true });
    last = m.index + m[0].length;
  }
  if (last < text.length) out.push({ text: text.slice(last), match: false });
  return out;
}
