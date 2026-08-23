/**
 * dsh-fonts client half types: the font registry (provided as `ctx.fonts`),
 * the font preset model, and the client plugin body.
 */
import type { Context } from '@deepseek-ai/cordis'

/** One webfont face (a @font-face rule body). */
export interface FontFaceSpec {
  /** font-family name. */
  family: string
  /** woff2 source URLs (bundled faces point at the plugin's host route). */
  src: string[]
  /** CSS font-weight; defaults to 400. */
  weight?: string
  /** CSS font-display; defaults to 'swap'. */
  display?: 'swap' | 'auto'
}

/** One selectable font preset: UI and code stacks plus their faces. */
export interface FontPreset {
  id: string
  label?: string
  /** --dsw-font-family stack (family names, no commas needed). */
  ui: string[]
  /** --ds-font-family-code stack. */
  code: string[]
  faces: FontFaceSpec[]
}

/** The user-imported custom set (independent of any preset). */
export interface CustomFontSet {
  ui: FontFaceSpec[]
  code: FontFaceSpec[]
}

/** One immutable registry snapshot published on every change. */
export interface FontSnapshot {
  /** "system" / "custom" / a preset id. */
  activeId: string | null
  presets: readonly FontPreset[]
  custom: CustomFontSet | null
  /** Monotonic change counter. */
  revision: number
}

/**
 * The font registry — the plugin's public interface. Provided to the root
 * context as `ctx.fonts`; other plugins consume it lazily via
 * `ctx.get("fonts")` (cross-plugin requires are a build error).
 */
export interface FontRegistry {
  /** Register a plugin-provided preset; returns its disposer. */
  register(preset: FontPreset): () => void
  unregister(id: string): void
  /** Select a preset id; "system" and "custom" are built-in ids. */
  select(id: string): void
  /** Apply a user-imported custom set (empty lists clear it). */
  selectCustom(ui: FontFaceSpec[], code: FontFaceSpec[]): void
  clearCustom(): void
  getSnapshot(): FontSnapshot
  subscribe(listener: (snapshot: FontSnapshot) => void): () => void
}

declare module '@deepseek-ai/cordis' {
  interface Context {
    /** Provided by dsh-fonts' browser half. */
    fonts: FontRegistry
  }
}

/** The settings row's locale namespace. */
export declare const SETTINGS_NS: 'settings.fonts'

/** Sentinel id meaning "the shipped system stacks". */
export declare const DEFAULT_ID: 'system'

/** Required services (cordis fiber inject). */
export declare const inject: string[]

/** Client plugin body: provide the registry, restore the saved selection, mount the picker row. */
export declare function apply(ctx: Context): void
