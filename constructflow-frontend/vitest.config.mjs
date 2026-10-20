import { defineConfig, transformWithOxc } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

/**
 * Next.js lets `.js` files contain JSX; Vite only parses JSX in `.jsx`. This pre-transform compiles our
 * own `src/**\/*.js` files as JSX so components can be tested without renaming them.
 */
const jsxInJs = {
  name: "constructflow:jsx-in-js",
  enforce: "pre",
  async transform(code, id) {
    if (!/[\\/]src[\\/].*\.js$/.test(id) || id.includes("node_modules")) return null;
    return transformWithOxc(code, id, { lang: "jsx", jsx: { runtime: "automatic" } });
  },
};

export default defineConfig({
  plugins: [jsxInJs, react()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./vitest.setup.js"],
    include: ["src/**/*.test.{js,jsx}"],
    css: false,
  },
});
