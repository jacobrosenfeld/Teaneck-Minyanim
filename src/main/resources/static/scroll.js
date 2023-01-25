document.addEventListener("DOMContentLoaded", function() {
    var section = document.getElementById("minyanim");
    var listExample = document.getElementById("list-example");
  
    window.addEventListener("scroll", function() {
      var rect = section.getBoundingClientRect();
      var isInView = rect.top <= (window.innerHeight || document.documentElement.clientHeight) && rect.bottom >= 0;
  
      if (isInView) {
        listExample.classList.add("fixed-top");
      } else {
        listExample.classList.remove("fixed-top");
      }
    });
  });  