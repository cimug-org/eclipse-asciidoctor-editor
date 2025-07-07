const mathjax = require('mathjax');

const input = process.argv[2] || 'E = mc^2';

mathjax.init({
  loader: { load: ['input/tex', 'output/svg'] }
}).then((MathJax) => {
  const svg = MathJax.tex2svg(input, { display: true });
  const output = MathJax.startup.adaptor.outerHTML(svg);
  console.log(output);
});