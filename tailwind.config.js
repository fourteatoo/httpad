/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./resources/public/index.html",
    "./src/cljs/**/*.{cljs,cljc}"
  ],
  theme: {
    extend: {
      colors: {
        slate: {
          850: '#131e32', // Custom active state color for buttons
        }
      }
    },
  },
  plugins: [],
}
