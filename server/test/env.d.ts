declare namespace Cloudflare {
  interface Env extends import("../src/index").Env {}
  interface GlobalProps { mainModule: typeof import("../src/index") }
}
