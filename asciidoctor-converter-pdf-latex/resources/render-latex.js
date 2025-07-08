const mathjax = require('mathjax');

const input = process.argv[2];
if (!input) {
  console.error('Error: No LaTeX input provided.\nUsage: node render.js "\\frac{1}{x}"');
  process.exit(1);
}

mathjax.init({
  loader: { load: ['input/tex', 'output/svg'] }
}).then((MathJax) => {
  const svg = MathJax.tex2svg(input, { display: true });
  const output = MathJax.startup.adaptor.outerHTML(svg);
  console.log(output);
});