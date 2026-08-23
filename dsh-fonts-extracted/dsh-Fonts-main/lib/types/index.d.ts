/**
 * dsh-fonts host half types: serves the bundled webfonts over the harness web
 * server; the feature itself lives in the browser half.
 */
import type { Context } from '@deepseek-ai/cordis'

/** Required services (cordis fiber inject). */
export declare const inject: string[]

/** Register the bundled-font route on the web server. */
export declare function apply(ctx: Context): void
