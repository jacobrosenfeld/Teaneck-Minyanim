(function() {
    var $sidebar = $("#list-example.fixed-top"), 
        $window = $(window),
        rightOffset = $sidebar.offset(),
        rightDelta = $("#footer").offset().top - $("#navbar").offset().top - $("#navbar").outerHeight() - $("#list-example.fixed-top").outerHeight(),
        topPadding = 15;

    $window.scroll(function() {
            $sidebar.stop().animate({
                marginTop: Math.max(Math.min($window.scrollTop() - rightOffset.top + topPadding, rightDelta), 0)
            });
    });
    
});