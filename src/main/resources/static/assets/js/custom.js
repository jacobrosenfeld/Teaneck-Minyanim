// Custom Scripts for Array Template //

jQuery(function($) {
    "use strict";

        // get the value of the bottom of the #main element by adding the offset of that element plus its height, set it as a variable
        var mainbottom = $('#main').offset().top;

        // on scroll,
        $(window).on('scroll',function(){

        // we round here to reduce a little workload
        stop = Math.round($(window).scrollTop());
        if (stop > mainbottom) {
            $('.navbar').addClass('past-main');
            $('.navbar').addClass('effect-main')
        } else {
            $('.navbar').removeClass('past-main');
       }

      });


  // Collapse navbar on click

   $(document).on('click.nav','.navbar-collapse.in',function(e) {
    if( $(e.target).is('a') ) {
    $(this).removeClass('in').addClass('collapse');
   }
  });


    /*-----------------------------------
    ----------- Scroll To Top -----------
    ------------------------------------*/

    $(window).on('scroll', function () {
      if ($(this).scrollTop() > 1000) {
          $('#back-top').fadeIn();
      } else {
          $('#back-top').fadeOut();
      }
    });
    // scroll body to 0px on click
    $('#back-top').on('click', function () {
      $('#back-top').tooltip('hide');
      $('body,html').animate({
          scrollTop: 0
      }, 1500);
      return false;
    });


    /*-------- Owl Carousel ---------- */

      $(".review-cards").owlCarousel({
        slideSpeed: 200,
        items: 1,
        singleItem: true,
        autoplay:true,
        autoplayTimeout:2000,
        autoplayHoverPause:true,
        pagination: false,
      });


  /* ------ jQuery for Easing min -- */
  (function($) {
    "use strict"; // Start of use strict

    // Smooth scrolling using jQuery easing
    $('a.js-scroll-trigger[href*="#"]:not([href="#"])').on('click', function () {
      if (location.pathname.replace(/^\//, '') == this.pathname.replace(/^\//, '') && location.hostname == this.hostname) {
        var target = $(this.hash);
        target = target.length ? target : $('[name=' + this.hash.slice(1) + ']');
        if (target.length) {
          $('html, body').animate({
            scrollTop: (target.offset().top - 54)
          }, 1000, "easeInOutExpo");
          return false;
        }
      }
    });

    // Closes responsive menu when a scroll trigger link is clicked
    $('.js-scroll-trigger').on('click', function() {
      $('.navbar-collapse').collapse('hide');
    });

    // Activate scrollspy to add active class to navbar items on scroll
    $('body').scrollspy({
      target: '#mainNav',
      offset: 54
    });

  })(jQuery); // End of use strict


/* --------- Wow Init ------ */

  new WOW().init();


  /* ----- Counter Up ----- */

  const counterUp = window.counterUp.default;

  const callback = entries => {
    entries.forEach(entry => {
      const el = entry.target;
      if (entry.isIntersecting && !el.classList.contains('is-visible')) {
        counterUp(el, {
          duration: 2000,
          delay: 16,
        });
        el.classList.add('is-visible');
      }
    });
  };
  
  const IO = new IntersectionObserver(callback, { threshold: 1 });
  
  const counters = document.querySelectorAll('.counter');
  counters.forEach(counter => IO.observe(counter));
  

/*----- Preloader ----- */

    $(window).on('load', function() {
  		setTimeout(function() {
        $('#loading').fadeOut('slow', function() {
        });
      }, 3000);
    });


/*----- Subscription Form ----- */

// $(document).ready(function() {
//      // jQuery Validation
//      $("#chimp-form").validate({
//          // if valid, post data via AJAX
//          submitHandler: function(form) {
//              $.post("assets/php/subscribe.php", { email: $("#chimp-email").val() }, function(data) {
//                  $('#response').html(data);
//              });
//          },
//          // all fields are required
//          rules: {
//              email: {
//                  required: true,
//                  email: true
//              }
//          }
//      });
//  });

});

// Shabbos 
function replaceSaturdayWithShabbos() {
  // Get the root node of the document
  const root = document.documentElement;

  // Create a tree walker to get all text nodes
  const treeWalker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);

  // Loop through all text nodes and replace the text
  let node;
  while (node = treeWalker.nextNode()) {
    const text = node.textContent;

    // Replace 'Saturday' with 'Shabbos' in the text content
    const replacedText = text.replace(/\bSaturday\b/g, 'Shabbos');

    // If the text content was modified, update the node
    if (text !== replacedText) {
      node.textContent = replacedText;
    }
  }
}

replaceSaturdayWithShabbos();

// tootlip boostrap popper
const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]')
const tooltipList = [...tooltipTriggerList].map(tooltipTriggerEl => new bootstrap.Tooltip(tooltipTriggerEl))