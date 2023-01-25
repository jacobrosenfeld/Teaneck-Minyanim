var section = document.getElementsByName("minyanim");
var listExample = document.getElementsByName("list-example");

window.addEventListener("scroll", function() {
  var rect = section.getBoundingClientRect();
  var isInView = rect.top <= (window.innerHeight || document.documentElement.clientHeight) && rect.bottom >= 0;

  if (isInView) {
    listExample.classList.add("fixed-top");
  } else {
    listExample.classList.remove("fixed-top");
  }
});